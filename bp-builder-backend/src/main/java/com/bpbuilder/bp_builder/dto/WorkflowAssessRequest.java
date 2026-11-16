package com.bpbuilder.bp_builder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** A chain the user has chosen or edited, checked again without calling any operation. */
public record WorkflowAssessRequest(
        @NotEmpty List<Integer> operationIds,
        @NotBlank String inputArtifactType
) {
}
