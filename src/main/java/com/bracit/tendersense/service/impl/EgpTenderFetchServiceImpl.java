package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.EgpProperties;
import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.exception.FetchException;
import com.bracit.tendersense.service.TenderFetchService;
import com.bracit.tendersense.util.EgpHtmlParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live e-GP Bangladesh ingestion.
 *
 * <p>The portal splits into two tiers with very different costs, and this class
 * treats them differently on purpose:
 *
 * <ul>
 *   <li><b>Listing</b> requires a real browser. {@code AllTenders.jsp} serves an empty
 *       table and fills it via a jQuery POST to {@code /TenderDetailsServlet}; that
 *       servlet returns "No records found" for plain HTTP clients regardless of
 *       parameters, so Selenium is not a convenience here, it is the only way in.</li>
 *   <li><b>Detail</b> is a plain form POST to {@code ViewTender.jsp} and needs no
 *       browser at all -- so it runs on a normal HTTP client, rate limited.</li>
 * </ul>
 *
 * <p>Discovery therefore stays cheap (1-3 listing pages) and the expensive per-tender
 * fetch is demand-driven: it only runs for ids discovery has not seen before.
 */
@Service
@ConditionalOnProperty(name = "tendersense.source.mode", havingValue = "live", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class EgpTenderFetchServiceImpl implements TenderFetchService {

    private static final Pattern TOTAL_PAGES = Pattern.compile("Page\\s*\\d+\\s*of\\s*(\\d+)");
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/144.0.0.0 Safari/537.36";

    /** Extracts the row data in one round trip; per-element walks go stale mid-page. */
    private static final String EXTRACT_ROWS_JS = """
            const out = [];
            document.querySelectorAll('#resultTable tr').forEach(tr => {
              const idIn = tr.querySelector('input[name="id"]');
              if (idIn) out.push(idIn.value);
            });
            return out;
            """;

    private final EgpProperties props;
    private final EgpHtmlParser parser;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public SourcePortal portal() {
        return SourcePortal.EGP_BANGLADESH;
    }

    @Override
    public FetchResult discover(Set<String> knownExternalIds) {
        return crawl(knownExternalIds, props.getDiscoveryMaxPages(), true);
    }

    @Override
    public FetchResult fetchAll() {
        return crawl(Set.of(), Integer.MAX_VALUE, false);
    }

    private FetchResult crawl(Set<String> known, int maxPages, boolean applyStopRule) {
        List<String> newIds = new ArrayList<>();
        int pagesScanned = 0;
        int consecutiveKnown = 0;
        boolean stoppedEarly = false;

        WebDriver driver = newDriver();
        try {
            String listingUrl = props.getBaseUrl() + props.getListingPath();
            driver.get(listingUrl);
            WebDriverWait wait = new WebDriverWait(
                    driver, Duration.ofSeconds(props.getPageLoadTimeoutSeconds()));
            wait.until(d -> !rowIds(d).isEmpty());

            int totalPages = totalPages(driver);
            int limit = Math.min(maxPages, totalPages);
            log.info("e-GP listing: {} pages available, scanning up to {}", totalPages, limit);

            for (int page = 1; page <= limit; page++) {
                if (page > 1 && !gotoPage(driver, page, wait)) {
                    log.warn("e-GP pagination stalled at page {}; stopping listing", page - 1);
                    break;
                }
                pagesScanned++;

                for (String id : rowIds(driver)) {
                    if (known.contains(id)) {
                        consecutiveKnown++;
                    } else {
                        consecutiveKnown = 0;
                        newIds.add(id);
                    }
                }

                if (applyStopRule && consecutiveKnown >= props.getDiscoveryStopAfterKnown()) {
                    log.info("e-GP discovery caught up after {} page(s): {} consecutive known ids",
                            pagesScanned, consecutiveKnown);
                    stoppedEarly = true;
                    break;
                }
            }
        } finally {
            driver.quit();
        }

        List<Tender> tenders = fetchDetails(newIds);
        return new FetchResult(tenders, pagesScanned, tenders.size(), stoppedEarly);
    }

    /**
     * Detail pages, one per second. This is a government portal: the delay is
     * deliberate and should not be tuned down to make a demo faster.
     */
    private List<Tender> fetchDetails(List<String> ids) {
        List<Tender> out = new ArrayList<>(ids.size());
        int failed = 0;

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            try {
                String html = fetchDetailHtml(id);
                Path snapshot = writeSnapshot(id, html);
                out.add(parser.parse(html, id, snapshot == null ? null : snapshot.toString()));
            } catch (Exception e) {
                failed++;
                log.warn("e-GP detail {} failed: {}", id, e.getMessage());
            }
            if (i + 1 < ids.size()) {
                sleep(props.getDetailDelayMs());
            }
        }

        if (failed > 0) {
            log.warn("e-GP detail fetch: {} of {} failed", failed, ids.size());
        }
        return out;
    }

    private String fetchDetailHtml(String tenderId) throws Exception {
        String body = "id=" + URLEncode(tenderId) + "&h=t";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.getBaseUrl() + props.getDetailPath()))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", props.getBaseUrl() + props.getListingPath())
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new FetchException("HTTP " + response.statusCode() + " for tender " + tenderId);
        }
        String html = response.body();
        if (html == null || html.length() < 5000) {
            throw new FetchException("suspiciously small detail page for tender " + tenderId);
        }
        return html;
    }

    /** Raw HTML is kept for provenance and so the cached profile can replay it. */
    private Path writeSnapshot(String tenderId, String html) {
        try {
            Path dir = Path.of(props.getSnapshotDir(), "detail");
            Files.createDirectories(dir);
            Path file = dir.resolve(tenderId + ".html");
            Files.writeString(file, html, StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            log.warn("could not write snapshot for {}: {}", tenderId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> rowIds(WebDriver driver) {
        Object result = ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript(EXTRACT_ROWS_JS);
        return result instanceof List<?> list ? (List<String>) list : List.of();
    }

    private static boolean gotoPage(WebDriver driver, int page, WebDriverWait wait) {
        List<String> before = rowIds(driver);
        String previousFirst = before.isEmpty() ? null : before.get(0);
        try {
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "document.getElementById('pageNo').value = arguments[0]; loadTable();",
                    String.valueOf(page));
            wait.until(d -> {
                List<String> now = rowIds(d);
                return !now.isEmpty() && !Objects.equals(now.get(0), previousFirst);
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int totalPages(WebDriver driver) {
        try {
            WebElement body = driver.findElement(By.tagName("body"));
            Matcher m = TOTAL_PAGES.matcher(body.getText());
            return m.find() ? Integer.parseInt(m.group(1)) : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private WebDriver newDriver() {
        ChromeOptions options = new ChromeOptions();
        if (props.isHeadless()) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage",
                "--window-size=1400,2000", "--user-agent=" + USER_AGENT);
        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(
                Duration.ofSeconds(props.getPageLoadTimeoutSeconds()));
        return driver;
    }

    private static String URLEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException("interrupted during rate-limited fetch", e);
        }
    }
}
