package com.bpbuilder.bp_builder.service.workflow;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.dto.SaveWorkflowRequest;
import com.bpbuilder.bp_builder.dto.SavedWorkflowView;
import com.bpbuilder.bp_builder.dto.UpdateWorkflowRequest;
import com.bpbuilder.bp_builder.dto.WorkflowPlanResponse;
import com.bpbuilder.bp_builder.dto.WorkflowSummary;
import com.bpbuilder.bp_builder.entity.Workflow;
import com.bpbuilder.bp_builder.entity.WorkflowRun;
import com.bpbuilder.bp_builder.entity.WorkflowStep;
import com.bpbuilder.bp_builder.repository.WorkflowRepository;
import com.bpbuilder.bp_builder.repository.WorkflowRunRepository;
import com.bpbuilder.bp_builder.service.deploy.WorkflowDeploymentService;
import com.bpbuilder.bp_builder.repository.WorkflowStepRepository;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Saves workflows as references to catalog operations. Every read checks the
 * steps against the current catalog, so a saved status never goes stale.
 */
@Service
public class WorkflowStoreService {

    private final WorkflowRepository workflows;
    private final WorkflowStepRepository steps;
    private final WorkflowRunRepository runs;
    private final WorkflowService workflowService;
    private final WorkflowDeploymentService deployments;

    public WorkflowStoreService(WorkflowRepository workflows, WorkflowStepRepository steps, WorkflowRunRepository runs,
                                WorkflowService workflowService, WorkflowDeploymentService deployments) {
        this.workflows = workflows;
        this.steps = steps;
        this.runs = runs;
        this.workflowService = workflowService;
        this.deployments = deployments;
    }

    @Transactional
    public SavedWorkflowView create(SaveWorkflowRequest request) {
        ArtifactType input = WorkflowService.artifact(request.inputArtifactType(), "inputArtifactType");
        ArtifactType goal = WorkflowService.artifact(request.goalArtifactType(), "goalArtifactType");
        workflowService.chain(request.operationIds());
        requireValidator(request.validationOperationId());

        LocalDateTime now = LocalDateTime.now();
        Workflow workflow = new Workflow();
        workflow.setName(request.name().trim());
        workflow.setRequirementText(blankToNull(request.requirementText()));
        workflow.setInputArtifactType(input.name());
        workflow.setGoalArtifactType(goal.name());
        workflow.setValidationOperationId(request.validationOperationId());
        workflow.setInterpretation(request.interpretation());
        workflow.setCreatedAt(now);
        workflow.setUpdatedAt(now);
        workflow = workflows.save(workflow);
        saveSteps(workflow.getId(), request.operationIds());
        return refresh(workflow, request.operationIds());
    }

    @Transactional(readOnly = true)
    public List<WorkflowSummary> list() {
        List<Workflow> saved = workflows.findAllByOrderByUpdatedAtDesc();
        if (saved.isEmpty()) return List.of();
        Map<Integer, List<Integer>> chains = steps.findAllByWorkflowIdInOrderByWorkflowIdAscSequenceAsc(
                        saved.stream().map(Workflow::getId).toList()).stream()
                .collect(Collectors.groupingBy(WorkflowStep::getWorkflowId,
                        Collectors.mapping(WorkflowStep::getOperationId, Collectors.toList())));
        Map<Integer, OperationSpec> active = workflowService.activeOperations();
        List<WorkflowSummary> result = new ArrayList<>();
        for (Workflow workflow : saved) {
            List<Integer> chain = chains.getOrDefault(workflow.getId(), List.of());
            String status = chain.isEmpty() ? "NOT_SUPPORTED" : workflowService
                    .assessSaved(chain, workflow.getInputArtifactType(), active).assessment().status().name();
            Optional<WorkflowRun> lastRun = runs.findFirstByWorkflowIdOrderByStartedAtDesc(workflow.getId());
            result.add(new WorkflowSummary(workflow.getId(), workflow.getName(), workflow.getInputArtifactType(),
                    workflow.getGoalArtifactType(), chain.size(), status, workflow.getUpdatedAt(),
                    lastRun.map(WorkflowRun::getStatus).orElse(null), lastRun.map(WorkflowRun::getStartedAt).orElse(null)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SavedWorkflowView get(Integer id) {
        Workflow workflow = find(id);
        return view(workflow, operationIds(id), workflowService.activeOperations());
    }

    @Transactional
    public SavedWorkflowView update(Integer id, UpdateWorkflowRequest request) {
        Workflow workflow = find(id);
        if (request.name() != null) {
            if (request.name().isBlank()) throw new IllegalArgumentException("name must not be blank");
            workflow.setName(request.name().trim());
        }
        List<Integer> chain = operationIds(id);
        if (request.operationIds() != null) {
            workflowService.chain(request.operationIds());
            steps.deleteAllOfWorkflow(id);
            saveSteps(id, request.operationIds());
            chain = request.operationIds();
        }
        workflow.setUpdatedAt(LocalDateTime.now());
        return refresh(workflow, chain);
    }

    /** Deletes the workflow and, first, its deployed Lambda function if it has one. */
    @Transactional
    public void delete(Integer id) {
        Workflow workflow = find(id);
        deployments.undeploy(id);
        workflows.delete(workflow);
    }

    /** Stores the current check result with the workflow and returns the full view. */
    private SavedWorkflowView refresh(Workflow workflow, List<Integer> chain) {
        SavedWorkflowView view = view(workflow, chain, workflowService.activeOperations());
        workflow.setAssessmentStatus(view.assessment().status().name());
        workflows.save(workflow);
        return view;
    }

    private SavedWorkflowView view(Workflow workflow, List<Integer> chain, Map<Integer, OperationSpec> active) {
        WorkflowPlanResponse.Candidate checked = workflowService.assessSaved(chain, workflow.getInputArtifactType(),
                active);
        return new SavedWorkflowView(workflow.getId(), workflow.getName(), workflow.getRequirementText(),
                workflow.getInputArtifactType(), workflow.getGoalArtifactType(), workflow.getValidationOperationId(),
                workflow.getInterpretation(), checked.steps(), checked.assessment(), workflow.getCreatedAt(),
                workflow.getUpdatedAt());
    }

    private void saveSteps(Integer workflowId, List<Integer> operationIds) {
        List<WorkflowStep> rows = new ArrayList<>();
        for (int index = 0; index < operationIds.size(); index++) {
            rows.add(new WorkflowStep(workflowId, index + 1, operationIds.get(index)));
        }
        steps.saveAll(rows);
    }

    private List<Integer> operationIds(Integer workflowId) {
        return steps.findAllByWorkflowIdOrderBySequenceAsc(workflowId).stream()
                .map(WorkflowStep::getOperationId).toList();
    }

    private Workflow find(Integer id) {
        return workflows.findById(id).orElseThrow(() -> new NoSuchElementException("Workflow not found: " + id));
    }

    private void requireValidator(Integer validationOperationId) {
        if (validationOperationId == null) return;
        OperationSpec operation = workflowService.activeOperations().get(validationOperationId);
        if (operation == null || operation.stage() != WorkflowStage.VALIDATION) {
            throw new IllegalArgumentException("validationOperationId " + validationOperationId
                    + " is not an active validation operation");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
