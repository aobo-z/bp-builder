package com.bpbuilder.bp_builder.workflow.definition;

import java.util.Locale;

/** The fixed business stages, in their only permitted order. */
public enum WorkflowStage {
    TRANSFORMATION,
    VALIDATION,
    COMMUNICATION;

    /** Accepts both the stored API category and the older operation-category spelling. */
    public static WorkflowStage fromCategory(String category) {
        if (category == null) return null;
        return switch (category.trim().toLowerCase(Locale.ROOT)) {
            case "transformation", "create or transform" -> TRANSFORMATION;
            case "validation" -> VALIDATION;
            case "communication" -> COMMUNICATION;
            default -> null;
        };
    }
}
