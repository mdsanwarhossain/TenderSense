package com.bracit.tendersense.service.impl;

import com.bracit.tendersense.entity.Tender;
import com.bracit.tendersense.repository.TenderRepository;
import com.bracit.tendersense.service.TenderEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenderEmbeddingServiceImpl implements TenderEmbeddingService {

    /** The model has a token limit; the same truncation the matcher used previously. */
    private static final int MAX_CHARS = 2000;

    private static final int BATCH = 500;

    private final EmbeddingModel embeddingModel;
    private final TenderRepository tenderRepository;

    @Override
    public String modelVersion() {
        return "onnx:all-MiniLM-L6-v2:384";
    }

    @Override
    @Transactional
    public int ensureEmbedded(List<Tender> tenders) {
        List<Tender> stale = tenders.stream().filter(this::needsEmbedding).toList();
        if (stale.isEmpty()) {
            return 0;
        }
        long started = System.currentTimeMillis();
        for (Tender t : stale) {
            embed(t);
        }
        tenderRepository.saveAll(stale);
        log.info("embedded {} tenders in {}ms", stale.size(), System.currentTimeMillis() - started);
        return stale.size();
    }

    @Override
    public int embedAll() {
        List<Tender> pending = tenderRepository.findNeedingEmbedding(modelVersion());
        if (pending.isEmpty()) {
            log.info("every tender already carries a current embedding");
            return 0;
        }
        long started = System.currentTimeMillis();
        int done = 0;
        // Committed per batch so a failure late in a long run keeps the work already done.
        for (int i = 0; i < pending.size(); i += BATCH) {
            done += ensureEmbedded(pending.subList(i, Math.min(i + BATCH, pending.size())));
            log.info("embedding progress: {}/{}", done, pending.size());
        }
        log.info("embedded {} tenders in {}ms total", done, System.currentTimeMillis() - started);
        return done;
    }

    @Override
    public Map<Long, float[]> vectorsFor(List<Tender> tenders) {
        Map<Long, float[]> out = new HashMap<>();
        for (Tender t : tenders) {
            if (t.getEmbedding() != null && t.getEmbedding().length > 0) {
                out.put(t.getId(), t.getEmbedding());
            }
        }
        return out;
    }

    private boolean needsEmbedding(Tender t) {
        return t.getEmbedding() == null
                || t.getEmbedding().length == 0
                || !modelVersion().equals(t.getEmbeddingModel());
    }

    private void embed(Tender tender) {
        String text = text(tender);
        if (text.isBlank()) {
            // An empty vector is still a decision: it stops us retrying a tender that has
            // no text on every subsequent run.
            tender.setEmbedding(new float[0]);
            tender.setEmbeddingModel(modelVersion());
            return;
        }
        tender.setEmbedding(embeddingModel.embed(text));
        tender.setEmbeddingModel(modelVersion());
    }

    /** Must stay identical to what the matcher used to embed, or scores shift silently. */
    private static String text(Tender t) {
        StringBuilder sb = new StringBuilder();
        if (t.getTitle() != null) {
            sb.append(t.getTitle());
        }
        if (t.getDescription() != null && !t.getDescription().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(". ");
            }
            sb.append(t.getDescription());
        }
        String text = sb.toString().trim();
        return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
    }
}
