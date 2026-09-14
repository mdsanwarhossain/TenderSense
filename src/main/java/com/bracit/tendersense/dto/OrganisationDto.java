package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Sector;

import java.util.List;

public record OrganisationDto(Long id,
                              String name,
                              String slug,
                              String description,
                              List<Sector> sectors,
                              boolean demonstration) {

    public static OrganisationDto of(Organisation o) {
        return new OrganisationDto(o.getId(), o.getName(), o.getSlug(), o.getDescription(),
                o.getSectors(), o.isDemonstration());
    }
}
