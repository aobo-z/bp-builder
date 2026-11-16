package com.bpbuilder.bp_builder.workflow.runtime;

import java.util.List;

/** The result of a communication step, e.g. a delivery receipt and its message reference. */
public record CommunicationOutcome(Status status, Integer step, String reference, List<String> errors,
                                   String detail) {

    public enum Status {
        ACCEPTED,
        REJECTED,
        /** 2xx response but no reviewed outcome rule to confirm acceptance. */
        UNCONFIRMED
    }

    public CommunicationOutcome {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
