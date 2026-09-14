package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Every query here is scoped to one organisation -- a notification is never shared. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    Page<Notification> findByOrganisationIdAndReadFlagFalseOrderByCreatedAtDesc(
            Long organisationId, Pageable pageable);

    long countByOrganisationIdAndReadFlagFalse(Long organisationId);

    /** Scoped by organisation so one company can never mark another's notification read. */
    Optional<Notification> findByIdAndOrganisationId(Long id, Long organisationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update Notification n set n.readFlag = true
           where n.organisation.id = :organisationId and n.readFlag = false
           """)
    int markAllRead(@Param("organisationId") Long organisationId);
}
