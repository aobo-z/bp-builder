package com.bpbuilder.bp_builder.workflow.runtime;

import com.bpbuilder.bp_builder.workflow.mapping.CanonicalInvoice;

import java.util.Map;

/**
 * What a run carries from one step to the next, in a form that can be
 * serialised, so each step can run in its own Lambda function under AWS Step
 * Functions. status is null while the run continues; once a step sets it, the
 * remaining steps are not run. artifacts are keyed by artifact type name and
 * hold the full documents; lastStep is the record of the step that produced
 * this state.
 */
public record WorkflowState(
        int stepCount,
        Map<String, Object> canonicalInvoice,
        Map<String, CanonicalInvoice.FieldSource> fieldSources,
        Map<String, Object> artifacts,
        ValidationOutcome validation,
        CommunicationOutcome communication,
        WorkflowRunResult.Status status,
        Integer stoppedAtStep,
        String message,
        StepRecord lastStep
) {
}
