package com.bpbuilder.bp_builder.dto;

import java.time.LocalDateTime;

/** One row of the workflow list; status is checked against the current catalog. */
public record WorkflowSummary(
        Integer id,
        String name,
        String inputArtifactType,
        String goalArtifactType,
        int stepCount,
        String status,
        LocalDateTime updatedAt,
        String lastRunStatus,
        LocalDateTime lastRunAt
) {
}
