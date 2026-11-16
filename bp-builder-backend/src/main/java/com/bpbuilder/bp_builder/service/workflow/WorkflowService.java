package com.bpbuilder.bp_builder.service.workflow;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.dto.WorkflowAssessRequest;
import com.bpbuilder.bp_builder.dto.WorkflowPlanRequest;
import com.bpbuilder.bp_builder.dto.WorkflowPlanResponse;
import com.bpbuilder.bp_builder.dto.WorkflowStepView;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowDefinition;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;
import com.bpbuilder.bp_builder.workflow.planning.ChainAssessment;
import com.bpbuilder.bp_builder.workflow.planning.ChainIssue;
import com.bpbuilder.bp_builder.workflow.planning.ChainPlanner;
import com.bpbuilder.bp_builder.workflow.planning.CompletenessChecker;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowExecutor;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowRunResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Plans and checks chains and runs a checked one; the workflow logic itself is in workflow.*. */
@Service
public class WorkflowService {

    private static final int MAX_ALLOWED_STEPS = 6;

    private final WorkflowCatalogService catalog;
    private final ChainPlanner planner;
    private final CompletenessChecker checker;
    private final WorkflowExecutor executor;

    public WorkflowService(WorkflowCatalogService catalog, ChainPlanner planner, CompletenessChecker checker,
                           WorkflowExecutor executor) {
        this.catalog = catalog;
        this.planner = planner;
        this.checker = checker;
        this.executor = executor;
    }

    public WorkflowPlanResponse plan(WorkflowPlanRequest request) {
        ArtifactType start = artifact(request.inputArtifactType(), "inputArtifactType");
        ArtifactType goal = artifact(request.goalArtifactType(), "goalArtifactType");
        int maxSteps = request.maxSteps() == null ? ChainPlanner.DEFAULT_MAX_STEPS
                : Math.max(1, Math.min(MAX_ALLOWED_STEPS, request.maxSteps()));

        // Only chains whose operations connect are offered; one that cannot run is not a candidate.
        List<WorkflowPlanResponse.Candidate> candidates = new ArrayList<>();
        List<OperationSpec> selectable = selectValidator(catalog.operations(), request.validationOperationId());
        for (List<OperationSpec> chain : planner.findChains(selectable, start, goal, maxSteps)) {
            ChainAssessment assessment = checker.assess(chain, start);
            if (assessment.status() == ChainAssessment.Status.READY) {
                candidates.add(new WorkflowPlanResponse.Candidate(views(chain), assessment));
            }
        }
        return new WorkflowPlanResponse(start.name(), goal.name(), request.validationOperationId(), candidates);
    }

    /** Checks a chosen or edited chain again, without calling any operation. */
    public WorkflowPlanResponse.Candidate assess(WorkflowAssessRequest request) {
        ArtifactType start = artifact(request.inputArtifactType(), "inputArtifactType");
        List<OperationSpec> chain = chain(request.operationIds());
        return new WorkflowPlanResponse.Candidate(views(chain), checker.assess(chain, start));
    }

    /** Active catalog operations by id, for checking several saved workflows against one snapshot. */
    public Map<Integer, OperationSpec> activeOperations() {
        return catalog.operations().stream()
                .collect(Collectors.toMap(OperationSpec::operationId, Function.identity()));
    }

    /**
     * Checks a saved chain against the current catalog. Unlike assess, an
     * operation that is no longer active is reported as NOT_SUPPORTED instead
     * of failing, so the workflow can still be opened and repaired.
     */
    public WorkflowPlanResponse.Candidate assessSaved(List<Integer> operationIds, String inputArtifactType,
                                                      Map<Integer, OperationSpec> active) {
        ArtifactType start = artifact(inputArtifactType, "inputArtifactType");
        List<OperationSpec> chain = new ArrayList<>();
        List<WorkflowStepView> steps = new ArrayList<>();
        List<ChainIssue> inactive = new ArrayList<>();
        for (int index = 0; index < operationIds.size(); index++) {
            Integer id = operationIds.get(index);
            OperationSpec operation = active.get(id);
            if (operation == null) {
                inactive.add(new ChainIssue(ChainIssue.Type.UNSUPPORTED_OPERATION, index + 1, null,
                        "Operation " + id + " is no longer active in the catalog."));
                steps.add(new WorkflowStepView(index + 1, id, "Operation " + id + " (no longer active)", null,
                        ArtifactType.UNKNOWN, ArtifactType.UNKNOWN, null));
            } else {
                chain.add(operation);
                steps.add(WorkflowStepView.of(index + 1, operation));
            }
        }
        if (!inactive.isEmpty()) {
            return new WorkflowPlanResponse.Candidate(steps,
                    new ChainAssessment(ChainAssessment.Status.NOT_SUPPORTED, inactive, List.of()));
        }
        return new WorkflowPlanResponse.Candidate(steps, checker.assess(chain, start));
    }

    /** Runs a chain the caller has checked to be READY; a canonical invoice input is checked against the catalog first. */
    public WorkflowRunResult execute(List<OperationSpec> chain, ArtifactType start, Object input) {
        if (input == null) throw new IllegalArgumentException("input is required when the workflow starts from " + start);
        if (start == ArtifactType.CANONICAL_INVOICE) catalog.requireCanonicalInvoice(input);
        return executor.run(new WorkflowDefinition(chain), start, input);
    }

    /** The active operations for these ids, in order; an unknown id is a NoSuchElementException. */
    public List<OperationSpec> chain(List<Integer> operationIds) {
        Map<Integer, OperationSpec> operations = activeOperations();
        List<OperationSpec> chain = new ArrayList<>();
        for (Integer id : operationIds) {
            OperationSpec operation = operations.get(id);
            if (operation == null) throw new NoSuchElementException("Active operation not found: " + id);
            chain.add(operation);
        }
        return chain;
    }

    private static List<WorkflowStepView> views(List<OperationSpec> chain) {
        List<WorkflowStepView> views = new ArrayList<>();
        for (int index = 0; index < chain.size(); index++) views.add(WorkflowStepView.of(index + 1, chain.get(index)));
        return views;
    }

    static ArtifactType artifact(String raw, String field) {
        ArtifactType type = ArtifactType.from(raw);
        if (type == ArtifactType.UNKNOWN) {
            throw new IllegalArgumentException(field + " must be one of "
                    + List.of(ArtifactType.values()).stream().filter(value -> value != ArtifactType.UNKNOWN)
                    .map(value -> value.name().toUpperCase(Locale.ROOT)).toList());
        }
        return type;
    }

    /** Keeps every non-validation operation and, when one is requested, only that validator. */
    private static List<OperationSpec> selectValidator(List<OperationSpec> operations, Integer validatorId) {
        if (validatorId == null) return operations;
        boolean known = operations.stream().anyMatch(operation -> validatorId.equals(operation.operationId())
                && operation.stage() == WorkflowStage.VALIDATION);
        if (!known) {
            throw new IllegalArgumentException("validationOperationId " + validatorId
                    + " is not an active validation operation");
        }
        return operations.stream().filter(operation -> operation.stage() != WorkflowStage.VALIDATION
                || validatorId.equals(operation.operationId())).toList();
    }
}
