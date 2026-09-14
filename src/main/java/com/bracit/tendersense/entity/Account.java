package com.bracit.tendersense.entity;

import com.bracit.tendersense.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A sign-in.
 *
 * <p>A company's account belongs to exactly one {@link Organisation} and is shared by that
 * company's tender team -- the account <em>is</em> the company. A platform admin
 * (TenderSense staff) has no organisation at all.
 *
 * <p>Deliberately a separate table rather than two columns on {@code Organisation}:
 * that entity is mapped into {@code OrganisationDto} by several endpoints, and keeping
 * the hash off it means a future serialisation cannot leak one by omission.
 */
@Entity
@Table(name = "account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_email", columnNames = "email"),
                // Postgres allows many NULLs under a unique constraint, so any number of
                // company-less admins can exist while each company keeps a single account.
                @UniqueConstraint(name = "uk_account_organisation", columnNames = "organisation_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for a platform admin. (config/SchemaUpgrades drops the old NOT NULL.) */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organisation_id")
    private Organisation organisation;

    /** Stored lower-cased so sign-in is not accidentally case-sensitive. */
    @Column(nullable = false, length = 256)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * Nullable in the database: rows from before roles existed read as USER (see
     * {@link #effectiveRole()}), because ddl-auto=update cannot add a NOT NULL column to them.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Role role;

    /** Null means enabled, for the same reason as {@link #role}. */
    private Boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public Role effectiveRole() {
        return role == null ? Role.USER : role;
    }

    /** The account itself is switched on -- its company's own active flag is separate. */
    public boolean isSwitchedOn() {
        return enabled == null || enabled;
    }
}
