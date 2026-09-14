package com.bracit.tendersense.dto.admin;

import java.time.Instant;
import java.util.List;

/**
 * One company, as the admin Companies table lists it.
 *
 * @param sectors  sector labels, as the sign-up form shows them
 * @param sOpen    open tenders graded S for this company
 * @param aOpen    open tenders graded A
 */
public record AdminCompanyResponse(Long id,
                                   String name,
                                   String slug,
                                   String description,
                                   List<String> sectors,
                                   boolean active,
                                   boolean demonstration,
                                   Instant createdAt,
                                   Long accountId,
                                   String accountEmail,
                                   Instant lastLoginAt,
                                   long sOpen,
                                   long aOpen) {
}
