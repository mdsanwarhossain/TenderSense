package com.bracit.tendersense.service.admin.impl;

import com.bracit.tendersense.dto.admin.AdminRequests.UserUpdate;
import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.Role;
import com.bracit.tendersense.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The rules that stop an admin locking the platform out. */
class AdminUserServiceImplTest {

    private AccountRepository accounts;
    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        when(accounts.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));
        service = new AdminUserServiceImpl(accounts, mock(PasswordEncoder.class));
    }

    private Account admin(long id) {
        Account a = Account.builder().id(id).email("admin" + id + "@x.com").role(Role.ADMIN).enabled(true).build();
        when(accounts.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    private Account companyUser(long id) {
        Organisation org = mock(Organisation.class);
        when(org.getId()).thenReturn(100 + id);
        when(org.isActive()).thenReturn(true);
        Account a = Account.builder().id(id).email("user" + id + "@x.com").organisation(org)
                .role(Role.USER).enabled(true).build();
        when(accounts.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    private void admins(Account... all) {
        when(accounts.findByRole(Role.ADMIN)).thenReturn(Arrays.asList(all));
    }

    @Test
    @DisplayName("an admin cannot switch themselves off or drop their own admin role")
    void notYourself() {
        Account me = admin(1);
        admins(me, admin(2));
        var off = assertThrows(IllegalArgumentException.class,
                () -> service.update(1L, 1L, new UserUpdate(null, false)));
        assertTrue(off.getMessage().contains("your own"));
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, 1L, new UserUpdate(Role.USER, null)));
        assertTrue(me.isSwitchedOn());
        assertEquals(Role.ADMIN, me.effectiveRole());
    }

    @Test
    @DisplayName("the last active admin cannot be switched off, whoever asks")
    void lastAdminIsKept() {
        Account only = admin(1);
        admins(only);
        var e = assertThrows(IllegalArgumentException.class,
                () -> service.update(99L, 1L, new UserUpdate(null, false)));
        assertEquals("TenderSense needs at least one active admin.", e.getMessage());
    }

    @Test
    @DisplayName("a switched-off admin does not count as the one that remains")
    void switchedOffAdminDoesNotCount() {
        Account target = admin(1);
        Account dormant = admin(2);
        dormant.setEnabled(false);
        admins(target, dormant);
        assertThrows(IllegalArgumentException.class, () -> service.update(99L, 1L, new UserUpdate(null, false)));
    }

    @Test
    @DisplayName("with another active admin, one admin can switch another off")
    void otherAdminCanBeSwitchedOff() {
        admin(1);
        Account other = admin(2);
        admins(accounts.findById(1L).orElseThrow(), other);
        assertFalse(service.update(1L, 2L, new UserUpdate(null, false)).enabled());
        assertFalse(other.isSwitchedOn());
    }

    @Test
    @DisplayName("an account with no company can only be an admin")
    void platformAdminCannotBecomeUser() {
        admin(1);
        Account other = admin(2);
        admins(accounts.findById(1L).orElseThrow(), other);
        var e = assertThrows(IllegalArgumentException.class,
                () -> service.update(1L, 2L, new UserUpdate(Role.USER, null)));
        assertTrue(e.getMessage().contains("no company"));
    }

    @Test
    @DisplayName("a company account can be promoted to admin and back")
    void promoteCompanyAccount() {
        Account me = admin(1);
        Account user = companyUser(5);
        admins(me);
        assertEquals(Role.ADMIN, service.update(1L, 5L, new UserUpdate(Role.ADMIN, null)).role());
        admins(me, user);
        assertEquals(Role.USER, service.update(1L, 5L, new UserUpdate(Role.USER, null)).role());
    }

    @Test
    @DisplayName("a password reset needs at least 8 characters")
    void passwordLength() {
        companyUser(5);
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(5L, "short"));
        assertDoesNotThrow(() -> service.resetPassword(5L, "long-enough"));
        verify(accounts, times(1)).save(any(Account.class));
    }

    @Test
    @DisplayName("the admin list puts staff first and marks the caller's own row")
    void listOrderAndYou() {
        Account me = admin(1);
        Account user = companyUser(5);
        when(user.getOrganisation().getName()).thenReturn("BracIT");
        when(accounts.findAll()).thenReturn(List.of(user, me));
        var rows = service.list(1L);
        assertEquals(1L, rows.get(0).id());
        assertTrue(rows.get(0).you());
        assertFalse(rows.get(1).you());
    }
}
