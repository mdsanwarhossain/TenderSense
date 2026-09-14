package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {

    Optional<Organisation> findBySlug(String slug);

    List<Organisation> findByActiveTrueOrderByIdAsc();

    /** The tenant a request falls back to when it names none. */
    Optional<Organisation> findFirstByActiveTrueOrderByIdAsc();
}
