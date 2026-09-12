package com.bracit.tendersense.repository;

import com.bracit.tendersense.entity.ScheduledJobSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ScheduledJobSettingRepository extends JpaRepository<ScheduledJobSetting, String> {

    /** Stamps a firing without touching the schedule an admin may be saving at the same moment. */
    @Modifying
    @Transactional
    @Query("update ScheduledJobSetting s set s.lastFiredAt = :at where s.jobKey = :key")
    int markFired(@Param("key") String key, @Param("at") Instant at);
}
