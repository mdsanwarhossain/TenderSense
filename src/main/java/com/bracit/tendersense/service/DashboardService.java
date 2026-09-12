package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.DashboardResponse;
import com.bracit.tendersense.entity.Organisation;

/** A company's landing page: headline numbers, best matches, deadlines, recent activity. */
public interface DashboardService {

    DashboardResponse dashboard(Organisation organisation);
}
