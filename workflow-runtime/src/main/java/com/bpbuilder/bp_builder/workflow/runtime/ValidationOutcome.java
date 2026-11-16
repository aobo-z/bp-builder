package com.bpbuilder.bp_builder.workflow.runtime;

import java.util.List;

/**
 * The validation service's verdict in a service-independent shape. It is kept
 * apart from the invoice: communication uses the UBL document, never this.
 */
public record ValidationOutcome(Status status, Integer step, List<Issue> errors, List<Issue> warnings,
                                String detail) {

    /**
     * One reported rule violation or warning. location is where in the
     * document it applies, when the service reports it, so repeated messages
     * about different elements stay distinguishable.
     */
    public record Issue(String message, String location) {
    }

    public enum Status {
        PASSED,
        FAILED,
        /** The response did not contain what the reviewed outcome rule expects. */
        UNDETERMINED
    }

    public ValidationOutcome {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
