package com.bpbuilder.bp_builder.service.deploy;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.canonical.MappingTransform;
import com.bpbuilder.bp_builder.dto.WorkflowDeploymentView;
import com.bpbuilder.bp_builder.entity.Workflow;
import com.bpbuilder.bp_builder.entity.WorkflowDeployment;
import com.bpbuilder.bp_builder.entity.WorkflowStep;
import com.bpbuilder.bp_builder.repository.WorkflowDeploymentRepository;
import com.bpbuilder.bp_builder.repository.WorkflowRepository;
import com.bpbuilder.bp_builder.repository.WorkflowStepRepository;
import com.bpbuilder.bp_builder.service.workflow.WorkflowService;
import com.bpbuilder.bp_builder.workflow.definition.FieldMappingSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowDefinition;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;
import com.bpbuilder.bp_builder.workflow.planning.ChainAssessment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Deploys a READY saved workflow to AWS as connected components:
 *
 * - each catalog operation it uses becomes a component function bp-op-{id}
 *   (the lambda-runner jar with component.json and only the environment
 *   variables that operation references), shared by every workflow using it;
 * - the workflow becomes a Standard Step Functions state machine
 *   bp-workflow-{id} that calls the components in order and branches on the
 *   state each step returns, e.g. "Invoice valid?" after validation;
 * - an entry function bp-workflow-{id} (with entry.json) gives the workflow a
 *   public function URL, starts the state machine and returns its result.
 */
@Service
public class WorkflowDeploymentService {

    /** A stable timestamp for zip entries, so unchanged code packages to identical bytes. */
    private static final long ZIP_TIME = 315_532_800_000L;
    private static final List<String> LAMBDA_RETRYABLE = List.of("Lambda.ServiceException",
            "Lambda.AWSLambdaException", "Lambda.SdkClientException", "Lambda.TooManyRequestsException");
    /** Entry functions need the Step Functions client; components only call operations, so their jar is smaller. */
    private static final List<String> RUNNER_JARS = List.of(
            "lambda-runner/target/lambda-runner-0.0.1-SNAPSHOT-aws.jar",
            "../lambda-runner/target/lambda-runner-0.0.1-SNAPSHOT-aws.jar");
    private static final List<String> COMPONENT_JARS = List.of(
            "lambda-runner/target/lambda-runner-0.0.1-SNAPSHOT-component.jar",
            "../lambda-runner/target/lambda-runner-0.0.1-SNAPSHOT-component.jar");

    static final String COMPONENT = "component.json";
    static final String ENTRY = "entry.json";
    /** The entry function waits for the whole state machine execution. */
    private static final int ENTRY_TIMEOUT_SECONDS = 300;

    private final ObjectMapper json = new ObjectMapper();
    private final WorkflowRepository workflows;
    private final WorkflowStepRepository steps;
    private final WorkflowDeploymentRepository deployments;
    private final WorkflowService workflowService;
    private final LambdaGateway lambda;
    private final Function<String, String> environment;

    @Autowired
    public WorkflowDeploymentService(WorkflowRepository workflows, WorkflowStepRepository steps,
                                     WorkflowDeploymentRepository deployments, WorkflowService workflowService,
                                     LambdaGateway lambda) {
        this(workflows, steps, deployments, workflowService, lambda, System::getenv);
    }

    WorkflowDeploymentService(WorkflowRepository workflows, WorkflowStepRepository steps,
                              WorkflowDeploymentRepository deployments, WorkflowService workflowService,
                              LambdaGateway lambda, Function<String, String> environment) {
        this.workflows = workflows;
        this.steps = steps;
        this.deployments = deployments;
        this.workflowService = workflowService;
        this.lambda = lambda;
        this.environment = environment;
    }

