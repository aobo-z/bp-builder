package com.bpbuilder.bp_builder.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/** Renames a workflow and/or replaces its steps; a null field is left unchanged. */
public record UpdateWorkflowRequest(
        @Size(min = 1, max = 200) String name,
        @Size(min = 1) List<Integer> operationIds
) {
}
