package com.bpbuilder.bp_builder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** A chosen chain saved as a new workflow, with the requirement it was built from. */
public record SaveWorkflowRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 4000) String requirementText,
        @NotBlank String inputArtifactType,
        @NotBlank String goalArtifactType,
        Integer validationOperationId,
        Map<String, Object> interpretation,
        @NotEmpty List<Integer> operationIds
) {
}
