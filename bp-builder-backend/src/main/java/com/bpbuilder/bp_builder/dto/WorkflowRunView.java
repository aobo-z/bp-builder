package com.bpbuilder.bp_builder.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * One stored run. Each step also carries the stage and output artifact of its
 * operation as it ran; artifacts holds the produced documents by artifact type.
 */
public record WorkflowRunView(
        Integer id,
        Integer workflowId,
        String workflowName,
        List<Integer> operationIds,
        String inputArtifactType,
        String inputName,
        Long inputSizeBytes,
        String status,
        String message,
        List<Map<String, Object>> steps,
        Map<String, Object> canonicalInvoice,
        Map<String, Object> artifacts,
        Map<String, Object> validation,
        Map<String, Object> communication,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
