package com.bracit.tendersense.dto.admin;

import com.bracit.tendersense.dto.CapabilityProfileDto;
import com.bracit.tendersense.dto.ProfileStaleness;

/** A company with its capability profile; {@code profile} is null if it never saved one. */
public record AdminCompanyDetailResponse(AdminCompanyResponse company,
                                         CapabilityProfileDto profile,
                                         ProfileStaleness scoring) {
}
