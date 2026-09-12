package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.dto.TenderSummaryResponse;
import com.bracit.tendersense.entity.enums.MatchGrade;
import com.bracit.tendersense.entity.enums.SourcePortal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 10, 0);

    private static TenderSummaryResponse tender(long id, MatchGrade grade, boolean saved, int closesInDays) {
        return new TenderSummaryResponse(id, "X" + id, SourcePortal.EGP_BANGLADESH, "T" + id, null, null, null,
                NOW.plusDays(closesInDays), closesInDays, true, grade, 0.5, null, 0, null, null,
                saved, false, null, null, null);
    }

    @Test
    @DisplayName("closing soon keeps S, A and saved tenders, soonest first")
    void keepsWhatIsWorthActingOn() {
        List<TenderSummaryResponse> ranked = List.of(
                tender(1, MatchGrade.S, false, 6),
                tender(2, MatchGrade.C, false, 1),   // noise: C and not saved
                tender(3, MatchGrade.B, true, 2),    // saved, so kept whatever the grade
                tender(4, MatchGrade.A, false, 0),
                tender(5, MatchGrade.B, false, 3));  // noise

        List<Long> kept = DashboardServiceImpl.worthActingOn(ranked).stream()
                .map(TenderSummaryResponse::id).toList();

        assertEquals(List.of(4L, 3L, 1L), kept);
    }

    @Test
    @DisplayName("never more than five")
    void capped() {
        List<TenderSummaryResponse> many = IntStream.range(0, 12)
                .mapToObj(i -> tender(i, MatchGrade.S, false, i % 7)).toList();
        assertEquals(DashboardServiceImpl.SHOWN, DashboardServiceImpl.worthActingOn(many).size());
    }
}
