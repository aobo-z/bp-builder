package com.bpbuilder.bp_builder.dto;

import java.time.LocalDateTime;

/**
 * A deployed workflow: functionUrl accepts POST {"input": ...} in the
 * workflow's input type and runs the state machine stateMachineArn, which
 * connects the workflow's component functions. upToDate is false once the
 * workflow or the catalog operations it uses change, until it is deployed again.
 */
public record WorkflowDeploymentView(
        Integer workflowId,
        String functionName,
        String functionUrl,
        String stateMachineArn,
        String region,
        String inputArtifactType,
        LocalDateTime deployedAt,
        boolean upToDate
) {
}
