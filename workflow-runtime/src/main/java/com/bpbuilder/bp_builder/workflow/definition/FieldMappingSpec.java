package com.bpbuilder.bp_builder.workflow.definition;

import com.bpbuilder.bp_builder.canonical.MappingTransform;

import java.util.Map;

/**
 * One approved mapping row between an operation's payload and the canonical
 * invoice. canonicalPath is null for CONSTANT and ARTIFACT input mappings.
 */
public record FieldMappingSpec(
        String canonicalPath,
        String operationPath,
        MappingTransform transform,
        Map<String, Object> config,
        boolean required,
        int priority
) {
    public FieldMappingSpec {
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
