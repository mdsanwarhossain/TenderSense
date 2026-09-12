package com.bracit.tendersense.config;

import com.bracit.tendersense.entity.enums.SourcePortal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The schema changes {@code ddl-auto=update} never makes, applied at startup so every
 * developer's database catches up by itself. Each statement is idempotent.
 *
 * <p>Hibernate's update adds tables and columns but never relaxes a NOT NULL, and never
 * rewrites the check constraint it generated for an enum column -- so an enum value added
 * later (UNGM, IsDB) is rejected by the database until the constraint is rebuilt.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class SchemaUpgrades implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        apply("platform admins have no company",
                "ALTER TABLE account ALTER COLUMN organisation_id DROP NOT NULL");
        apply("accounts from before roles are USER, and switched on",
                "UPDATE account SET role = 'USER' WHERE role IS NULL",
                "UPDATE account SET enabled = TRUE WHERE enabled IS NULL");

        String portals = Arrays.stream(SourcePortal.values())
                .map(p -> "'" + p.name() + "'")
                .collect(Collectors.joining(", "));
        apply("tender.source_portal accepts every portal",
                "ALTER TABLE tender DROP CONSTRAINT IF EXISTS tender_source_portal_check",
                "ALTER TABLE tender ADD CONSTRAINT tender_source_portal_check CHECK (source_portal IN (" + portals + "))");
    }

    private void apply(String what, String... statements) {
        try {
            for (String sql : statements) {
                jdbc.execute(sql);
            }
            log.debug("schema upgrade applied: {}", what);
        } catch (DataAccessException e) {
            log.warn("schema upgrade '{}' skipped: {}", what, e.getMostSpecificCause().getMessage());
        }
    }
}
