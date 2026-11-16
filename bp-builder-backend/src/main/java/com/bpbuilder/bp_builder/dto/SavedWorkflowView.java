package com.bpbuilder.bp_builder.dto;

import com.bpbuilder.bp_builder.workflow.planning.ChainAssessment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** A saved workflow with its steps, checked against the current catalog. */
public record SavedWorkflowView(
        Integer id,
        String name,
        String requirementText,
        String inputArtifactType,
        String goalArtifactType,
        Integer validationOperationId,
        Map<String, Object> interpretation,
        List<WorkflowStepView> steps,
        ChainAssessment assessment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
