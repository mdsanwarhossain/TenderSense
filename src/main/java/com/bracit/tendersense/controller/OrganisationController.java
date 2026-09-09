package com.bracit.tendersense.controller;

import com.bracit.tendersense.config.CurrentOrganisation;
import com.bracit.tendersense.dto.OrganisationDto;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.repository.OrganisationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The subscribing companies, and which one the current request is acting for. */
@RestController
@RequestMapping("/api/organisations")
@RequiredArgsConstructor
public class OrganisationController {

    private final OrganisationRepository organisationRepository;

    @GetMapping
    public List<OrganisationDto> list() {
        return organisationRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(OrganisationController::toDto)
                .toList();
    }

    /** Echoes back whichever organisation the X-Org-Id header resolved to. */
    @GetMapping("/current")
    public OrganisationDto current(@CurrentOrganisation Organisation organisation) {
        return toDto(organisation);
    }

    private static OrganisationDto toDto(Organisation o) {
        return new OrganisationDto(o.getId(), o.getName(), o.getSlug(), o.getDescription(),
                o.getSectors(), o.isDemonstration());
    }
}
