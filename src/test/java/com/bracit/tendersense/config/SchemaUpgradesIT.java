package com.bracit.tendersense.config;

import com.bracit.tendersense.entity.enums.SourcePortal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The database accepts every portal the code knows about.
 *
 * <p>Exists because adding BRAC passed every test and still failed in production: the
 * check constraint was rebuilt on {@code tender} but not on {@code tender_staging}, so
 * each run fetched the whole list and 64 documents, then died on the insert. Hibernate's
 * {@code ddl-auto=update} never rewrites a constraint it generated, so every table
 * holding a portal has to be repaired by {@link SchemaUpgrades} -- and checked here.
 */
@SpringBootTest(properties = "tendersense.source.mode=cached")
class SchemaUpgradesIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("every table with a source_portal accepts every portal in the code")
    void portalConstraintsCoverEveryPortal() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.columns "
                        + "where column_name = 'source_portal' and table_schema = 'public' order by table_name",
                String.class);
        assertTrue(tables.contains("tender") && tables.contains("tender_staging"),
                "expected the tender and staging tables to hold a portal, found " + tables);

        for (String table : tables) {
            String constraint = jdbc.query(
                    "select pg_get_constraintdef(c.oid) from pg_constraint c "
                            + "where c.contype = 'c' and c.conrelid = ?::regclass "
                            + "and pg_get_constraintdef(c.oid) ilike '%EGP_BANGLADESH%'",
                    rs -> rs.next() ? rs.getString(1) : null, table);
            if (constraint == null) {
                continue;   // no check on this table: nothing can reject a portal
            }
            for (SourcePortal portal : SourcePortal.values()) {
                assertTrue(constraint.contains("'" + portal.name() + "'"),
                        table + " rejects " + portal + " -- a fetch would run to the end and then fail "
                                + "on the insert. SchemaUpgrades must rebuild this constraint: " + constraint);
            }
        }
    }
}
