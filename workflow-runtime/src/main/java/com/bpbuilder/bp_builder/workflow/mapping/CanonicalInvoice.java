package com.bpbuilder.bp_builder.workflow.mapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The canonical invoice that exists while a workflow runs. Values are keyed by
 * concrete canonical paths ("invoice.id", "lines[0].tax_rate"); a value given
 * for a wildcard path ("lines[*].tax_rate") applies to every line.
 *
 * Every value records where it came from. When two sources supply the same
 * field, a value supplied as the workflow input wins over an operation's output (a later
 * step may overwrite an earlier operation output).
 */
public final class CanonicalInvoice {

    /** Declared in ascending precedence. */
    public enum SourceType {
        STEP_OUTPUT,
        USER_INPUT
    }

    public record FieldSource(SourceType type, Integer stepSequence, String detail) {
        public static FieldSource step(int sequence, String operationName) {
            return new FieldSource(SourceType.STEP_OUTPUT, sequence, operationName);
        }

        public static FieldSource input() {
            return new FieldSource(SourceType.USER_INPUT, null, "supplied as the workflow input");
        }

    }

    /**
     * Loads a canonical invoice JSON object, e.g. {"invoice": {"id": "INV-1"},
     * "lines": [{"quantity": 2}]}, field by field into concrete canonical paths.
     */
    public void load(Map<?, ?> json, FieldSource source) {
        flatten(json, "", (path, value) -> set(path, value, source));
    }

    /**
     * Rebuilds an invoice from toJson() and fieldSources(), e.g. when the next
     * step runs in another Lambda function. A value without a recorded source
     * counts as workflow input.
     */
    public static CanonicalInvoice restore(Map<?, ?> json, Map<String, FieldSource> sources) {
        CanonicalInvoice invoice = new CanonicalInvoice();
        Map<String, FieldSource> known = sources == null ? Map.of() : sources;
        flatten(json, "", (path, value) -> {
            FieldSource source = known.get(path);
            if (source == null) source = known.get(path.replaceAll("\\[\\d+]", "[*]"));
            invoice.set(path, value, source == null ? FieldSource.input() : source);
        });
        return invoice;
    }

    /** The canonical field paths a canonical invoice JSON object uses, with line indexes written as [*]. */
    public static Set<String> fieldPaths(Map<?, ?> json) {
        Set<String> paths = new LinkedHashSet<>();
        flatten(json, "", (path, value) -> paths.add(path.replaceAll("\\[\\d+]", "[*]")));
        return paths;
    }

    private static void flatten(Object node, String prefix, BiConsumer<String, Object> sink) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                flatten(entry.getValue(), prefix.isEmpty() ? key : prefix + "." + key, sink);
            }
        } else if (node instanceof List<?> list && !list.isEmpty() && list.stream().allMatch(Map.class::isInstance)) {
            for (int index = 0; index < list.size(); index++) flatten(list.get(index), prefix + "[" + index + "]", sink);
        } else if (!prefix.isEmpty()) {
            sink.accept(prefix, node);
        }
    }

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, FieldSource> sources = new LinkedHashMap<>();
    private final Map<String, Object> wildcardValues = new LinkedHashMap<>();
    private final Map<String, FieldSource> wildcardSources = new LinkedHashMap<>();

    public static boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String text) return text.isBlank();
        if (value instanceof List<?> list) return list.isEmpty() || list.stream().allMatch(CanonicalInvoice::isEmpty);
        return false;
    }

    /**
     * Sets a canonical field. For a wildcard path, a list sets one value per
     * line and a single value becomes the default for every line.
     */
    public void set(String canonicalPath, Object value, FieldSource source) {
        if (isEmpty(value)) return;
        if (!JsonPath.hasWildcard(canonicalPath)) {
            put(values, sources, canonicalPath, value, source);
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                if (!isEmpty(list.get(index))) put(values, sources, concrete(canonicalPath, index), list.get(index), source);
            }
        } else {
            put(wildcardValues, wildcardSources, canonicalPath, value, source);
        }
    }

    /** A wildcard path reads as one entry per existing line (null when there are no lines). */
    public Object get(String canonicalPath) {
        if (!JsonPath.hasWildcard(canonicalPath)) return values.get(canonicalPath);
        int size = size(arrayName(canonicalPath));
        if (size == 0) return null;
        List<Object> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) result.add(valueAt(canonicalPath, index));
        return result;
    }

    /** True when the field has a value; for a line field, when every line has one. */
    public boolean isComplete(String canonicalPath) {
        Object value = get(canonicalPath);
        if (value instanceof List<?> list) return !list.isEmpty() && list.stream().noneMatch(CanonicalInvoice::isEmpty);
        return !isEmpty(value);
    }

    public int size(String arrayName) {
        String prefix = arrayName + "[";
        int max = -1;
        for (String key : values.keySet()) {
            if (!key.startsWith(prefix)) continue;
            int end = key.indexOf(']', prefix.length());
            if (end > prefix.length()) max = Math.max(max, Integer.parseInt(key.substring(prefix.length(), end)));
        }
        return max + 1;
    }

    /** The invoice as a nested JSON object, with line defaults applied to every line. */
    public Map<String, Object> toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            JsonPath.write(root, entry.getKey(), entry.getValue());
        }
        for (String wildcardPath : wildcardValues.keySet()) {
            int size = size(arrayName(wildcardPath));
            for (int index = 0; index < size; index++) {
                JsonPath.write(root, concrete(wildcardPath, index), valueAt(wildcardPath, index));
            }
        }
        return root;
    }

    /** Where each value came from, keyed by concrete or wildcard path. */
    public Map<String, FieldSource> fieldSources() {
        Map<String, FieldSource> result = new LinkedHashMap<>(sources);
        result.putAll(wildcardSources);
        return result;
    }

    public Set<String> paths() {
        Set<String> result = new LinkedHashSet<>(values.keySet());
        result.addAll(wildcardValues.keySet());
        return result;
    }

    private Object valueAt(String wildcardPath, int index) {
        String concretePath = concrete(wildcardPath, index);
        FieldSource explicit = sources.get(concretePath);
        FieldSource fallback = wildcardSources.get(wildcardPath);
        if (explicit == null) return wildcardValues.get(wildcardPath);
        if (fallback != null && fallback.type().ordinal() > explicit.type().ordinal()) {
            return wildcardValues.get(wildcardPath);
        }
        return values.get(concretePath);
    }

    private static void put(Map<String, Object> target, Map<String, FieldSource> targetSources,
                            String path, Object value, FieldSource source) {
        FieldSource existing = targetSources.get(path);
        if (existing != null && existing.type().ordinal() > source.type().ordinal()) return;
        target.put(path, value);
        targetSources.put(path, source);
    }

    private static String arrayName(String wildcardPath) {
        return wildcardPath.substring(0, wildcardPath.indexOf("[*]"));
    }

    private static String concrete(String wildcardPath, int index) {
        return wildcardPath.replaceFirst("\\[\\*]", "[" + index + "]");
    }
}
