package com.bracit.tendersense.service.admin.impl;

import com.bracit.tendersense.dto.admin.AdminDashboardResponse;
import com.bracit.tendersense.dto.admin.AdminDashboardResponse.CompanyGrades;
import com.bracit.tendersense.dto.admin.AdminDashboardResponse.CompanyRef;
import com.bracit.tendersense.dto.admin.AdminDashboardResponse.UserRef;
import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Role;
import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.repository.AdminQueries;
import com.bracit.tendersense.repository.AdminQueries.OpenGrades;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.service.PipelineOverviewService;
import com.bracit.tendersense.service.TenderProcessingService;
import com.bracit.tendersense.service.admin.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int RECENT = 5;

    private final OrganisationRepository organisationRepository;
    private final AccountRepository accountRepository;
    private final AdminQueries queries;
    private final PipelineOverviewService pipelineOverview;
    private final TenderProcessingService processingService;

    @Override
    public AdminDashboardResponse dashboard() {
        List<Organisation> organisations = organisationRepository.findAll(Sort.by("id"));
        List<Account> accounts = accountRepository.findAll();
        Map<Long, OpenGrades> grades = queries.openGradesByCompany();

        var companies = new AdminDashboardResponse.Companies(
                organisations.size(),
                organisations.stream().filter(Organisation::isActive).count(),
                organisations.stream().filter(Organisation::isDemonstration).count(),
                organisations.stream()
                        .sorted(Comparator.comparing(Organisation::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(RECENT)
                        .map(o -> new CompanyRef(o.getId(), o.getName(), o.getSlug(), o.isActive(), o.getCreatedAt()))
                        .toList());

        var users = new AdminDashboardResponse.Users(
                accounts.size(),
                accounts.stream().filter(Account::isSwitchedOn).count(),
                accounts.stream().filter(a -> a.effectiveRole() == Role.ADMIN).count(),
                accounts.stream()
                        .filter(a -> a.getLastLoginAt() != null)
                        .sorted(Comparator.comparing(Account::getLastLoginAt).reversed())
                        .limit(RECENT)
                        .map(a -> new UserRef(a.getId(), a.getEmail(),
                                a.getOrganisation() == null ? null : a.getOrganisation().getName(),
                                a.effectiveRole(), a.getLastLoginAt()))
                        .toList());

        List<CompanyGrades> companyGrades = organisations.stream()
                .map(o -> {
                    OpenGrades g = grades.getOrDefault(o.getId(), OpenGrades.NONE);
                    return new CompanyGrades(o.getId(), o.getName(), o.isActive(), g.s(), g.a(), g.b(), g.c());
                })
                .toList();

        return new AdminDashboardResponse(companies, users, queries.corpusByPortal(), companyGrades,
                pipelineOverview.summary(), pipelineOverview.schedule(), processingService.status());
    }
}
