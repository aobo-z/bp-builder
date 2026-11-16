package com.bpbuilder.bp_builder.workflow.planning;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.canonical.MappingTransform;
import com.bpbuilder.bp_builder.workflow.definition.FieldMappingSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Checks, before anything is called, whether a chain's operations connect: the
 * artifacts follow on, every step can be executed, and every canonical field an
 * operation requires is produced by an earlier step's reviewed output mapping.
 * A canonical invoice input supplies every canonical field itself. Whether a
 * particular input actually contains the data is checked by the executor at
 * run time; business-profile rules are left to the validation operation.
 */
public final class CompletenessChecker {

    private final ChainPlanner planner;

    public CompletenessChecker(ChainPlanner planner) {
        this.planner = planner;
    }

    public ChainAssessment assess(List<OperationSpec> chain, ArtifactType start) {
        List<ChainIssue> issues = new ArrayList<>(planner.sequenceIssues(chain, start));
        Set<String> warnings = new LinkedHashSet<>();
        boolean inputSuppliesFields = start == ArtifactType.CANONICAL_INVOICE;

        Set<String> available = new HashSet<>();
        Set<ArtifactType> artifacts = EnumSet.of(start);
        for (int index = 0; index < chain.size(); index++) {
            OperationSpec operation = chain.get(index);
            int step = index + 1;
            checkExecutable(operation, step, issues);
            for (FieldMappingSpec mapping : operation.inputMappings()) {
                if (mapping.transform() == MappingTransform.ARTIFACT) {
                    ArtifactType needed = ArtifactType.from(String.valueOf(mapping.config().get("artifact")));
                    if (mapping.required() && !artifacts.contains(needed)) {
                        issues.add(new ChainIssue(ChainIssue.Type.INVALID_SEQUENCE, step, null,
                                operation.name() + " needs the " + needed + " artifact, which no earlier step produces."));
                    }
                } else if (mapping.canonicalPath() != null && mapping.required() && !inputSuppliesFields
                        && !available.contains(mapping.canonicalPath())) {
                    issues.add(new ChainIssue(ChainIssue.Type.FIELD_NOT_PRODUCED, step, mapping.canonicalPath(),
                            operation.name() + " requires " + mapping.canonicalPath()
                                    + ", which no earlier step produces, so it cannot be connected here."));
                }
            }
            for (FieldMappingSpec mapping : operation.outputMappings()) {
                if (mapping.canonicalPath() != null) available.add(mapping.canonicalPath());
            }
            artifacts.add(operation.outputArtifact());
            String warning = verificationWarning(operation);
            if (warning != null) warnings.add(warning);
        }
        return ChainAssessment.of(issues, new ArrayList<>(warnings));
    }

    private static void checkExecutable(OperationSpec operation, int step, List<ChainIssue> issues) {
        if (!"HTTP".equalsIgnoreCase(operation.executionType())) {
            issues.add(new ChainIssue(ChainIssue.Type.UNSUPPORTED_OPERATION, step, null,
                    operation.name() + " has execution type " + operation.executionType() + "; only HTTP is supported."));
        }
        String contentType = operation.endpoint() == null ? null : operation.endpoint().contentType();
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!normalized.contains("json") && !normalized.startsWith("multipart/form-data")) {
            issues.add(new ChainIssue(ChainIssue.Type.UNSUPPORTED_OPERATION, step, null,
                    operation.name() + " expects " + contentType
                            + "; only JSON and multipart/form-data request bodies are supported."));
        }
        if (operation.stage() == WorkflowStage.VALIDATION
                && (operation.outcomeRule() == null || operation.outcomeRule().passedPath() == null)) {
            issues.add(new ChainIssue(ChainIssue.Type.UNSUPPORTED_OPERATION, step, null,
                    operation.name() + " has no reviewed outcome rule, so a pass or fail cannot be determined."));
        }
    }

    private static String verificationWarning(OperationSpec operation) {
        String status = operation.verificationStatus();
        if (status == null || status.startsWith("VERIFIED")) return null;
        if ("RUNNABLE_XSD_INVALID".equals(status)) {
            return operation.name() + " is known to produce UBL that fails the UBL 2.1 XSD; "
                    + "the validation service is expected to reject it.";
        }
        return operation.name() + " has verification status " + status + " and has not been confirmed by a live test.";
    }

}
