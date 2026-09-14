package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.TenderStaging;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.entity.enums.StagingStatus;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.repository.TenderStagingRepository;
import com.bracit.tendersense.service.TenderStagingService;
import com.bracit.tendersense.util.EgpHtmlParser;
import com.bracit.tendersense.util.EnrichmentPrompt;
import com.bracit.tendersense.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenderStagingServiceImpl implements TenderStagingService {

    /** Statuses meaning "already on its way in". */
    static final Set<StagingStatus> IN_FLIGHT =
            EnumSet.of(StagingStatus.PENDING, StagingStatus.PROCESSING, StagingStatus.PERSISTED);

    private final TenderStagingRepository stagingRepository;
    private final TenderRepository tenderRepository;
    private final EgpHtmlParser egpParser;
    private final LlmProperties llm;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public int stage(FetchResult result) {
        int queued = 0, unchanged = 0, already = 0;
        Instant now = Instant.now();
        for (Tender t : result.tenders()) {
            if (t.getExternalId() == null || t.getSourcePortal() == null) {
                log.warn("skipping tender with no identity: {}", t.getTitle());
                continue;
            }
            Optional<Tender> existing = tenderRepository.findBySourcePortalAndExternalId(
                    t.getSourcePortal(), t.getExternalId());
            if (existing.isPresent() && sameVersion(existing.get(), t)) {
                existing.get().setLastSeenAt(now);
                tenderRepository.save(existing.get());
                unchanged++;
                continue;
            }
            if (enqueue(t, now)) {
                queued++;
            } else {
                already++;
            }
        }
        log.info("stage: {} queued, {} already queued, {} unchanged", queued, already, unchanged);
        return queued;
    }

    @Override
    public int backfill() {
        int queued = 0, skipped = 0, reparsed = 0;
        Instant now = Instant.now();
        for (Tender stored : tenderRepository.findAll()) {
            Tender source = stored;
            if (stored.getSourcePortal() == SourcePortal.EGP_BANGLADESH) {
                Tender fresh = reparse(stored);
                if (fresh != null) {
                    source = fresh;
                    reparsed++;
                }
            }
            if (sameVersion(stored, source) && processedByCurrentPipeline(stored)) {
                skipped++;
                continue;
            }
            if (enqueue(source, now)) {
                queued++;
            }
        }
        log.info("backfill: {} queued ({} e-GP pages re-parsed), {} already processed", queued, reparsed, skipped);
        return queued;
    }

    @Override
    public Set<String> queuedExternalIds(SourcePortal portal) {
        return new HashSet<>(stagingRepository.findExternalIds(portal, IN_FLIGHT));
    }

    /**
     * Queues this version of the tender. A version already on its way in is left alone;
     * one that went through before (a backfill re-run) is queued again.
     */
    private boolean enqueue(Tender t, Instant now) {
        Optional<TenderStaging> existing = stagingRepository.findVersion(t.getSourcePortal(), t.getExternalId(),
                nz(t.getContentHash()), nz(t.getParserVersion()));
        if (existing.isPresent() && IN_FLIGHT.contains(existing.get().getStatus())) {
            return false;
        }
        TenderStaging row = existing.orElseGet(TenderStaging::new);
        row.setSourcePortal(t.getSourcePortal());
        row.setExternalId(t.getExternalId());
        row.setContentHash(t.getContentHash());
        row.setParserVersion(t.getParserVersion());
        row.setClosingAt(t.getClosingAt());
        row.setParsedJson(json.writeValueAsString(t));
        row.setRawPayload(t.getRawPayload());
        row.setStatus(StagingStatus.PENDING);
        row.setAttempts(0);
        row.setLockedAt(null);
        row.setLastError(null);
        row.setProcessedAt(null);
        row.setFetchedAt(now);
        stagingRepository.save(row);
        return true;
    }

    /** The saved e-GP page, parsed with today's parser -- so parser fixes reach old tenders. */
    private Tender reparse(Tender stored) {
        String path = stored.getRawSnapshotPath();
        if (path == null || !Files.isRegularFile(Path.of(path))) {
            return null;
        }
        try {
            return egpParser.parse(Files.readString(Path.of(path), StandardCharsets.UTF_8),
                    stored.getExternalId(), path);
        } catch (Exception e) {
            log.warn("could not re-parse {}: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * True when the stored tender already carries this pipeline's output: standard
     * fields, and -- with the model on -- a reading made from this exact content, model
     * and prompt.
     */
    private boolean processedByCurrentPipeline(Tender stored) {
        if (stored.getNoticeType() == null && stored.getLocation() == null) {
            return false;
        }
        if (!llm.isEnabled()) {
            return true;
        }
        // A failed reading is tried again on the next backfill.
        return stored.getAiStatus() != com.bracit.tendersense.entity.enums.AiStatus.FAILED
                && Objects.equals(stored.getAiInputHash(), aiInputHash(stored, llm.getModel()));
    }

    static String aiInputHash(Tender t, String model) {
        return HashUtil.sha256(String.join("|", nz(t.getContentHash()), nz(t.getParserVersion()),
                model, EnrichmentPrompt.VERSION));
    }

    private static boolean sameVersion(Tender a, Tender b) {
        return Objects.equals(a.getContentHash(), b.getContentHash())
                && Objects.equals(a.getParserVersion(), b.getParserVersion());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
