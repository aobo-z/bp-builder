package com.bpbuilder.bp_builder.workflow.runtime;

import com.bpbuilder.bp_builder.workflow.mapping.CanonicalInvoice;

import java.util.List;
import java.util.Map;

/**
 * The outcome of one workflow run. The canonical invoice, the documents
 * (artifacts) and the validation/communication outcomes are kept separately.
 */
public record WorkflowRunResult(
        Status status,
        Integer stoppedAtStep,
        String message,
        List<StepRecord> steps,
        Map<String, Object> canonicalInvoice,
        Map<String, CanonicalInvoice.FieldSource> fieldSources,
        Map<String, Object> artifacts,
        ValidationOutcome validation,
        CommunicationOutcome communication
) {
    public enum Status {
        COMPLETED,
        INPUT_REQUIRED,
        /** The validator found the invoice invalid: an expected outcome, not a failure. Later steps are not run. */
        INVOICE_INVALID,
        FAILED
    }
}
