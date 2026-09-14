package com.bracit.tendersense.repository;

import com.bracit.tendersense.dto.admin.AdminDashboardResponse.PortalCorpus;
import com.bracit.tendersense.entity.enums.SourcePortal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-company aggregates for the admin screens. Plain SQL: each is one grouped scan,
 * which JPQL can express only clumsily and derived queries not at all.
 *
 * <p>"Open" means what the tender list means by it: no closing date, or one not yet passed.
 */
@Repository
@RequiredArgsConstructor
public class AdminQueries {

    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    private final JdbcTemplate jdbc;

    /** Grade counts over open tenders, as the ranked list scores them (embedding matcher). */
    public record OpenGrades(long s, long a, long b, long c) {
        public static final OpenGrades NONE = new OpenGrades(0, 0, 0, 0);
    }

    public Map<Long, OpenGrades> openGradesByCompany() {
        Map<Long, OpenGrades> out = new HashMap<>();
        jdbc.query("""
                        select m.organisation_id,
                               count(*) filter (where m.grade = 'S'),
                               count(*) filter (where m.grade = 'A'),
                               count(*) filter (where m.grade = 'B'),
                               count(*) filter (where m.grade = 'C')
                        from match_result m
                        join tender t on t.id = m.tender_id
                        where m.matcher_type = 'EMBEDDING'
                          and (t.closing_at is null or t.closing_at >= ?)
                        group by m.organisation_id""",
                (RowCallbackHandler) rs -> out.put(rs.getLong(1),
                        new OpenGrades(rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5))),
                LocalDateTime.now());
        return out;
    }

    public List<PortalCorpus> corpusByPortal() {
        OffsetDateTime startOfToday = LocalDate.now(DHAKA).atStartOfDay(DHAKA).toOffsetDateTime();
        OffsetDateTime weekAgo = Instant.now().minus(Duration.ofDays(7)).atOffset(ZoneOffset.UTC);
        return jdbc.query("""
                        select source_portal,
                               count(*),
                               count(*) filter (where closing_at is null or closing_at >= ?),
                               count(*) filter (where first_seen_at >= ?),
                               count(*) filter (where first_seen_at >= ?),
                               count(*) filter (where ai_status = 'DONE'),
                               count(*) filter (where ai_status = 'SKIPPED'),
                               count(*) filter (where ai_status = 'FAILED'),
                               count(*) filter (where ai_status is null)
                        from tender
                        group by source_portal
                        order by count(*) desc""",
                (rs, i) -> {
                    long total = rs.getLong(2);
                    long open = rs.getLong(3);
                    return new PortalCorpus(SourcePortal.valueOf(rs.getString(1)), total, open, total - open,
                            rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8),
                            rs.getLong(9));
                },
                LocalDateTime.now(), startOfToday, weekAgo);
    }
}
