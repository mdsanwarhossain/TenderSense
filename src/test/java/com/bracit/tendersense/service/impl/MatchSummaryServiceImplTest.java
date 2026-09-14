package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.config.LlmProperties;
import com.bracit.tendersense.dto.MatchComparison;
import com.bracit.tendersense.dto.MatchSummaryResponse;
import com.bracit.tendersense.entity.CapabilityProfile;
import com.bracit.tendersense.entity.MatchResult;
import com.bracit.tendersense.entity.MatchSummary;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.MatcherType;
import com.bracit.tendersense.exception.LlmCallException;
import com.bracit.tendersense.repository.MatchResultRepository;
import com.bracit.tendersense.repository.MatchSummaryRepository;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.CapabilityProfileService;
import com.bracit.tendersense.service.LlmClient;
import com.bracit.tendersense.service.ModelPriority;
import com.bracit.tendersense.util.MatchSummaryPrompt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules a reader depends on: a written comparison is reused for the day, an aged one
 * is shown while it is rewritten, and nothing the model does -- including failing -- ever
 * leaves the page with nothing to show.
 *
 * <p>The executor runs inline, so "handed to a background thread" happens before the call
 * returns. The assertions are still the real ones: the first reader is told GENERATING,
 * because that is decided before the model is asked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchSummaryServiceImplTest {

    private static final long TENDER_ID = 42L;
    private static final long ORG_ID = 7L;

    @Mock private MatchSummaryRepository summaryRepository;
    @Mock private MatchResultRepository matchResultRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private CapabilityProfileService profileService;
    @Mock private LlmClient llmClient;

    private final LlmProperties llm = new LlmProperties();
    private final Organisation org = Organisation.builder().id(ORG_ID).name("Padma").slug("padma").build();
    private final Tender tender = Tender.builder().id(TENDER_ID).externalId("T-1")
            .title("Supply and installation of a solar irrigation system")
            .description("The buyer needs solar pumps installed and maintained for two years.")
            .build();
    private final CapabilityProfile profile = CapabilityProfile.builder()
            .orgName("Padma")
            .summary("Solar irrigation and rural electrification.")
            .services(List.of("Solar pump installation", "Two-year maintenance contracts"))
            .build();

    /** Stands in for the table: what save() writes is what the next read finds. */
    private MatchSummary stored;

    private MatchSummaryServiceImpl service;

    @BeforeEach
    void setUp() {
        llm.setEnabled(true);
        stored = null;

        when(tenderRepository.findById(TENDER_ID)).thenReturn(Optional.of(tender));
        when(profileService.forOrganisation(org)).thenReturn(profile);
        when(llmClient.summaryModel()).thenReturn("qwen2.5:3b");
        when(matchResultRepository.findByTenderIdAndOrganisationIdAndMatcherType(
                TENDER_ID, ORG_ID, MatcherType.EMBEDDING))
                .thenReturn(Optional.of(MatchResult.builder().summaryText("Matched on 2 services.").build()));

        when(summaryRepository.findByTenderIdAndOrganisationId(TENDER_ID, ORG_ID))
                .thenAnswer(i -> Optional.ofNullable(stored));
        when(summaryRepository.save(any(MatchSummary.class))).thenAnswer(i -> stored = i.getArgument(0));

        service = new MatchSummaryServiceImpl(summaryRepository, matchResultRepository, tenderRepository,
                profileService, llmClient, llm, new ModelPriority(), Runnable::run);
    }

    @Test
    @DisplayName("the first reader is told it is being written; the next gets the comparison")
    void writesOnceThenServesIt() throws Exception {
        when(llmClient.compare(eq(tender), eq(profile), any(), anyInt()))
                .thenReturn(new MatchComparison("The tender needs solar pumps. Padma installs them.",
                        List.of("Solar pumps — Padma's solar pump installation"), List.of()));

        MatchSummaryResponse first = service.forTender(org, TENDER_ID);
        assertEquals(MatchSummaryResponse.Status.GENERATING, first.status());
        assertNull(first.comparison());

        MatchSummaryResponse second = service.forTender(org, TENDER_ID);
        assertEquals(MatchSummaryResponse.Status.READY, second.status());
        assertEquals("The tender needs solar pumps. Padma installs them.", second.comparison());
        assertEquals(List.of("Solar pumps — Padma's solar pump installation"), second.matches());
        assertEquals("qwen2.5:3b", second.writtenBy());

        // The day's cache: the second reader did not cost a second model call.
        verify(llmClient, times(1)).compare(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("a day-old comparison is shown while a fresh one is written")
    void staleIsShownNotHidden() throws Exception {
        stored = cached(Instant.now().minus(30, ChronoUnit.HOURS), currentHash());
        when(llmClient.compare(any(), any(), any(), anyInt()))
                .thenReturn(new MatchComparison("Rewritten.", List.of(), List.of()));

        MatchSummaryResponse response = service.forTender(org, TENDER_ID);

        // Yesterday's reading of the same tender beats a spinner.
        assertEquals(MatchSummaryResponse.Status.READY, response.status());
        assertEquals("Yesterday's comparison.", response.comparison());
        verify(llmClient, times(1)).compare(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("an edited profile rewrites the comparison even inside the day")
    void hashBeatsTheClock() throws Exception {
        stored = cached(Instant.now().minus(1, ChronoUnit.HOURS), "a-hash-from-a-different-prompt");
        when(llmClient.compare(any(), any(), any(), anyInt()))
                .thenReturn(new MatchComparison("Rewritten.", List.of(), List.of()));

        service.forTender(org, TENDER_ID);

        verify(llmClient, times(1)).compare(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("a comparison written by today's model and prompt is reused untouched")
    void freshCacheIsNotRegenerated() throws Exception {
        stored = cached(Instant.now().minus(2, ChronoUnit.HOURS), currentHash());

        MatchSummaryResponse response = service.forTender(org, TENDER_ID);

        assertEquals(MatchSummaryResponse.Status.READY, response.status());
        assertEquals("Yesterday's comparison.", response.comparison());
        verify(llmClient, never()).compare(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("with the model off, the matcher's own sentence is shown")
    void fallsBackWhenTheModelIsOff() throws Exception {
        llm.setEnabled(false);

        MatchSummaryResponse response = service.forTender(org, TENDER_ID);

        assertEquals(MatchSummaryResponse.Status.UNAVAILABLE, response.status());
        assertEquals("Matched on 2 services.", response.comparison());
        verify(llmClient, never()).compare(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("a failing model falls back, and is left alone rather than asked on every poll")
    void failureDoesNotLoop() throws Exception {
        when(llmClient.compare(any(), any(), any(), anyInt()))
                .thenThrow(new LlmCallException("reply is not the JSON asked for"));

        assertEquals(MatchSummaryResponse.Status.GENERATING, service.forTender(org, TENDER_ID).status());

        MatchSummaryResponse afterFailure = service.forTender(org, TENDER_ID);
        assertEquals(MatchSummaryResponse.Status.UNAVAILABLE, afterFailure.status());
        assertEquals("Matched on 2 services.", afterFailure.comparison());

        // Two attempts on the one request, then the cool-off: the polling page does not
        // turn one broken tender into a stream of model calls.
        verify(llmClient, times(2)).compare(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("a tender this company has not been matched against asks the model nothing")
    void unscoredTenderIsNotSentToTheModel() throws Exception {
        when(matchResultRepository.findByTenderIdAndOrganisationIdAndMatcherType(
                TENDER_ID, ORG_ID, MatcherType.EMBEDDING)).thenReturn(Optional.empty());

        MatchSummaryResponse response = service.forTender(org, TENDER_ID);

        assertEquals(MatchSummaryResponse.Status.UNAVAILABLE, response.status());
        assertTrue(response.comparison().contains("not been matched"));
        verify(llmClient, never()).compare(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("blank and repeated lines from the model are dropped")
    void tidiesTheModelsLines() throws Exception {
        // Every line here passes validation: the point of this test is the blank and the
        // repeat, not the rules MatchComparisonValidatorTest covers.
        when(llmClient.compare(any(), any(), any(), anyInt()))
                .thenReturn(new MatchComparison("A comparison.",
                        List.of("Solar pumps — pump installation", "  ",
                                "Solar pumps — pump installation",
                                "Maintained for two years — two-year maintenance contracts"),
                        List.of()));

        service.forTender(org, TENDER_ID);

        assertEquals(List.of("Solar pumps — pump installation",
                        "Maintained for two years — two-year maintenance contracts"),
                service.forTender(org, TENDER_ID).matches());
    }

    private MatchSummary cached(Instant when, String hash) {
        return MatchSummary.builder()
                .tender(tender).organisation(org)
                .comparison("Yesterday's comparison.")
                .matches(new String[]{"Pumps — installation"})
                .gaps(new String[0])
                .model("qwen2.5:3b")
                .promptVersion(MatchSummaryPrompt.VERSION)
                .inputHash(hash)
                .generatedAt(when)
                .build();
    }

    /** The same hash the service computes: over the exact prompt the model would be given. */
    private String currentHash() throws Exception {
        String prompt = MatchSummaryPrompt.user(tender, profile, List.of());
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(prompt.getBytes(StandardCharsets.UTF_8)));
    }
}
