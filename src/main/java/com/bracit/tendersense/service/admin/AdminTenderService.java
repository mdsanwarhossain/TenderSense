package com.bracit.tendersense.service.admin;

import com.bracit.tendersense.dto.PageResponse;
import com.bracit.tendersense.dto.admin.AdminTenderRow;
import com.bracit.tendersense.entity.enums.AiStatus;
import com.bracit.tendersense.entity.enums.SourcePortal;

/** Every tender collected, for TenderSense staff. No company, so no scores. */
public interface AdminTenderService {

    /**
     * Newest first. Each filter is optional.
     *
     * @param search matches the title, buyer, reference or portal id
     */
    PageResponse<AdminTenderRow> list(SourcePortal source, AiStatus aiStatus, boolean includeClosed,
                                      String search, int page, int size);
}
