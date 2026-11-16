package com.bpbuilder.bp_builder.dto;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;

/** One step of a candidate chain, without endpoint details. */
public record WorkflowStepView(
        int sequence,
        Integer operationId,
        String name,
        WorkflowStage stage,
        ArtifactType input,
        ArtifactType output,
        String verificationStatus
) {
    public static WorkflowStepView of(int sequence, OperationSpec operation) {
        return new WorkflowStepView(sequence, operation.operationId(), operation.name(), operation.stage(),
                operation.inputArtifact(), operation.outputArtifact(), operation.verificationStatus());
    }
}
