package com.bpbuilder.bp_builder.workflow.mapping;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.canonical.MappingTransform;
import com.bpbuilder.bp_builder.workflow.definition.FieldMappingSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Moves data between operation payloads and the canonical invoice using only
 * the reviewed mappings: operation output into the canonical invoice, and the
 * canonical invoice (plus workflow artifacts) into the next operation's request.
 */
public final class CanonicalMapper {

    public record RequestBuild(Object body, Map<String, List<String>> queryParameters, List<String> missing) {
        public RequestBuild {
            Map<String, List<String>> copy = new LinkedHashMap<>();
            queryParameters.forEach((key, values) -> copy.put(key, List.copyOf(values)));
            queryParameters = Map.copyOf(copy);
            missing = List.copyOf(missing);
        }
    }

    /**
     * Applies OUTPUT_TO_CANONICAL mappings. Several mappings for one canonical
     * field are tried in priority order and the first non-empty value wins.
     */
    public void applyOutput(Object response, List<FieldMappingSpec> mappings, CanonicalInvoice invoice,
                            CanonicalInvoice.FieldSource source) {
        Map<String, List<FieldMappingSpec>> byField = new LinkedHashMap<>();
        for (FieldMappingSpec mapping : byPriority(mappings)) {
            if (mapping.canonicalPath() == null) continue;
            byField.computeIfAbsent(mapping.canonicalPath(), ignored -> new ArrayList<>()).add(mapping);
        }
        for (Map.Entry<String, List<FieldMappingSpec>> entry : byField.entrySet()) {
            for (FieldMappingSpec candidate : entry.getValue()) {
                Object raw = JsonPath.read(response, candidate.operationPath());
                if (CanonicalInvoice.isEmpty(raw)) continue;
                invoice.set(entry.getKey(),
                        ValueTransforms.apply(candidate.transform(), raw, candidate.config(), entry.getKey()), source);
                break;
            }
        }
    }

    /**
     * Builds an operation's request body. A required field that the canonical
     * invoice cannot supply is reported in missing rather than sent empty.
     */
    public RequestBuild buildRequest(OperationSpec operation, CanonicalInvoice invoice,
                                     Map<ArtifactType, Object> artifacts) {
        return buildRequest(operation, invoice, artifacts, ignored -> null);
    }

    public RequestBuild buildRequest(OperationSpec operation, CanonicalInvoice invoice,
                                     Map<ArtifactType, Object> artifacts,
                                     Function<String, String> environment) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, List<String>> queryParameters = new LinkedHashMap<>();
        Object rootBody = null;
        List<String> missing = new ArrayList<>();

        for (FieldMappingSpec mapping : byPriority(operation.inputMappings())) {
            Object value;
            if (mapping.transform() == MappingTransform.CONSTANT) {
                value = mapping.config().get("value");
            } else if (mapping.transform() == MappingTransform.ENVIRONMENT) {
                String name = String.valueOf(mapping.config().get("name"));
                value = environment.apply(name);
                if (value == null || String.valueOf(value).isBlank()) {
                    if (mapping.required()) missing.add("environment variable " + name);
                    continue;
                }
            } else if (mapping.transform() == MappingTransform.ARTIFACT) {
                ArtifactType type = ArtifactType.from(String.valueOf(mapping.config().get("artifact")));
                Object artifact = artifacts.get(type);
                if (artifact == null) {
                    if (mapping.required()) missing.add("artifact " + type);
                    continue;
                }
                value = ValueTransforms.apply(MappingTransform.ARTIFACT, artifact, mapping.config(), mapping.operationPath());
            } else {
                if (mapping.canonicalPath() == null) continue;
                Object current = invoice.get(mapping.canonicalPath());
                if (CanonicalInvoice.isEmpty(current)) {
                    if (mapping.required()) missing.add(mapping.canonicalPath());
                    continue;
                }
                if (mapping.required() && !invoice.isComplete(mapping.canonicalPath())) {
                    missing.add(mapping.canonicalPath() + " (missing on some lines)");
                    continue;
                }
                value = ValueTransforms.apply(mapping.transform(), current, mapping.config(), mapping.canonicalPath());
            }
            String queryName = queryName(mapping.operationPath());
            if (queryName != null) {
                List<?> values = value instanceof List<?> list ? list : List.of(value);
                queryParameters.put(queryName, values.stream().map(String::valueOf).toList());
            } else if (JsonPath.isRoot(mapping.operationPath())) rootBody = value;
            else JsonPath.write(body, mapping.operationPath(), value);
        }

        if (operation.inputArtifact() != ArtifactType.CANONICAL_INVOICE && operation.inputArtifactPath() != null) {
            Object artifact = artifacts.get(operation.inputArtifact());
            if (artifact == null) {
                missing.add("artifact " + operation.inputArtifact());
            } else if (JsonPath.isRoot(operation.inputArtifactPath())) {
                rootBody = artifact;
            } else {
                JsonPath.write(body, operation.inputArtifactPath(), artifact);
            }
        }
        return new RequestBuild(rootBody != null ? rootBody : body, queryParameters, missing);
    }

    /** Publisher mapping convention for HTTP query parameters. */
    private static String queryName(String operationPath) {
        if (operationPath == null) return null;
        if (operationPath.startsWith("query.")) return operationPath.substring("query.".length());
        if (operationPath.startsWith("$.query.")) return operationPath.substring("$.query.".length());
        return null;
    }

    private static List<FieldMappingSpec> byPriority(List<FieldMappingSpec> mappings) {
        return mappings.stream().sorted(Comparator.comparingInt(FieldMappingSpec::priority)).toList();
    }
}
