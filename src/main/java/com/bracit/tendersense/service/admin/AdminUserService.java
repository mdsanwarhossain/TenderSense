package com.bracit.tendersense.service.admin;

import com.bracit.tendersense.dto.admin.AdminRequests;
import com.bracit.tendersense.dto.admin.AdminUserResponse;

import java.util.List;

/**
 * Accounts, for TenderSense staff. Every rule that could lock the platform out is enforced
 * here, not in the page: an admin cannot demote or switch off themselves, and the last
 * active admin cannot be removed.
 */
public interface AdminUserService {

    List<AdminUserResponse> list(Long actorAccountId);

    AdminUserResponse update(Long actorAccountId, Long accountId, AdminRequests.UserUpdate request);

    void resetPassword(Long accountId, String rawPassword);

    /** Another platform admin: an account with no company. */
    AdminUserResponse createAdmin(Long actorAccountId, AdminRequests.NewAdmin request);
}
