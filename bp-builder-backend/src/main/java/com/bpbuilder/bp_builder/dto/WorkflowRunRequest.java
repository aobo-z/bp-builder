package com.bpbuilder.bp_builder.dto;

import jakarta.validation.constraints.NotNull;

/**
 * The input for one test run of a saved workflow, in the workflow's input
 * type: a PDF as raw Base64 without a data: prefix, a UBL document as XML
 * text, or a canonical invoice as a canonical JSON object. inputName is the
 * file name shown with the run.
 */
public record WorkflowRunRequest(
        @NotNull Object input,
        String inputName
) {
}
