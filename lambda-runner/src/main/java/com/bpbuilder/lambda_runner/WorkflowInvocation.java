package com.bpbuilder.lambda_runner;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowDefinition;


/**
 * The complete, database-independent input accepted by the deployed Lambda.
 * BP Builder creates the workflow definition; Lambda only executes it.
 */
public record WorkflowInvocation(
        WorkflowDefinition workflow,
        ArtifactType inputArtifactType,
        /** The document for inputArtifactType; a canonical invoice JSON object when that is CANONICAL_INVOICE. */
        Object input
) {
    public WorkflowInvocation {
        if (workflow == null) throw new IllegalArgumentException("workflow is required");
        if (inputArtifactType == null || inputArtifactType == ArtifactType.UNKNOWN) {
            throw new IllegalArgumentException("inputArtifactType must be a supported artifact type");
        }
    }
}
