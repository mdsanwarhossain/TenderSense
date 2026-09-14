package com.bracit.tendersense.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A whole-profile replacement, not a patch.
 *
 * <p>The editor loads everything and saves everything, so a field absent from the payload
 * means "cleared", not "unchanged". A patch would need every list to carry stable ids for
 * reordering and deletion, which is a lot of machinery for a form one team edits.
 */
public record ProfileUpdateRequest(String orgName,
                                   String summary,
                                   BigDecimal annualTurnoverBdt,
                                   List<String> services,
                                   List<String> exclusions,
                                   List<String> sectors,
                                   List<String> geographies,
                                   List<ProjectInput> pastProjects,
                                   List<CertificationInput> certifications) {

    public record ProjectInput(String title, String client, String description,
                               String sector, BigDecimal valueBdt, Integer year) {}

    public record CertificationInput(String code, String name, String validUntil) {}
}
