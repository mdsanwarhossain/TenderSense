package com.bracit.tendersense.service.admin.impl;

import com.bracit.tendersense.dto.admin.AdminCompanyDetailResponse;
import com.bracit.tendersense.dto.admin.AdminCompanyResponse;
import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Sector;
import com.bracit.tendersense.exception.NotFoundException;
import com.bracit.tendersense.repository.AccountRepository;
import com.bracit.tendersense.repository.AdminQueries;
import com.bracit.tendersense.repository.AdminQueries.OpenGrades;
import com.bracit.tendersense.repository.CapabilityProfileRepository;
import com.bracit.tendersense.repository.OrganisationRepository;
import com.bracit.tendersense.service.ProfileStatusService;
import com.bracit.tendersense.service.admin.AdminCompanyService;
import com.bracit.tendersense.util.ProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AdminCompanyServiceImpl implements AdminCompanyService {

    private final OrganisationRepository organisationRepository;
    private final AccountRepository accountRepository;
    private final CapabilityProfileRepository profileRepository;
    private final ProfileStatusService profileStatusService;
    private final AdminQueries queries;

    @Override
    public List<AdminCompanyResponse> list() {
        Map<Long, Account> accountByCompany = accountRepository.findAll().stream()
                .filter(a -> a.getOrganisation() != null)
                .collect(Collectors.toMap(a -> a.getOrganisation().getId(), Function.identity(), (a, b) -> a));
        Map<Long, OpenGrades> grades = queries.openGradesByCompany();
        return organisationRepository.findAll(Sort.by("name")).stream()
                .map(o -> toResponse(o, accountByCompany.get(o.getId()), grades.getOrDefault(o.getId(), OpenGrades.NONE)))
                .toList();
    }

    @Override
    public AdminCompanyDetailResponse detail(Long organisationId) {
        Organisation organisation = require(organisationId);
        AdminCompanyResponse company = toResponse(organisation,
                accountRepository.findByOrganisationId(organisationId).orElse(null),
                queries.openGradesByCompany().getOrDefault(organisationId, OpenGrades.NONE));
        return new AdminCompanyDetailResponse(company,
                profileRepository.findByOrganisationId(organisationId)
                        .map(p -> ProfileMapper.toDto(p, organisation)).orElse(null),
                profileStatusService.staleness(organisation));
    }

    @Override
    @Transactional
    public AdminCompanyResponse setActive(Long actorAccountId, Long organisationId, boolean active) {
        Organisation organisation = require(organisationId);
        if (!active) {
            Long actorsCompany = accountRepository.findById(actorAccountId)
                    .map(Account::getOrganisation).map(Organisation::getId).orElse(null);
            if (Objects.equals(actorsCompany, organisationId)) {
                throw new IllegalArgumentException(
                        "You can't switch off your own company -- it would sign you out too.");
            }
        }
        if (organisation.isActive() != active) {
            organisation.setActive(active);
            organisationRepository.save(organisation);
            log.info("company {} switched {} by account {}", organisation.getSlug(), active ? "on" : "off", actorAccountId);
        }
        return toResponse(organisation,
                accountRepository.findByOrganisationId(organisationId).orElse(null),
                queries.openGradesByCompany().getOrDefault(organisationId, OpenGrades.NONE));
    }

    private Organisation require(Long id) {
        return organisationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("company " + id + " not found"));
    }

    private static AdminCompanyResponse toResponse(Organisation o, Account account, OpenGrades grades) {
        return new AdminCompanyResponse(o.getId(), o.getName(), o.getSlug(), o.getDescription(),
                o.getSectors().stream().map(Sector::label).toList(),
                o.isActive(), o.isDemonstration(), o.getCreatedAt(),
                account == null ? null : account.getId(),
                account == null ? null : account.getEmail(),
                account == null ? null : account.getLastLoginAt(),
                grades.s(), grades.a());
    }
}
