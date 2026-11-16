package com.bpbuilder.bp_builder.dto;

import com.bpbuilder.bp_builder.workflow.planning.ChainAssessment;

import java.util.List;

/** Every chain from the input to the goal, READY ones first. */
public record WorkflowPlanResponse(
        String inputArtifactType,
        String goalArtifactType,
        Integer validationOperationId,
        List<Candidate> candidates
) {
    public record Candidate(List<WorkflowStepView> steps, ChainAssessment assessment) {
    }
}
