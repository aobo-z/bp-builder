package com.bpbuilder.bp_builder.workflow.definition;

import java.util.List;
import java.util.Map;

/**
 * How to read a validation or communication response. passedValues are
 * compared with the value at passedPath as case-insensitive strings, so a
 * rule can match true, "VALID", "PASS" and so on. When errorsPath selects
 * objects, their "message" is the error text and errorLocationField, if set,
 * names the field that says where in the document the error applies.
 */
public record OutcomeRuleSpec(
        String passedPath,
        List<String> passedValues,
        String errorsPath,
        String warningsPath,
        String referencePath,
        String errorLocationField
) {
    public OutcomeRuleSpec {
        passedValues = passedValues == null ? List.of() : List.copyOf(passedValues);
    }

    public OutcomeRuleSpec(String passedPath, List<String> passedValues, String errorsPath, String warningsPath,
                           String referencePath) {
        this(passedPath, passedValues, errorsPath, warningsPath, referencePath, null);
    }

    /** Null when the stored rule is absent; an incomplete rule keeps its nulls. */
    public static OutcomeRuleSpec from(Map<String, Object> stored) {
        if (stored == null || stored.isEmpty()) return null;
        Object values = stored.get("passedValues");
        List<String> passed = values instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : values == null ? List.of() : List.of(String.valueOf(values));
        return new OutcomeRuleSpec(text(stored.get("passedPath")), passed, text(stored.get("errorsPath")),
                text(stored.get("warningsPath")), text(stored.get("referencePath")),
                text(stored.get("errorLocationField")));
    }

    private static String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
}
