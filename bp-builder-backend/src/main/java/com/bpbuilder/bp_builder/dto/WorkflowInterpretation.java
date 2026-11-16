package com.bpbuilder.bp_builder.dto;

import java.util.List;

/**
 * A requirement turned into the fields of a plan request, for the user to
 * review before planning. A null artifact type means it could not be
 * determined and is asked about in questions. deliveryPhrase is the part of
 * the requirement that asked to send the invoice, when it did.
 */
public record WorkflowInterpretation(
        String inputArtifactType,
        String goalArtifactType,
        Integer validationOperationId,
        String validationOperationName,
        String deliveryPhrase,
        List<String> questions,
        String model
) {
}
