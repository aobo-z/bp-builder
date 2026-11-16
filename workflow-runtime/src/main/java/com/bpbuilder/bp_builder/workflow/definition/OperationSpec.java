package com.bpbuilder.bp_builder.workflow.definition;

import com.bpbuilder.bp_builder.canonical.ArtifactType;

import java.util.List;

/**
 * Everything the planner and the executor need to know about one reviewed
 * operation. It is a plain snapshot of the catalog, so a workflow definition
 * can be serialised and executed without database access (e.g. in Lambda).
 */
public record OperationSpec(
        Integer operationId,
        String name,
        WorkflowStage stage,
        ArtifactType inputArtifact,
        ArtifactType outputArtifact,
        String verificationStatus,
        String executionType,
        HttpEndpointSpec endpoint,
        String inputArtifactPath,
        String outputArtifactPath,
        List<FieldMappingSpec> inputMappings,
        List<FieldMappingSpec> outputMappings,
        OutcomeRuleSpec outcomeRule
) {
    public OperationSpec {
        inputMappings = inputMappings == null ? List.of() : List.copyOf(inputMappings);
        outputMappings = outputMappings == null ? List.of() : List.copyOf(outputMappings);
    }
}
