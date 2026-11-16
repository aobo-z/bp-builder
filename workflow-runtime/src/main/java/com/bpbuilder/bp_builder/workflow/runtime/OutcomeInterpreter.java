package com.bpbuilder.bp_builder.workflow.runtime;

import com.bpbuilder.bp_builder.workflow.definition.OutcomeRuleSpec;
import com.bpbuilder.bp_builder.workflow.mapping.JsonPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads validation and communication responses using only their reviewed outcome rules. */
public final class OutcomeInterpreter {

    public ValidationOutcome validation(OutcomeRuleSpec rule, Object response, int step) {
        if (rule == null || rule.passedPath() == null) {
            return new ValidationOutcome(ValidationOutcome.Status.UNDETERMINED, step, List.of(), List.of(),
                    "No reviewed outcome rule; the verdict cannot be read.");
        }
        Object verdict = JsonPath.read(response, rule.passedPath());
        List<ValidationOutcome.Issue> errors = issues(response, rule.errorsPath(), rule.errorLocationField());
        List<ValidationOutcome.Issue> warnings = issues(response, rule.warningsPath(), rule.errorLocationField());
        if (verdict == null) {
            return new ValidationOutcome(ValidationOutcome.Status.UNDETERMINED, step, errors, warnings,
                    "The response has no value at " + rule.passedPath() + ".");
        }
        boolean passed = matches(verdict, rule.passedValues());
        return new ValidationOutcome(passed ? ValidationOutcome.Status.PASSED : ValidationOutcome.Status.FAILED,
                step, errors, warnings, rule.passedPath() + " = " + verdict);
    }

    public CommunicationOutcome communication(OutcomeRuleSpec rule, Object response, int step) {
        if (rule == null || rule.passedPath() == null) {
            String reference = rule == null ? null : text(readOptional(response, rule.referencePath()));
            return new CommunicationOutcome(CommunicationOutcome.Status.UNCONFIRMED, step, reference, List.of(),
                    "2xx response; no reviewed outcome rule confirms acceptance.");
        }
        Object verdict = JsonPath.read(response, rule.passedPath());
        boolean accepted = verdict != null && matches(verdict, rule.passedValues());
        // The error path may be the same field as the success message (Mailgun
        // answers "Queued. Thank you." and "Forbidden" in $.message), so it is
        // only read when the service did not accept the request.
        return new CommunicationOutcome(accepted ? CommunicationOutcome.Status.ACCEPTED
                : CommunicationOutcome.Status.REJECTED, step, text(readOptional(response, rule.referencePath())),
                accepted ? List.of() : texts(response, rule.errorsPath()), rule.passedPath() + " = " + verdict);
    }

    private static Object readOptional(Object response, String path) {
        return path == null ? null : JsonPath.read(response, path);
    }

    private static boolean matches(Object verdict, List<String> passedValues) {
        String value = String.valueOf(verdict).trim().toLowerCase(Locale.ROOT);
        return passedValues.stream().anyMatch(candidate -> candidate.trim().toLowerCase(Locale.ROOT).equals(value));
    }

    private static List<ValidationOutcome.Issue> issues(Object response, String path, String locationField) {
        List<ValidationOutcome.Issue> result = new ArrayList<>();
        collectIssues(readOptional(response, path), locationField, result);
        return result;
    }

    private static void collectIssues(Object value, String locationField, List<ValidationOutcome.Issue> result) {
        if (value instanceof List<?> list) {
            list.forEach(item -> collectIssues(item, locationField, result));
        } else if (value instanceof Map<?, ?> map && map.get("message") != null) {
            result.add(new ValidationOutcome.Issue(String.valueOf(map.get("message")),
                    locationField == null ? null : text(map.get(locationField))));
        } else if (value != null) {
            result.add(new ValidationOutcome.Issue(String.valueOf(value), null));
        }
    }

    private static List<String> texts(Object response, String path) {
        Object value = readOptional(response, path);
        List<String> result = new ArrayList<>();
        collectText(value, result);
        return result;
    }

    private static void collectText(Object value, List<String> result) {
        if (value instanceof List<?> list) {
            list.forEach(item -> collectText(item, result));
        } else if (value instanceof Map<?, ?> map && map.get("message") != null) {
            result.add(String.valueOf(map.get("message")));
        } else if (value != null) {
            result.add(String.valueOf(value));
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
