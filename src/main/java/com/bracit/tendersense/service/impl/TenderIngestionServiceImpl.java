package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.TenderIngestionService;
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

            if (existing.isEmpty()) {
                changed.add(tenderRepository.save(incoming));
                created++;
                continue;
            }

            Tender current = existing.get();
            boolean sameSource = Objects.equals(current.getContentHash(), incoming.getContentHash());
            boolean sameParser = Objects.equals(current.getParserVersion(), incoming.getParserVersion());
            if (sameSource && sameParser) {
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
    public Set<String> knownExternalIds(SourcePortal portal) {
        return new HashSet<>(tenderRepository.findAllExternalIds(portal));
    }

    /**
     * Copies the new content onto the stored row, preserving identity and
     * {@code firstSeenAt} so the audit trail keeps the original discovery time.
     */
    private void applyRevision(Tender current, Tender incoming) {
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
        current.setEligibilityText(incoming.getEligibilityText());
        current.setRawSnapshotPath(incoming.getRawSnapshotPath());
        current.setContentHash(incoming.getContentHash());
        current.setParserVersion(incoming.getParserVersion());
        current.setLastSeenAt(Instant.now());
        current.setRevisionCount(current.getRevisionCount() + 1);
    }
}
