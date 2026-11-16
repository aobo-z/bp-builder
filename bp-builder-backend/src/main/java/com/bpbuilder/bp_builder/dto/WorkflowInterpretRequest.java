package com.bpbuilder.bp_builder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A workflow requirement in the user's own words. */
public record WorkflowInterpretRequest(
        @NotBlank @Size(max = 4000) String text
) {
}
