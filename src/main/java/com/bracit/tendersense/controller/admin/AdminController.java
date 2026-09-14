package com.bracit.tendersense.controller.admin;

import com.bracit.tendersense.dto.admin.AdminCompanyDetailResponse;
import com.bracit.tendersense.dto.admin.AdminCompanyResponse;
import com.bracit.tendersense.dto.admin.AdminDashboardResponse;
import com.bracit.tendersense.dto.admin.AdminRequests;
import com.bracit.tendersense.dto.admin.AdminUserResponse;
import com.bracit.tendersense.security.AccountPrincipal;
import com.bracit.tendersense.service.admin.AdminCompanyService;
import com.bracit.tendersense.service.admin.AdminDashboardService;
import com.bracit.tendersense.service.admin.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The admin panel's API. TenderSense staff only: SecurityConfig requires ROLE_ADMIN for
 * everything under {@code /api/admin}.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final AdminCompanyService companyService;
    private final AdminUserService userService;

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard() {
        return dashboardService.dashboard();
    }

    // ------------------------------------------------------------------ companies

    @GetMapping("/companies")
    public List<AdminCompanyResponse> companies() {
        return companyService.list();
    }

    @GetMapping("/companies/{id}")
    public AdminCompanyDetailResponse company(@PathVariable Long id) {
        return companyService.detail(id);
    }

    @PatchMapping("/companies/{id}")
    public AdminCompanyResponse updateCompany(@AuthenticationPrincipal AccountPrincipal me,
                                              @PathVariable Long id,
                                              @RequestBody AdminRequests.CompanyUpdate request) {
        if (request.active() == null) {
            throw new IllegalArgumentException("Nothing to change.");
        }
        return companyService.setActive(me.getAccountId(), id, request.active());
    }

    // ---------------------------------------------------------------------- users

    @GetMapping("/users")
    public List<AdminUserResponse> users(@AuthenticationPrincipal AccountPrincipal me) {
        return userService.list(me.getAccountId());
    }

    @PatchMapping("/users/{id}")
    public AdminUserResponse updateUser(@AuthenticationPrincipal AccountPrincipal me,
                                        @PathVariable Long id,
                                        @RequestBody AdminRequests.UserUpdate request) {
        return userService.update(me.getAccountId(), id, request);
    }

    @PostMapping("/users/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @RequestBody AdminRequests.PasswordReset request) {
        userService.resetPassword(id, request.password());
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse createAdmin(@AuthenticationPrincipal AccountPrincipal me,
                                         @RequestBody AdminRequests.NewAdmin request) {
        return userService.createAdmin(me.getAccountId(), request);
    }
}
