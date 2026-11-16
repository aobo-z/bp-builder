package com.bpbuilder.bp_builder.dto;

import java.time.LocalDateTime;

/** One row of the run list. */
public record WorkflowRunSummary(
        Integer id,
        Integer workflowId,
        String workflowName,
        String inputArtifactType,
        String inputName,
        String status,
        String message,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
