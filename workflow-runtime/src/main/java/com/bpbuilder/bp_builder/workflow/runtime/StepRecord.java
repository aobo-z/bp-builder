package com.bpbuilder.bp_builder.workflow.runtime;

import java.util.List;

/**
 * What happened in one step. request/response are compacted previews (long
 * strings such as Base64 files are shortened); endpoint never contains a
 * secret URL.
 */
public record StepRecord(
        int sequence,
        Integer operationId,
        String operationName,
        String endpoint,
        Status status,
        Integer httpStatus,
        Long durationMs,
        Object request,
        Object response,
        List<String> messages
) {
    public enum Status {
        SUCCEEDED,
        /** Not called: a required value was missing. */
        INPUT_REQUIRED,
        FAILED,
        /** Skipped because an earlier step stopped the workflow. */
        NOT_RUN
    }

    public StepRecord {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
