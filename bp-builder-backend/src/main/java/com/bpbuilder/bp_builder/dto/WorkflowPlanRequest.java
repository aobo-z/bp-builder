package com.bpbuilder.bp_builder.dto;

import jakarta.validation.constraints.NotBlank;


/**
 * inputArtifactType/goalArtifactType are ArtifactType names, e.g. PDF_DOCUMENT
 * and VALIDATION_REPORT. validationOperationId optionally keeps only chains
 * that validate with that operation; without it every validator is offered.
 */
public record WorkflowPlanRequest(
        @NotBlank String inputArtifactType,
        @NotBlank String goalArtifactType,
        Integer validationOperationId,
        Integer maxSteps
) {
}
