package com.bracit.tendersense.service.admin.impl;

import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.dto.admin.AdminTenderRow;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.AiStatus;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.admin.AdminTenderService;
import com.bracit.tendersense.util.TenderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTenderServiceImpl implements AdminTenderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TenderRepository tenderRepository;
    private final TenderMapper mapper;

    @Override
    public PageResponse<AdminTenderRow> list(SourcePortal source, AiStatus aiStatus, boolean includeClosed,
                                             String search, int page, int size) {
        String term = search == null || search.isBlank()
                ? null
                : "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
        LocalDateTime now = LocalDateTime.now();

        return PageResponse.of(
                tenderRepository.findForAdmin(source, aiStatus, includeClosed, term, now,
                        PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE))),
                t -> toRow(t, now));
    }

    private AdminTenderRow toRow(Tender t, LocalDateTime now) {
        Sector sector = t.getSector();
        return new AdminTenderRow(
                t.getId(),
                t.getSourcePortal(),
                t.getExternalId(),
                t.getReferenceNo(),
                t.getAiShortTitle() != null ? t.getAiShortTitle() : t.getTitle(),
                t.getAiShortTitle() != null,
                t.getBuyer() != null ? t.getBuyer() : t.getOrganization(),
                sector == null ? null : sector.label(),
                t.getCategory() == null ? null : t.getCategory().name(),
                t.getNoticeType() == null ? null : t.getNoticeType().name(),
                t.getPublishedAt(),
                t.getClosingAt(),
                t.getClosingAt() != null && t.getClosingAt().isBefore(now),
                t.getAiStatus(),
                t.getAiProcessedAt(),
                t.getFirstSeenAt(),
                mapper.sourceUrl(t));
    }
}