    /** Deploys or updates the workflow; a workflow that is not READY is an IllegalStateException. */
    public WorkflowDeploymentView deploy(Integer workflowId) {
        Workflow workflow = find(workflowId);
        List<OperationSpec> chain = readyChain(workflow)
                .orElseThrow(() -> new IllegalStateException("Only a Ready workflow can be deployed."));
        ArtifactType start = ArtifactType.from(workflow.getInputArtifactType());
        String name = "bp-workflow-" + workflowId;

        Map<Integer, String> components = new LinkedHashMap<>();
        for (OperationSpec operation : chain) {
            if (components.containsKey(operation.operationId())) continue;
            byte[] code = packageCode(COMPONENT, json(Map.of("operation", operation)));
            components.put(operation.operationId(), lambda.deployFunction("bp-op-" + operation.operationId(), code,
                    variables(List.of(operation)), componentTimeout(operation), false).arn());
        }
        String stateMachine = lambda.deployStateMachine(name, stateMachine(workflow, chain, components));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("stateMachineArn", stateMachine);
        entry.put("inputArtifactType", start);
        entry.put("stepCount", chain.size());
        LambdaGateway.DeployedFunction function = lambda.deployFunction(name, packageCode(ENTRY, json(entry)),
                Map.of(), ENTRY_TIMEOUT_SECONDS, true);

        WorkflowDeployment deployment = deployments.findByWorkflowId(workflowId).orElseGet(WorkflowDeployment::new);
        deployment.setWorkflowId(workflowId);
        deployment.setFunctionName(name);
        deployment.setFunctionUrl(function.url());
        deployment.setStateMachineArn(stateMachine);
        deployment.setRegion(lambda.region());
        deployment.setDefinitionHash(sha256(definition(workflow, chain)));
        deployment.setDeployedAt(LocalDateTime.now());
        return view(deployments.save(deployment), workflow, true);
    }

    /** The workflow's deployment, if it has one, and whether it still matches the workflow. */
    public Optional<WorkflowDeploymentView> get(Integer workflowId) {
        Workflow workflow = find(workflowId);
        return deployments.findByWorkflowId(workflowId).map(deployment -> view(deployment, workflow,
                readyChain(workflow).map(chain -> sha256(definition(workflow, chain)))
                        .filter(deployment.getDefinitionHash()::equals).isPresent()));
    }

    /** Deletes the workflow's entry function and state machine; its components stay for other workflows. */
    public void undeploy(Integer workflowId) {
        deployments.findByWorkflowId(workflowId).ifPresent(deployment -> {
            lambda.deleteFunction(deployment.getFunctionName());
            if (deployment.getStateMachineArn() != null) lambda.deleteStateMachine(deployment.getFunctionName());
            deployments.delete(deployment);
        });
    }

    private Optional<List<OperationSpec>> readyChain(Workflow workflow) {
        List<Integer> operationIds = steps.findAllByWorkflowIdOrderBySequenceAsc(workflow.getId()).stream()
                .map(WorkflowStep::getOperationId).toList();
        Map<Integer, OperationSpec> active = workflowService.activeOperations();
        ChainAssessment assessment = workflowService.assessSaved(operationIds, workflow.getInputArtifactType(), active)
                .assessment();
        if (operationIds.isEmpty() || assessment.status() != ChainAssessment.Status.READY) return Optional.empty();
        return Optional.of(operationIds.stream().map(active::get).toList());
    }

