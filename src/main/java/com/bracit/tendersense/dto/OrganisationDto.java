package com.bracit.tendersense.dto;

import com.bracit.tendersense.entity.enums.Sector;

import java.util.List;

public record OrganisationDto(Long id,
                              String name,
                              String slug,
                              String description,
                              List<Sector> sectors,
                              boolean demonstration) {
}
