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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Replays the Day-0 disk snapshot instead of touching the network.
 *
 * <p>This is the demo safety net. Activated by {@code tendersense.source.mode=cached}
 * (set by the {@code cached} profile), it produces the same tenders the live e-GP
 * source would, parsed by the same {@link EgpHtmlParser}, so a network failure on
 * presentation day costs a profile flag rather than a code change.
 */
@Service
@ConditionalOnProperty(name = "tendersense.source.mode", havingValue = "cached")
@RequiredArgsConstructor
@Slf4j
public class CachedTenderFetchServiceImpl implements TenderFetchService {

    private final EgpProperties props;
    private final EgpHtmlParser parser;

    @Override
    public SourcePortal portal() {
        return SourcePortal.EGP_BANGLADESH;
    }

    @Override
    public FetchResult discover(Set<String> knownExternalIds) {
        return read(knownExternalIds, props.getDiscoveryStopAfterKnown() * 5);
    }

    @Override
    public FetchResult fetchAll() {
        return read(Set.of(), Integer.MAX_VALUE);
    }

    private FetchResult read(Set<String> known, int limit) {
        Path dir = Path.of(props.getSnapshotDir(), "detail");
        if (!Files.isDirectory(dir)) {
            throw new FetchException("snapshot directory not found: " + dir.toAbsolutePath()
                    + " -- run the Day-0 snapshot before using the cached profile");
        }

        List<Tender> out = new ArrayList<>();
        int parsed = 0, skipped = 0;

        try (Stream<Path> files = Files.list(dir)) {
            List<Path> sorted = files.filter(p -> p.toString().endsWith(".html")).sorted().toList();
            for (Path f : sorted) {
                if (out.size() >= limit) {
                    break;
                }
                String id = f.getFileName().toString().replace(".html", "");
                if (known.contains(id)) {
                    skipped++;
                    continue;
                }
                try {
                    String html = Files.readString(f, StandardCharsets.UTF_8);
                    out.add(parser.parse(html, id, f.toString()));
                    parsed++;
                } catch (IOException | UncheckedIOException e) {
                    log.warn("unreadable snapshot {}: {}", f.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new FetchException("failed to list snapshot directory " + dir, e);
        }

        log.info("cached replay: {} tenders parsed, {} already known", parsed, skipped);
        return new FetchResult(out, 0, parsed, out.size() >= limit);
    }
}
