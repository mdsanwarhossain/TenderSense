package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.repository.TenderStagingRepository;
import com.bracit.tendersense.service.TenderIngestionService;
import com.bracit.tendersense.util.SectorClassifier;
import com.bracit.tendersense.util.TenderStandardiser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Persists fetched tenders, distinguishing three cases by content hash:
 * genuinely new, republished with changes (a corrigendum), and unchanged.
 *
 * <p>e-GP routinely republishes amendments under the same tender id, so treating
 * every fetch as an insert would duplicate the shortlist, and treating every
 * re-fetch as a no-op would miss deadline and requirement changes. Only new and
 * revised tenders are returned, because only those need re-scoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderIngestionServiceImpl implements TenderIngestionService {

    private final TenderRepository tenderRepository;
    private final TenderStagingRepository stagingRepository;
    private final SectorClassifier sectorClassifier;

    @Override
    @Transactional
    public List<Tender> ingest(FetchResult result) {
        List<Tender> changed = new ArrayList<>();
        int created = 0, revised = 0, unchanged = 0;

        for (Tender incoming : result.tenders()) {
            if (incoming.getExternalId() == null || incoming.getSourcePortal() == null) {
                log.warn("skipping tender with no identity: {}", incoming.getTitle());
                continue;
            }

            Optional<Tender> existing = tenderRepository.findBySourcePortalAndExternalId(
                    incoming.getSourcePortal(), incoming.getExternalId());

            // Classification is tenant-independent, so it happens once here rather than
            // per organisation at scoring time.
            prepare(incoming);

            if (existing.isEmpty()) {
                changed.add(tenderRepository.save(incoming));
                created++;
                continue;
            }

            Tender current = existing.get();
            if (sameVersion(current, incoming)) {
                // Same content: record that we still see it, but do not re-score.
                current.setLastSeenAt(Instant.now());
                tenderRepository.save(current);
                unchanged++;
                continue;
            }

            applyRevision(current, incoming);
            changed.add(tenderRepository.save(current));
            revised++;
        }

        log.info("ingest: {} created, {} revised (corrigenda), {} unchanged",
                created, revised, unchanged);
        return changed;
    }

    @Override
    public void prepare(Tender tender) {
        tender.setCpvTop(sectorClassifier.topLevel(tender.getCpvRaw()));
        tender.setSector(tender.getSourcePortal() == SourcePortal.WORLD_BANK
                ? sectorClassifier.classifyWorldBank(
                        tender.getProcurementNature(), tender.getProcurementType(), tender.getTitle())
                : sectorClassifier.classify(
                        tender.getCpvRaw(), tender.getProcurementMethod(), tender.getTitle()));
        TenderStandardiser.apply(tender);
    }

    @Override
    @Transactional
    public PersistOutcome persist(Tender incoming) {
        Optional<Tender> existing = tenderRepository.findBySourcePortalAndExternalId(
                incoming.getSourcePortal(), incoming.getExternalId());
        if (existing.isEmpty()) {
            return new PersistOutcome(tenderRepository.save(incoming), true, true);
        }

        Tender current = existing.get();
        boolean changed = !sameVersion(current, incoming);
        if (changed) {
            applyRevision(current, incoming);
        } else {
            current.setLastSeenAt(Instant.now());
            copyStandard(current, incoming);
        }
        copyAi(current, incoming);
        return new PersistOutcome(tenderRepository.save(current), false, changed);
    }

    @Override
    public Set<String> knownExternalIds(SourcePortal portal) {
        Set<String> known = new HashSet<>(tenderRepository.findAllExternalIds(portal));
        // Tenders waiting in the staging table count as known: discovery must not fetch
        // their detail pages again while the model works through the queue.
        known.addAll(stagingRepository.findExternalIds(portal, TenderStagingServiceImpl.IN_FLIGHT));
        return known;
    }

    private static boolean sameVersion(Tender a, Tender b) {
        return Objects.equals(a.getContentHash(), b.getContentHash())
                && Objects.equals(a.getParserVersion(), b.getParserVersion());
    }

    /**
     * Copies the new content onto the stored row, preserving identity and
     * {@code firstSeenAt} so the audit trail keeps the original discovery time.
     */
    private void applyRevision(Tender current, Tender incoming) {
        // A changed title or description invalidates the stored embedding; without this
        // a corrigendum would be scored on its old text.
        if (!Objects.equals(current.getTitle(), incoming.getTitle())
                || !Objects.equals(current.getDescription(), incoming.getDescription())) {
            current.setEmbedding(null);
            current.setEmbeddingModel(null);
        }
        current.setReferenceNo(incoming.getReferenceNo());
        current.setTitle(incoming.getTitle());
        current.setDescription(incoming.getDescription());
        current.setProcurementNature(incoming.getProcurementNature());
        current.setProcurementType(incoming.getProcurementType());
        current.setProcurementMethod(incoming.getProcurementMethod());
        current.setMinistry(incoming.getMinistry());
        current.setDivision(incoming.getDivision());
        current.setOrganization(incoming.getOrganization());
        current.setProcuringEntity(incoming.getProcuringEntity());
        current.setPeCode(incoming.getPeCode());
        current.setDistrict(incoming.getDistrict());
        current.setCountry(incoming.getCountry());
        current.setBudgetType(incoming.getBudgetType());
        current.setSourceOfFunds(incoming.getSourceOfFunds());
        current.setDocumentPriceBdt(incoming.getDocumentPriceBdt());
        current.setPublishedAt(incoming.getPublishedAt());
        current.setClosingAt(incoming.getClosingAt());
        current.setStatus(incoming.getStatus());
        current.setCpvRaw(incoming.getCpvRaw());
        current.setCpvTop(incoming.getCpvTop());
        current.setSector(incoming.getSector());
        current.setEligibilityText(incoming.getEligibilityText());
        current.setNoticeTypeRaw(incoming.getNoticeTypeRaw());
        current.setRawSnapshotPath(incoming.getRawSnapshotPath());
        current.setContentHash(incoming.getContentHash());
        current.setParserVersion(incoming.getParserVersion());
        current.setLastSeenAt(Instant.now());
        current.setRevisionCount(current.getRevisionCount() + 1);
        copyStandard(current, incoming);
    }

    private static void copyStandard(Tender current, Tender incoming) {
        current.setBuyer(incoming.getBuyer());
        current.setPartOf(incoming.getPartOf());
        current.setLocation(incoming.getLocation());
        current.setCategory(incoming.getCategory());
        current.setNoticeType(incoming.getNoticeType());
        current.setOpenTo(incoming.getOpenTo());
        current.setMethodLabel(incoming.getMethodLabel());
        current.setFundedBy(incoming.getFundedBy());
        current.setAmendments(incoming.getAmendments());
    }

    private static void copyAi(Tender current, Tender incoming) {
        current.setAiShortTitle(incoming.getAiShortTitle());
        current.setAiSummary(incoming.getAiSummary());
        current.setAiDeliverables(incoming.getAiDeliverables());
        current.setAiLocation(incoming.getAiLocation());
        current.setAiMinTurnoverBdt(incoming.getAiMinTurnoverBdt());
        current.setAiMinExperienceYears(incoming.getAiMinExperienceYears());
        current.setAiCertifications(incoming.getAiCertifications());
        current.setAiStatus(incoming.getAiStatus());
        current.setAiModel(incoming.getAiModel());
        current.setAiPromptVersion(incoming.getAiPromptVersion());
        current.setAiInputHash(incoming.getAiInputHash());
        current.setAiProcessedAt(incoming.getAiProcessedAt());
    }
}
