package com.bracit.tendersense.dto.admin;

import com.bracit.tendersense.entity.enums.Role;

import java.time.Instant;

/**
 * One account, as the admin Users table lists it.
 *
 * @param you true for the signed-in admin's own row, whose role and switch are locked
 */
public record AdminUserResponse(Long id,
                                String email,
                                Role role,
                                boolean enabled,
                                Long organisationId,
                                String organisationName,
                                boolean organisationActive,
                                Instant createdAt,
                                Instant lastLoginAt,
                                boolean you) {
}
