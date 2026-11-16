package com.bpbuilder.bp_builder.workflow.planning;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.canonical.MappingTransform;
import com.bpbuilder.bp_builder.workflow.definition.FieldMappingSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds every chain of reviewed operations that turns the user's input
 * artifact into the goal artifact. The catalog is small, so the search simply
 * enumerates; nothing is precomputed or cached.
 *
 * A step may use any artifact produced earlier in the chain (not only the
 * previous step's output). Business rules enforced here: stages never go
 * backwards (Transformation, Validation, Communication); communication needs a
 * validation step earlier in the chain; an operation is used at most once; and
 * no step re-produces an artifact type that is already available.
 */
public final class ChainPlanner {

    public static final int DEFAULT_MAX_STEPS = 4;

    public List<List<OperationSpec>> findChains(List<OperationSpec> catalog, ArtifactType start,
                                                ArtifactType goal, int maxSteps) {
        List<List<OperationSpec>> results = new ArrayList<>();
        if (start == goal) return results;
        List<OperationSpec> candidates = catalog.stream().filter(ChainPlanner::plannable).toList();
        Set<ArtifactType> available = EnumSet.of(start);
        search(candidates, goal, maxSteps, new ArrayList<>(), available, results);
        results.sort(Comparator.<List<OperationSpec>>comparingInt(List::size)
                .thenComparing(chain -> chain.stream().map(OperationSpec::name).toList().toString()));
        return results;
    }

    /** Why a user-selected sequence is not a valid chain; empty when it is. */
    public List<ChainIssue> sequenceIssues(List<OperationSpec> chain, ArtifactType start) {
        List<ChainIssue> issues = new ArrayList<>();
        if (chain.isEmpty()) {
            issues.add(new ChainIssue(ChainIssue.Type.INVALID_SEQUENCE, null, null, "The chain has no steps."));
            return issues;
        }
        Set<ArtifactType> available = EnumSet.of(start);
        Set<Integer> used = new HashSet<>();
        WorkflowStage lastStage = null;
        boolean validated = false;
        for (int index = 0; index < chain.size(); index++) {
            OperationSpec operation = chain.get(index);
            String reason = !plannable(operation)
                    ? operation.name() + " has no reviewed stage or input/output artifact types."
                    : rejection(operation, available, used, lastStage, validated);
            if (reason != null) {
                issues.add(new ChainIssue(ChainIssue.Type.INVALID_SEQUENCE, index + 1, null, reason));
                continue;
            }
            used.add(operation.operationId());
            available.add(operation.outputArtifact());
            lastStage = operation.stage();
            validated |= operation.stage() == WorkflowStage.VALIDATION;
        }
        return issues;
    }

    private void search(List<OperationSpec> candidates, ArtifactType goal, int maxSteps, List<OperationSpec> chain,
                        Set<ArtifactType> available, List<List<OperationSpec>> results) {
        if (available.contains(goal)) {
            if (allStepsUseful(chain, goal)) results.add(List.copyOf(chain));
            return;
        }
        if (chain.size() >= maxSteps) return;
        WorkflowStage lastStage = chain.isEmpty() ? null : chain.get(chain.size() - 1).stage();
        boolean validated = chain.stream().anyMatch(step -> step.stage() == WorkflowStage.VALIDATION);
        Set<Integer> used = new HashSet<>();
        chain.forEach(step -> used.add(step.operationId()));
        for (OperationSpec operation : candidates) {
            if (rejection(operation, available, used, lastStage, validated) != null) continue;
            chain.add(operation);
            available.add(operation.outputArtifact());
            search(candidates, goal, maxSteps, chain, available, results);
            available.remove(operation.outputArtifact());
            chain.remove(chain.size() - 1);
        }
    }

    private static String rejection(OperationSpec operation, Set<ArtifactType> available, Set<Integer> used,
                                    WorkflowStage lastStage, boolean validated) {
        if (used.contains(operation.operationId())) {
            return operation.name() + " is already used in this chain.";
        }
        if (!available.contains(operation.inputArtifact())) {
            return operation.name() + " needs " + operation.inputArtifact() + ", which no earlier step produces.";
        }
        if (available.contains(operation.outputArtifact())) {
            return operation.name() + " would produce " + operation.outputArtifact() + " again.";
        }
        if (lastStage != null && operation.stage().ordinal() < lastStage.ordinal()) {
            return operation.name() + " (" + operation.stage() + ") cannot come after a " + lastStage + " step.";
        }
        if (operation.stage() == WorkflowStage.COMMUNICATION && !validated) {
            return operation.name() + " is a communication step; a validation step must come first.";
        }
        return null;
    }

    /** Drops chains containing a step whose output nothing later uses. */
    private static boolean allStepsUseful(List<OperationSpec> chain, ArtifactType goal) {
        for (int index = 0; index < chain.size(); index++) {
            OperationSpec step = chain.get(index);
            if (step.outputArtifact() == goal) continue;
            boolean used = false;
            for (OperationSpec later : chain.subList(index + 1, chain.size())) {
                used |= later.inputArtifact() == step.outputArtifact()
                        || consumesArtifact(later, step.outputArtifact())
                        || step.stage() == WorkflowStage.VALIDATION && later.stage() == WorkflowStage.COMMUNICATION;
            }
            if (!used) return false;
        }
        return true;
    }

    static boolean consumesArtifact(OperationSpec operation, ArtifactType artifact) {
        for (FieldMappingSpec mapping : operation.inputMappings()) {
            if (mapping.transform() == MappingTransform.ARTIFACT
                    && ArtifactType.from(String.valueOf(mapping.config().get("artifact"))) == artifact) {
                return true;
            }
        }
        return false;
    }

    private static boolean plannable(OperationSpec operation) {
        return operation.stage() != null
                && operation.inputArtifact() != null && operation.inputArtifact() != ArtifactType.UNKNOWN
                && operation.outputArtifact() != null && operation.outputArtifact() != ArtifactType.UNKNOWN;
    }
}
