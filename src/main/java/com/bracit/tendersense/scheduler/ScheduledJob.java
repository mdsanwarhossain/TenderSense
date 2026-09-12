package com.bracit.tendersense.scheduler;

import com.bracit.tendersense.config.ScheduleProperties;
import com.bracit.tendersense.entity.enums.SourcePortal;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The jobs that run on a clock. Their schedules live in the database (see
 * JobScheduleService) so an admin can switch them off or move them without a restart;
 * the crons in {@code tendersense.schedule.*} are only the defaults a new database starts
 * with, and what "back to default" restores.
 */
public enum ScheduledJob {

    EGP_DISCOVERY("egpDiscovery", "e-GP new tenders",
            "Reads the newest listing pages and fetches details for tenders not seen before.",
            Duration.ofMinutes(10), SourcePortal.EGP_BANGLADESH, false),
    WORLD_BANK_SYNC("worldBankSync", "World Bank",
            "Reads the World Bank procurement notices API for Bangladesh.",
            Duration.ofMinutes(10), SourcePortal.WORLD_BANK, false),
    UNGM_SYNC("ungmSync", "UNGM",
            "Reads the UN Global Marketplace listing filtered to Bangladesh.",
            Duration.ofMinutes(10), SourcePortal.UNGM, false),
    ISDB_SYNC("isdbSync", "IsDB",
            "Reads the Islamic Development Bank's Bangladesh notices.",
            Duration.ofMinutes(10), SourcePortal.ISDB, false),
    BRAC_SYNC("bracSync", "BRAC e-Tender",
            "Reads BRAC's live tender list, and the document of each new tender.",
            Duration.ofMinutes(10), SourcePortal.BRAC, false),
    /** About an hour of e-GP requests at one a second: more than a few a day would hammer the portal. */
    EGP_RECONCILE("egpReconcile", "e-GP full re-check",
            "Re-reads every stored e-GP tender, so amendments and closures are picked up. Takes about an hour.",
            Duration.ofHours(6), SourcePortal.EGP_BANGLADESH, true),
    MORNING_DIGEST("morningDigest", "Morning digest",
            "Builds each company's shortlist summary for the start of the working day.",
            Duration.ofHours(1), null, false);

    private final String key;
    private final String label;
    private final String description;
    private final Duration minGap;
    private final SourcePortal portal;
    private final boolean full;

    ScheduledJob(String key, String label, String description, Duration minGap, SourcePortal portal, boolean full) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.minGap = minGap;
        this.portal = portal;
        this.full = full;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** The shortest time allowed between two runs. */
    public Duration minGap() {
        return minGap;
    }

    /**
     * The job name its runs are recorded under -- the one PipelineServiceImpl writes
     * ("discovery:WORLD_BANK"). Null for the digest, which records no runs.
     */
    public String runJobName() {
        return portal == null ? null : (full ? "reconcile:" : "discovery:") + portal;
    }

    public String defaultCron(ScheduleProperties p) {
        return switch (this) {
            case EGP_DISCOVERY -> p.getEgpDiscovery();
            case WORLD_BANK_SYNC -> p.getWorldBankSync();
            case UNGM_SYNC -> p.getUngmSync();
            case ISDB_SYNC -> p.getIsdbSync();
            case BRAC_SYNC -> p.getBracSync();
            case EGP_RECONCILE -> p.getEgpReconcile();
            case MORNING_DIGEST -> p.getMorningDigest();
        };
    }

    public static Optional<ScheduledJob> byKey(String key) {
        return Arrays.stream(values()).filter(j -> j.key.equals(key)).findFirst();
    }
}
