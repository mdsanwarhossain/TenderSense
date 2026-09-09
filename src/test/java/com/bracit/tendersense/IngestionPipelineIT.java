package com.bracit.tendersense;

import com.bracit.tendersense.dto.FetchResult;
import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.entity.enums.SourcePortal;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.TenderFetchService;
import com.bracit.tendersense.service.TenderIngestionService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Day-1 gate: a tender is fetched, parsed, persisted, and embedded end to end.
 *
 * <p>Runs against the cached source so it exercises the same parser and ingestion
 * path as live e-GP without depending on the portal being reachable -- which is
 * also exactly the configuration the demo falls back to.
 */
@SpringBootTest(properties = "tendersense.source.mode=cached")
class IngestionPipelineIT {

    @Autowired
    private List<TenderFetchService> fetchServices;
    @Autowired
    private TenderIngestionService ingestionService;
    @Autowired
    private TenderRepository tenderRepository;
    @Autowired
    private VectorStore vectorStore;

    private TenderFetchService egpSource() {
        return fetchServices.stream()
                .filter(s -> s.portal() == SourcePortal.EGP_BANGLADESH)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no e-GP source bean is active"));
    }

    @Test
    @DisplayName("fetch -> parse -> persist -> embed")
    void endToEnd() {
        Assumptions.assumeTrue(Files.isDirectory(Path.of("data/snapshots/egp/detail")),
                "snapshot corpus required");

        // 1. fetch + parse
        Set<String> known = ingestionService.knownExternalIds(SourcePortal.EGP_BANGLADESH);
        FetchResult fetched = egpSource().discover(known);
        Assumptions.assumeFalse(fetched.tenders().isEmpty(),
                "no unseen tenders in the snapshot - already fully ingested");

        Tender sample = fetched.tenders().get(0);
        assertEquals(SourcePortal.EGP_BANGLADESH, sample.getSourcePortal());
        assertNotNull(sample.getTitle(), "parser produced no title");
        assertNotNull(sample.getContentHash());

        // 2. persist
        List<Tender> ingested = ingestionService.ingest(fetched);
        assertFalse(ingested.isEmpty(), "nothing was persisted");
        ingested.forEach(t -> assertNotNull(t.getId(), "tender was not assigned an id"));

        Tender reloaded = tenderRepository
                .findBySourcePortalAndExternalId(SourcePortal.EGP_BANGLADESH, sample.getExternalId())
                .orElseThrow(() -> new AssertionError("tender not found after ingest"));
        assertEquals(sample.getContentHash(), reloaded.getContentHash());

        // 3. re-ingesting identical content must not duplicate or re-score
        List<Tender> secondPass = ingestionService.ingest(fetched);
        assertTrue(secondPass.isEmpty(),
                "re-ingesting unchanged tenders returned " + secondPass.size()
                        + " - corrigendum detection is broken");

        // 4. embed into pgvector and retrieve it back
        String text = reloaded.getTitle();
        vectorStore.add(List.of(new Document(
                text,
                Map.of("tenderId", String.valueOf(reloaded.getId()),
                        "externalId", reloaded.getExternalId()))));

        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(text).topK(3).build());

        assertNotNull(hits);
        assertFalse(hits.isEmpty(), "vector store returned no results for its own document");

        System.out.printf("pipeline: fetched=%d ingested=%d persisted=%d vectorHits=%d%n",
                fetched.count(), ingested.size(), tenderRepository.count(), hits.size());
    }

    @Test
    @DisplayName("cached source replays the snapshot without network access")
    void cachedSourceWorksOffline() {
        Assumptions.assumeTrue(Files.isDirectory(Path.of("data/snapshots/egp/detail")),
                "snapshot corpus required");

        FetchResult result = egpSource().discover(Set.of());
        assertFalse(result.tenders().isEmpty(), "cached replay produced nothing");

        long withTitle = result.tenders().stream()
                .filter(t -> t.getTitle() != null && !t.getTitle().isBlank()).count();
        assertEquals(result.count(), withTitle, "some replayed tenders failed to parse");
    }
}
