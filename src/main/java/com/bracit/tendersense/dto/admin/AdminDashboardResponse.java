package com.bracit.tendersense.dto.admin;

import com.bracit.tendersense.dto.ProcessingStatusResponse;
import com.bracit.tendersense.dto.RunSummaryResponse;
import com.bracit.tendersense.dto.ScheduleResponse;
import com.bracit.tendersense.entity.enums.Role;
import com.bracit.tendersense.entity.enums.SourcePortal;

import java.time.Instant;
import java.util.List;

/** The admin dashboard in one round trip. */
public record AdminDashboardResponse(Companies companies,
                                     Users users,
                                     List<PortalCorpus> corpus,
                                     List<CompanyGrades> grades,
                                     RunSummaryResponse runs,
                                     ScheduleResponse schedule,
                                     ProcessingStatusResponse processing) {

    public record Companies(long total, long active, long demonstration, List<CompanyRef> newest) {
    }

    public record CompanyRef(Long id, String name, String slug, boolean active, Instant createdAt) {
    }

    public record Users(long total, long enabled, long admins, List<UserRef> recentSignIns) {
    }

    public record UserRef(Long id, String email, String company, Role role, Instant lastLoginAt) {
    }

    /**
     * Tenders stored from one portal. {@code aiNone} are those the local model has not
     * read (yet, or ever -- tenders stored before the model existed).
     */
    public record PortalCorpus(SourcePortal portal, long total, long open, long closed,
                               long newToday, long newThisWeek,
                               long aiRead, long aiSkipped, long aiFailed, long aiNone) {
    }

    /** How a company's open tenders grade out: the health of its profile at a glance. */
    public record CompanyGrades(Long organisationId, String name, boolean active,
                                long s, long a, long b, long c) {
    }
}
