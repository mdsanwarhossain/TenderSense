package com.bracit.tendersense.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code tendersense.schedule.*} block. PipelineScheduler reads the crons through
 * {@code @Scheduled} placeholders; this binding is what the admin Pipeline page lists, so
 * the page shows the schedule that is actually armed rather than a hand-kept copy.
 */
@Getter @Setter
@ConfigurationProperties(prefix = "tendersense.schedule")
public class ScheduleProperties {

    /** Disabled by the demo profile so nothing fires mid-presentation. */
    private boolean enabled = true;

    private String zone = "Asia/Dhaka";

    private String egpDiscovery;
    private String egpReconcile;
    private String worldBankSync;
    private String ungmSync;
    private String isdbSync;
    private String bracSync;
    private String morningDigest;
}
