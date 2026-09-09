package com.bracit.tendersense.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * The sign-in for one company.
 *
 * <p>There is exactly one account per {@link Organisation}, shared by that company's tender
 * team -- the account <em>is</em> the company. No per-person users, no roles.
 *
 * <p>Deliberately a separate table rather than two columns on {@code Organisation}:
 * that entity is mapped into {@code OrganisationDto} by several endpoints, and keeping
 * the hash off it means a future serialisation cannot leak one by omission.
 */
@Entity
@Table(name = "account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_email", columnNames = "email"),
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

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    /** Stored lower-cased so sign-in is not accidentally case-sensitive. */
    @Column(nullable = false, length = 256)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
