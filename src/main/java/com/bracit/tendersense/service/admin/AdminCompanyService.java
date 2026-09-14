package com.bracit.tendersense.service.admin;

import com.bracit.tendersense.dto.admin.AdminCompanyDetailResponse;
import com.bracit.tendersense.dto.admin.AdminCompanyResponse;

import java.util.List;

/** Every company on the platform, for TenderSense staff. */
public interface AdminCompanyService {

    List<AdminCompanyResponse> list();

    AdminCompanyDetailResponse detail(Long organisationId);

    /**
     * Switches a company on or off. Off, its account cannot sign in and an open session
     * is signed out on its next request; its tenders and scores are kept.
     *
     * @param actorAccountId the admin making the change: may not switch off their own company
     */
    AdminCompanyResponse setActive(Long actorAccountId, Long organisationId, boolean active);
}
