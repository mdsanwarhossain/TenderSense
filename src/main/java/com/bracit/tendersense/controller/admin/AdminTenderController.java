package com.bracit.tendersense.controller.admin;

import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.dto.admin.AdminTenderRow;
import com.bracit.tendersense.entity.enums.AiStatus;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.service.admin.AdminTenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The whole tender corpus, for TenderSense staff: what has been collected and what the
 * local model made of it. Admin only (SecurityConfig: {@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/tenders")
@RequiredArgsConstructor
public class AdminTenderController {

    private final AdminTenderService adminTenderService;

    @GetMapping
    public PageResponse<AdminTenderRow> list(@RequestParam(required = false) SourcePortal source,
                                             @RequestParam(required = false) AiStatus aiStatus,
                                             @RequestParam(defaultValue = "true") boolean includeClosed,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "25") int size) {
        return adminTenderService.list(source, aiStatus, includeClosed, search, page, size);
    }
}
