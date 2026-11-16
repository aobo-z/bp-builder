package com.bpbuilder.bp_builder.dto;

import com.bpbuilder.bp_builder.canonical.MappingDirection;
import com.bpbuilder.bp_builder.canonical.MappingTransform;

import java.util.List;

/**
 * One approved field mapping as shown in the builder. fixedValue is set for a
 * CONSTANT; artifact and encodings for an ARTIFACT (the document an earlier
 * step produced). Environment variable names are never exposed: an
 * ENVIRONMENT mapping is only marked as a deployment setting.
 */
public record OperationMappingView(
        MappingDirection direction,
        String canonicalField,
        String operationField,
        MappingTransform transform,
        boolean required,
        Object fixedValue,
        String artifact,
        List<String> encodings
) {
}
