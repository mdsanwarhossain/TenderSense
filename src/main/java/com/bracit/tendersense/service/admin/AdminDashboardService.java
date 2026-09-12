package com.bracit.tendersense.service.admin;

import com.bracit.tendersense.dto.admin.AdminDashboardResponse;

/** The admin dashboard: companies, users, the tender corpus, collection and AI health. */
public interface AdminDashboardService {

    AdminDashboardResponse dashboard();
}
