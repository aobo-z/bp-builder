package com.bpbuilder.bp_builder.service.workflow;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.dto.WorkflowRunRequest;
import com.bpbuilder.bp_builder.dto.WorkflowRunSummary;
import com.bpbuilder.bp_builder.dto.WorkflowRunView;
import com.bpbuilder.bp_builder.entity.Workflow;
import com.bpbuilder.bp_builder.entity.WorkflowRun;
import com.bpbuilder.bp_builder.entity.WorkflowStep;
import com.bpbuilder.bp_builder.repository.WorkflowRepository;
import com.bpbuilder.bp_builder.repository.WorkflowRunRepository;
import com.bpbuilder.bp_builder.repository.WorkflowStepRepository;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.planning.ChainAssessment;
import com.bpbuilder.bp_builder.workflow.runtime.StepRecord;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowRunResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Runs saved workflows and keeps each run. Only a READY workflow is run, and
 * no database transaction is held while the external operations are called.
 */
@Service
public class WorkflowRunService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final WorkflowRepository workflows;
    private final WorkflowStepRepository steps;
    private final WorkflowRunRepository runs;
    private final WorkflowService workflowService;

    public WorkflowRunService(WorkflowRepository workflows, WorkflowStepRepository steps, WorkflowRunRepository runs,
                              WorkflowService workflowService) {
        this.workflows = workflows;
        this.steps = steps;
        this.runs = runs;
        this.workflowService = workflowService;
    }

    /** Runs the workflow with this input and stores the run; a workflow that is not READY is an IllegalStateException. */
    public WorkflowRunView run(Integer workflowId, WorkflowRunRequest request) {
        Workflow workflow = workflows.findById(workflowId)
                .orElseThrow(() -> new NoSuchElementException("Workflow not found: " + workflowId));
        List<Integer> operationIds = steps.findAllByWorkflowIdOrderBySequenceAsc(workflowId).stream()
                .map(WorkflowStep::getOperationId).toList();
        Map<Integer, OperationSpec> active = workflowService.activeOperations();
        ChainAssessment assessment = workflowService.assessSaved(operationIds, workflow.getInputArtifactType(), active)
                .assessment();
        if (operationIds.isEmpty() || assessment.status() != ChainAssessment.Status.READY) {
            throw new IllegalStateException("This workflow is not ready to run"
                    + (assessment.issues().isEmpty() ? "." : ": " + assessment.issues().get(0).message()));
        }

        List<OperationSpec> chain = operationIds.stream().map(active::get).toList();
        ArtifactType start = ArtifactType.from(workflow.getInputArtifactType());
        LocalDateTime startedAt = LocalDateTime.now();
        WorkflowRunResult result = workflowService.execute(chain, start, request.input());

        WorkflowRun run = new WorkflowRun();
        run.setWorkflowId(workflowId);
        run.setOperationIds(operationIds);
        run.setInputArtifactType(start.name());
        run.setInputName(request.inputName() == null || request.inputName().isBlank() ? null : request.inputName().trim());
        run.setInputSizeBytes(inputSize(start, request.input()));
        run.setStatus(result.status().name());
        run.setMessage(result.message());
        run.setSteps(stepMaps(result.steps(), chain));
        run.setCanonicalInvoice(result.canonicalInvoice());
        run.setArtifacts(result.artifacts());
        run.setValidation(result.validation() == null ? null : JSON.convertValue(result.validation(), MAP));
        run.setCommunication(result.communication() == null ? null : JSON.convertValue(result.communication(), MAP));
        run.setStartedAt(startedAt);
        run.setFinishedAt(LocalDateTime.now());
        return view(runs.save(run), workflow.getName());
    }

    /** The most recent runs of every workflow, newest first. */
    @Transactional(readOnly = true)
    public List<WorkflowRunSummary> list() {
        List<WorkflowRun> recent = runs.findTop100ByOrderByStartedAtDesc();
        Map<Integer, String> names = names(recent.stream().map(WorkflowRun::getWorkflowId).distinct().toList());
        return recent.stream().map(run -> new WorkflowRunSummary(run.getId(), run.getWorkflowId(),
                names.get(run.getWorkflowId()), run.getInputArtifactType(), run.getInputName(), run.getStatus(),
                run.getMessage(), run.getStartedAt(), run.getFinishedAt())).toList();
    }

    @Transactional(readOnly = true)
    public WorkflowRunView get(Integer id) {
        WorkflowRun run = runs.findById(id).orElseThrow(() -> new NoSuchElementException("Run not found: " + id));
        return view(run, names(List.of(run.getWorkflowId())).get(run.getWorkflowId()));
    }

    private Map<Integer, String> names(List<Integer> workflowIds) {
        return workflows.findAllById(workflowIds).stream()
                .collect(Collectors.toMap(Workflow::getId, Workflow::getName));
    }

    /** Each step record with the stage and output artifact of the operation it ran. */
    private static List<Map<String, Object>> stepMaps(List<StepRecord> records, List<OperationSpec> chain) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (StepRecord record : records) {
            Map<String, Object> step = JSON.convertValue(record, MAP);
            if (record.sequence() >= 1 && record.sequence() <= chain.size()) {
                OperationSpec operation = chain.get(record.sequence() - 1);
                step.put("stage", operation.stage().name());
                step.put("outputArtifact", operation.outputArtifact().name());
            }
            result.add(step);
        }
        return result;
    }

    private static Long inputSize(ArtifactType start, Object input) {
        if (input instanceof String text) {
            if (!start.isFile()) return (long) text.getBytes(StandardCharsets.UTF_8).length;
            String base64 = text.strip();
            long padding = base64.endsWith("==") ? 2 : base64.endsWith("=") ? 1 : 0;
            return base64.length() * 3L / 4 - padding;
        }
        return (long) JSON.writeValueAsBytes(input).length;
    }

    private static WorkflowRunView view(WorkflowRun run, String workflowName) {
        return new WorkflowRunView(run.getId(), run.getWorkflowId(), workflowName, run.getOperationIds(),
                run.getInputArtifactType(), run.getInputName(), run.getInputSizeBytes(), run.getStatus(),
                run.getMessage(), run.getSteps(), run.getCanonicalInvoice(), run.getArtifacts(), run.getValidation(),
                run.getCommunication(), run.getStartedAt(), run.getFinishedAt());
    }
}
