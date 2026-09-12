package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.Account;
import com.bracit.tendersense.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<Account> findByOrganisationId(Long organisationId);

    boolean existsByRole(Role role);

    Optional<Account> findFirstByRoleOrderByIdAsc(Role role);

    List<Account> findByRole(Role role);
}
