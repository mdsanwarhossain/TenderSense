package com.bracit.tendersense.service;

import com.bracit.tendersense.dto.DigestResponse;
import com.bracit.tendersense.dto.PipelineRunResponse;
import com.bracit.tendersense.entity.Organisation;
import com.bracit.tendersense.entity.enums.SourcePortal;

import java.util.List;

/**
 * Everything the pipeline can be asked to do, in one place.
 *
 * <p>Both the manual API trigger and the scheduler call through here. Keeping the
 * run/ingest/score/recalibrate sequence in a single implementation means a change to
 * it cannot apply to one caller and silently not the other.
 *
 * <p>Every method is guarded by {@link PipelineLock}: a rejected run is recorded as
 * {@code SKIPPED} rather than dropped, so the run history shows what did not happen
 * as well as what did.
 */
public interface PipelineService {

    /** Runs every configured source. {@code full} switches discovery for reconcile. */
    List<PipelineRunResponse> runAll(boolean full);

    /** Runs one source by portal, if a bean for it is active. */
    PipelineRunResponse runSource(SourcePortal portal, boolean full);

    /**
     * Re-scores every stored tender for one company. This is what the Re-score button
     * on the profile editor calls: a company that edited its own wording must not spend
     * its click recomputing every other company's scores and holding the pipeline lock
     * for all of them.
     */
    PipelineRunResponse rescore(Organisation organisation);

    /** Re-scores every active company. The scheduler's path, and manual admin use. */
    PipelineRunResponse rescoreAll();

    /** The morning shortlist: what the 08:00 digest reports. */
    DigestResponse digest(Organisation organisation);
}
