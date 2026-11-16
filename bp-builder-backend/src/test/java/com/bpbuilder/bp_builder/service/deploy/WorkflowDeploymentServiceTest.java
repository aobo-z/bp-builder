package com.bpbuilder.bp_builder.service.deploy;

import com.bpbuilder.bp_builder.dto.WorkflowDeploymentView;
import com.bpbuilder.bp_builder.entity.Workflow;
import com.bpbuilder.bp_builder.entity.WorkflowDeployment;
import com.bpbuilder.bp_builder.entity.WorkflowStep;
import com.bpbuilder.bp_builder.repository.WorkflowDeploymentRepository;
import com.bpbuilder.bp_builder.repository.WorkflowRepository;
import com.bpbuilder.bp_builder.repository.WorkflowStepRepository;
import com.bpbuilder.bp_builder.service.workflow.WorkflowCatalogService;
import com.bpbuilder.bp_builder.service.workflow.WorkflowService;
import com.bpbuilder.bp_builder.workflow.WorkflowFixtures;
import com.bpbuilder.bp_builder.workflow.planning.ChainPlanner;
import com.bpbuilder.bp_builder.workflow.planning.CompletenessChecker;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowDeploymentServiceTest {

    @TempDir
    Path temp;

    private final ObjectMapper json = new ObjectMapper();
    private final WorkflowRepository workflows = mock(WorkflowRepository.class);
    private final WorkflowStepRepository steps = mock(WorkflowStepRepository.class);
    private final WorkflowDeploymentRepository deployments = mock(WorkflowDeploymentRepository.class);
    private final FakeAws aws = new FakeAws();
    private final Map<String, String> environment = new HashMap<>();
    private WorkflowDeploymentService service;

    @BeforeEach
    void setUp() throws IOException {
        WorkflowCatalogService catalog = mock(WorkflowCatalogService.class);
        when(catalog.operations()).thenReturn(List.of(WorkflowFixtures.upbrainsExtractor(),
                WorkflowFixtures.jsonToUblConverter(), WorkflowFixtures.validator(), WorkflowFixtures.sender()));
        ChainPlanner planner = new ChainPlanner();
        WorkflowService workflowService = new WorkflowService(catalog, planner, new CompletenessChecker(planner),
                new WorkflowExecutor((method, url, type, body, timeout) -> null, name -> null));
        when(deployments.save(any(WorkflowDeployment.class))).thenAnswer(call -> call.getArgument(0));

        environment.put("LAMBDA_RUNNER_JAR", jar("runner.jar", "software/amazon/awssdk/services/sfn/SfnClient.class"));
        environment.put("LAMBDA_COMPONENT_JAR", jar("component.jar", null));
        environment.put(WorkflowFixtures.UPBRAINS_URL_ENV, "https://extractor.example.test/sync");
        service = new WorkflowDeploymentService(workflows, steps, deployments, workflowService, aws, environment::get);
    }

    @Test
    void eachOperationBecomesAComponentWithOnlyItsOwnSecrets() throws IOException {
        saved(1, "PDF_DOCUMENT", 1, 2, 3);

        service.deploy(1);

        assertEquals(Map.of(WorkflowFixtures.UPBRAINS_URL_ENV, "https://extractor.example.test/sync"),
                aws.functions.get("bp-op-1").environment());
        assertEquals(Map.of(), aws.functions.get("bp-op-2").environment());
        assertFalse(aws.functions.get("bp-op-1").publicUrl());
        Map<String, byte[]> code = unzip(aws.functions.get("bp-op-1").code());
        assertTrue(code.containsKey("com/bpbuilder/lambda_runner/WorkflowLambdaHandler.class"));
        assertEquals(1, json.readTree(code.get("component.json")).at("/operation/operationId").asInt());
        assertFalse(code.containsKey("software/amazon/awssdk/services/sfn/SfnClient.class"));
    }

    @Test
    void theStateMachineCallsTheComponentsInOrderAndBranchesAfterValidation() throws IOException {
        saved(1, "PDF_DOCUMENT", 1, 2, 3);

        service.deploy(1);

        JsonNode machine = json.readTree(aws.machines.get("bp-workflow-1"));
        String first = machine.get("StartAt").asText();
        assertTrue(first.startsWith("1. "));
        JsonNode task = machine.at("/States").get(first);
        assertEquals("arn:bp-op-1", task.get("Resource").asText());
        assertEquals(1, task.at("/Parameters/sequence").asInt());
        assertEquals("$", task.at("/Parameters/state.$").asText());
        JsonNode verdict = machine.at("/States").get("Invoice valid? (step 3)");
        assertEquals("Choice", verdict.get("Type").asText());
        assertEquals("Completed", verdict.at("/Choices/0/Next").asText());
        assertEquals("Invoice invalid", verdict.at("/Choices/1/Next").asText());
        assertEquals("Stopped", verdict.get("Default").asText());
        // Only the validation step can end with an invalid invoice; other checks just continue or stop.
        JsonNode converted = machine.at("/States").get("Step 2 succeeded?");
        assertEquals(1, converted.get("Choices").size());
        assertEquals("3. " + WorkflowFixtures.validator().name(), converted.at("/Choices/0/Next").asText());
        assertEquals("Stopped", converted.get("Default").asText());
    }

    @Test
    void aWorkflowWithoutValidationHasNoInvalidInvoiceEnding() throws IOException {
        saved(3, "PDF_DOCUMENT", 1, 2);

        service.deploy(3);

        JsonNode states = json.readTree(aws.machines.get("bp-workflow-3")).get("States");
        assertFalse(states.has("Invoice invalid"));
        assertTrue(states.has("Completed"));
        assertTrue(states.has("Stopped"));
    }

    @Test
    void theEntryFunctionGetsTheUrlAndStartsTheStateMachine() throws IOException {
        saved(1, "PDF_DOCUMENT", 1, 2, 3);

        WorkflowDeploymentView view = service.deploy(1);

        FakeAws.Function entry = aws.functions.get("bp-workflow-1");
        assertTrue(entry.publicUrl());
        assertEquals(Map.of(), entry.environment());
        Map<String, byte[]> entryCode = unzip(entry.code());
        assertTrue(entryCode.containsKey("software/amazon/awssdk/services/sfn/SfnClient.class"));
        JsonNode packaged = json.readTree(entryCode.get("entry.json"));
        assertEquals("arn:machine:bp-workflow-1", packaged.get("stateMachineArn").asText());
        assertEquals(3, packaged.get("stepCount").asInt());
        assertEquals("PDF_DOCUMENT", packaged.get("inputArtifactType").asText());
        assertEquals("https://bp-workflow-1.lambda-url.test/", view.functionUrl());
        assertEquals("arn:machine:bp-workflow-1", view.stateMachineArn());
        assertTrue(view.upToDate());
    }

    @Test
    void unchangedComponentsPackageToIdenticalBytes() {
        saved(1, "PDF_DOCUMENT", 1, 2, 3);
        service.deploy(1);
        byte[] first = aws.functions.get("bp-op-3").code();

        service.deploy(1);

        assertArrayEquals(first, aws.functions.get("bp-op-3").code());
    }

    @Test
    void aWorkflowThatIsNotReadyIsNotDeployed() {
        saved(2, "PDF_DOCUMENT", 1, 3);

        assertThrows(IllegalStateException.class, () -> service.deploy(2));
        assertTrue(aws.functions.isEmpty());
    }

    @Test
    void aSecretTheBackendDoesNotHaveStopsTheDeployment() {
        environment.remove(WorkflowFixtures.UPBRAINS_URL_ENV);
        saved(1, "PDF_DOCUMENT", 1, 2, 3);

        DeploymentException error = assertThrows(DeploymentException.class, () -> service.deploy(1));

        assertTrue(error.unavailable());
        assertTrue(error.getMessage().contains(WorkflowFixtures.UPBRAINS_URL_ENV));
        assertTrue(aws.machines.isEmpty());
    }

    @Test
    void aDeploymentIsOutOfDateOnceTheWorkflowChanges() {
        saved(1, "PDF_DOCUMENT", 1, 2, 3);
        WorkflowDeployment row = new WorkflowDeployment();
        when(deployments.save(any(WorkflowDeployment.class))).thenAnswer(call -> {
            WorkflowDeployment deployment = call.getArgument(0);
            row.setWorkflowId(deployment.getWorkflowId());
            row.setFunctionName(deployment.getFunctionName());
            row.setDefinitionHash(deployment.getDefinitionHash());
            return deployment;
        });
        service.deploy(1);
        when(deployments.findByWorkflowId(1)).thenReturn(Optional.of(row));

        assertTrue(service.get(1).orElseThrow().upToDate());

        saved(1, "PDF_DOCUMENT", 1, 2);
        assertFalse(service.get(1).orElseThrow().upToDate());
    }

    @Test
    void undeployingRemovesTheEntryAndStateMachineButKeepsSharedComponents() {
        WorkflowDeployment row = new WorkflowDeployment();
        row.setFunctionName("bp-workflow-7");
        row.setStateMachineArn("arn:machine:bp-workflow-7");
        when(deployments.findByWorkflowId(7)).thenReturn(Optional.of(row));

        service.undeploy(7);

        assertEquals(List.of("function bp-workflow-7", "machine bp-workflow-7"), aws.deleted);
        verify(deployments).delete(row);
    }

    /** A stand-in runner jar; only the entry jar contains the Step Functions client. */
    private String jar(String name, String extraEntry) throws IOException {
        Path jar = temp.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new ZipEntry("com/bpbuilder/lambda_runner/WorkflowLambdaHandler.class"));
            out.write(new byte[]{1, 2, 3});
            out.closeEntry();
            if (extraEntry != null) {
                out.putNextEntry(new ZipEntry(extraEntry));
                out.closeEntry();
            }
        }
        return jar.toString();
    }

    private void saved(int id, String inputArtifactType, Integer... operationIds) {
        Workflow workflow = new Workflow();
        workflow.setId(id);
        workflow.setName("Workflow " + id);
        workflow.setInputArtifactType(inputArtifactType);
        when(workflows.findById(id)).thenReturn(Optional.of(workflow));
        List<WorkflowStep> rows = new ArrayList<>();
        for (int index = 0; index < operationIds.length; index++) {
            rows.add(new WorkflowStep(id, index + 1, operationIds[index]));
        }
        when(steps.findAllByWorkflowIdOrderBySequenceAsc(id)).thenReturn(rows);
    }

    private static Map<String, byte[]> unzip(byte[] code) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(code))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static final class FakeAws implements LambdaGateway {
        record Function(byte[] code, Map<String, String> environment, int timeout, boolean publicUrl) {
        }

        final Map<String, Function> functions = new LinkedHashMap<>();
        final Map<String, String> machines = new LinkedHashMap<>();
        final List<String> deleted = new ArrayList<>();

        @Override
        public DeployedFunction deployFunction(String name, byte[] code, Map<String, String> environment,
                                               int timeoutSeconds, boolean publicUrl) {
            functions.put(name, new Function(code, environment, timeoutSeconds, publicUrl));
            return new DeployedFunction("arn:" + name, publicUrl ? "https://" + name + ".lambda-url.test/" : null);
        }

        @Override
        public String deployStateMachine(String name, String definition) {
            machines.put(name, definition);
            return "arn:machine:" + name;
        }

        @Override
        public void deleteFunction(String name) {
            deleted.add("function " + name);
        }

        @Override
        public void deleteStateMachine(String name) {
            deleted.add("machine " + name);
        }

        @Override
        public String region() {
            return "ap-southeast-2";
        }
    }
}
