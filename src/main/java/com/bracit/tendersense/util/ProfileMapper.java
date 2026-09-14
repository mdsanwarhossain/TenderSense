package com.bracit.tendersense.util;

import com.bracit.tendersense.dto.CapabilityProfileDto;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.Organisation;

import java.util.Objects;

/**
 * Capability profile to its API shape. Shared by the company's own Profile page and the
 * admin company view. Reads lazy collections, so call it inside a transaction.
 */
public final class ProfileMapper {

    private ProfileMapper() {
    }

    public static CapabilityProfileDto toDto(CapabilityProfile p, Organisation organisation) {
        return new CapabilityProfileDto(
                p.getId(), p.getOrgName(), p.getSummary(), p.getAnnualTurnoverBdt(),
                p.getServices(), p.getExclusions(),
                organisation.getSectors().stream().map(Enum::name).toList(),
                p.getGeographies(),
                p.getPastProjects().stream()
                        .map(x -> new CapabilityProfileDto.ProjectDto(
                                x.getId(), x.getTitle(), x.getClient(), x.getDescription(),
                                x.getSector(), x.getValueBdt(), x.getYear()))
                        .toList(),
                p.getCertifications().stream()
                        .map(c -> new CapabilityProfileDto.CertificationDto(
                                c.getId(), c.getCode(), c.getName(),
                                Objects.toString(c.getValidUntil(), null)))
                        .toList());
    }
}
