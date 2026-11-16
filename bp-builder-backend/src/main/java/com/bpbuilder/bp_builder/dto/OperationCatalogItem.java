package com.bpbuilder.bp_builder.dto;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;

/**
 * One active operation as shown in the read-only catalog. It describes what
 * the operation does and how far it has been reviewed; endpoint URLs,
 * environment variable names and credential references are never exposed.
 */
public record OperationCatalogItem(
        Integer id,
        String operationKey,
        String name,
        String provider,
        String summary,
        String description,
        WorkflowStage stage,
        ArtifactType inputArtifact,
        ArtifactType outputArtifact,
        String verificationStatus,
        String contractSource,
        String executionType,
        String method,
        String requestContentType,
        String authMethod,
        int inputMappings,
        int requiredInputMappings,
        int outputMappings,
        boolean outcomeRule,
        int timeoutSeconds,
        /** True when the stage and both artifact types are known, so the planner can place it in a chain. */
        boolean chainable
) {
}
