package com.bracit.tendersense.dto.admin;

import com.bracit.tendersense.entity.enums.Role;

/** Request bodies of the admin endpoints. A null field means "leave as it is". */
public final class AdminRequests {

    private AdminRequests() {
    }

    public record CompanyUpdate(Boolean active) {
    }

    public record UserUpdate(Role role, Boolean enabled) {
    }

    public record PasswordReset(String password) {
    }

    public record NewAdmin(String email, String password) {
    }

    /** A scheduled job's switch and schedule; either may be left out. */
    public record JobUpdate(Boolean enabled, String cron) {
    }
}