    /** What a deployment is made from; its hash tells whether a deployment is still up to date. */
    private byte[] definition(Workflow workflow, List<OperationSpec> chain) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("deployment", "step-functions");
        definition.put("workflow", new WorkflowDefinition(chain));
        definition.put("inputArtifactType", ArtifactType.from(workflow.getInputArtifactType()));
        return json(definition);
    }

    /**
     * The Amazon States Language definition: one task per step, calling that
     * step's component with {"sequence": n, "state": <previous state>}, then a
     * choice that continues while the returned state has no status.
     */
    String stateMachine(Workflow workflow, List<OperationSpec> chain, Map<Integer, String> components) {
        Map<String, Object> states = new LinkedHashMap<>();
        List<String> tasks = new ArrayList<>();
        for (int index = 0; index < chain.size(); index++) {
            tasks.add(stateName((index + 1) + ". " + chain.get(index).name()));
        }
        for (int index = 0; index < chain.size(); index++) {
            OperationSpec operation = chain.get(index);
            int sequence = index + 1;
            String check = stateName(operation.stage() == WorkflowStage.VALIDATION
                    ? "Invoice valid? (step " + sequence + ")" : "Step " + sequence + " succeeded?");
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("Type", "Task");
            task.put("Resource", components.get(operation.operationId()));
            task.put("Parameters", Map.of("sequence", sequence, "state.$", "$"));
            task.put("Retry", List.of(Map.of("ErrorEquals", LAMBDA_RETRYABLE, "IntervalSeconds", 2,
                    "MaxAttempts", 2, "BackoffRate", 2)));
            task.put("Next", check);
            states.put(tasks.get(index), task);

            // Only a validation step can end the run with an invalid-invoice verdict.
            List<Map<String, Object>> rules = new ArrayList<>();
            rules.add(Map.of("Variable", "$.status", "IsNull", true,
                    "Next", sequence < chain.size() ? tasks.get(index + 1) : "Completed"));
            if (operation.stage() == WorkflowStage.VALIDATION) {
                rules.add(Map.of("Variable", "$.status", "StringEquals", "INVOICE_INVALID", "Next", "Invoice invalid"));
            }
            Map<String, Object> choice = new LinkedHashMap<>();
            choice.put("Type", "Choice");
            choice.put("Choices", rules);
            choice.put("Default", "Stopped");
            states.put(check, choice);
        }
        states.put("Completed", Map.of("Type", "Succeed"));
        if (chain.stream().anyMatch(operation -> operation.stage() == WorkflowStage.VALIDATION)) {
            states.put("Invoice invalid", Map.of("Type", "Succeed"));
        }
        states.put("Stopped", Map.of("Type", "Succeed"));

        Map<String, Object> machine = new LinkedHashMap<>();
        machine.put("Comment", "BP Builder workflow " + workflow.getId() + ": " + workflow.getName());
        machine.put("StartAt", tasks.get(0));
        machine.put("States", states);
        return new String(json(machine), StandardCharsets.UTF_8);
    }

    /** State names are limited to 80 characters. */
    private static String stateName(String name) {
        return name.length() <= 80 ? name : name.substring(0, 79) + "…";
    }

    /** A component waits for its operation's own timeout plus time for the function itself. */
    private static int componentTimeout(OperationSpec operation) {
        int timeout = operation.endpoint() == null ? 0 : operation.endpoint().timeoutSeconds();
        return Math.min(300, Math.max(60, timeout + 30));
    }

    private byte[] json(Object value) {
        try {
            return json.writeValueAsBytes(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("The deployment cannot be serialised", error);
        }
    }

    /** The environment variables the chain's operations reference, with this backend's values. */
    private Map<String, String> variables(List<OperationSpec> chain) {
        Set<String> names = new LinkedHashSet<>();
        for (OperationSpec operation : chain) {
            if (operation.endpoint() != null) {
                addName(names, operation.endpoint().endpointUrlRef());
                addName(names, operation.endpoint().credentialRef());
            }
            for (FieldMappingSpec mapping : operation.inputMappings()) {
                if (mapping.transform() == MappingTransform.ENVIRONMENT && mapping.config().get("name") != null) {
                    addName(names, String.valueOf(mapping.config().get("name")));
                }
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : names) {
            String value = environment.apply(name);
            if (value == null || value.isBlank()) {
                throw DeploymentException.unavailable("The backend has no value for " + name
                        + ", which this workflow's operations need.");
            }
            values.put(name, value);
        }
        return values;
    }

    private static void addName(Set<String> names, String name) {
        if (name != null && !name.isBlank()) names.add(name.trim());
    }

    /** The lambda-runner jar with one packaged file (component.json or entry.json) added at its root. */
    private byte[] packageCode(String file, byte[] content) {
        Path jar = file.equals(COMPONENT) ? jar("LAMBDA_COMPONENT_JAR", COMPONENT_JARS) : jar("LAMBDA_RUNNER_JAR", RUNNER_JARS);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar));
             ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (entry.getName().equals(COMPONENT) || entry.getName().equals(ENTRY)) continue;
                out.putNextEntry(stable(entry.getName()));
                in.transferTo(out);
                out.closeEntry();
            }
            out.putNextEntry(stable(file));
            out.write(content);
            out.closeEntry();
        } catch (IOException error) {
            throw DeploymentException.unavailable("The lambda-runner jar " + jar + " cannot be read: "
                    + error.getMessage());
        }
        return bytes.toByteArray();
    }

    private Path jar(String variable, List<String> defaults) {
        String configured = environment.apply(variable);
        List<String> candidates = configured == null || configured.isBlank() ? defaults : List.of(configured);
        return candidates.stream().map(Path::of).filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> DeploymentException.unavailable("The lambda-runner jar was not found; build it with "
                        + "\"mvnw -pl lambda-runner -am package\" or set " + variable + "."));
    }

    private static ZipEntry stable(String name) {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(ZIP_TIME);
        return entry;
    }

    private static WorkflowDeploymentView view(WorkflowDeployment deployment, Workflow workflow, boolean upToDate) {
        return new WorkflowDeploymentView(deployment.getWorkflowId(), deployment.getFunctionName(),
                deployment.getFunctionUrl(), deployment.getStateMachineArn(), deployment.getRegion(),
                workflow.getInputArtifactType(), deployment.getDeployedAt(), upToDate);
    }

    private Workflow find(Integer id) {
        return workflows.findById(id).orElseThrow(() -> new NoSuchElementException("Workflow not found: " + id));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
