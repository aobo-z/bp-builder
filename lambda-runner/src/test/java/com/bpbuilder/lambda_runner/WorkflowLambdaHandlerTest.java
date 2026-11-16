package com.bpbuilder.lambda_runner;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.workflow.definition.HttpEndpointSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowDefinition;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;
import com.bpbuilder.bp_builder.workflow.runtime.HttpTransport;
import com.bpbuilder.bp_builder.workflow.runtime.JdkHttpTransport;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowExecutor;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowLambdaHandlerTest {

    private static final String MACHINE = "arn:aws:states:ap-southeast-2:123456789012:stateMachine:bp-workflow-1";

    private final ObjectMapper mapper = WorkflowLambdaHandler.mapper();
    private final HttpTransport converter = (method, url, contentType, body, timeout) ->
            new HttpTransport.Response(200, "application/json", "{\"xml\": \"<Invoice/>\"}");
    private final WorkflowExecutor executor = new WorkflowExecutor(converter, name -> null);

    @Test
    void executesASerialisedDefinitionWithoutSpringOrDatabaseAccess() throws Exception {
        WorkflowLambdaHandler handler = new WorkflowLambdaHandler(mapper,
                new WorkflowExecutor(new JdkHttpTransport(), name -> null), null, null, () -> null);

        JsonNode result = invoke(handler, """
                {"workflow": {"steps": []}, "inputArtifactType": "CANONICAL_INVOICE",
                 "input": {"invoice": {"id": "INV-1"}}}
                """);

        assertEquals("COMPLETED", result.get("status").asText());
    }

    @Test
    void aComponentFunctionRunsItsOneStepOnTheStateItIsGiven() throws Exception {
        WorkflowLambdaHandler handler = new WorkflowLambdaHandler(mapper, executor,
                new WorkflowLambdaHandler.Component(converterSpec()), null, () -> null);
        WorkflowState first = executor.start(2, ArtifactType.CANONICAL_INVOICE, Map.of("invoice", Map.of("id", "INV-1")));

        WorkflowState next = mapper.treeToValue(invoke(handler,
                mapper.writeValueAsString(Map.of("sequence", 1, "state", first))), WorkflowState.class);

        assertNull(next.status());
        assertEquals("<Invoice/>", next.artifacts().get("UBL_XML"));
        assertEquals(1, next.lastStep().sequence());
        assertEquals("INV-1", ((Map<?, ?>) next.canonicalInvoice().get("invoice")).get("id"));
    }

    @Test
    void anEntryFunctionStartsTheStateMachineWithTheInputAndAnswersWithItsResult() throws Exception {
        List<String> started = new ArrayList<>();
        StateMachineRunner machine = (arn, input) -> {
            started.add(arn);
            started.add(input);
            return input;
        };
        String body = "{\"input\": {\"invoice\": {\"id\": \"INV-1\"}}}";

        JsonNode http = invoke(entry(machine), mapper.writeValueAsString(Map.of("requestContext", Map.of(),
                "isBase64Encoded", true,
                "body", Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)))));

        assertEquals(200, http.get("statusCode").asInt());
        assertEquals(MACHINE, started.get(0));
        WorkflowState first = mapper.readValue(started.get(1), WorkflowState.class);
        assertEquals(2, first.stepCount());
        JsonNode result = mapper.readTree(http.get("body").asText());
        assertEquals("COMPLETED", result.get("status").asText());
        assertEquals("INV-1", result.at("/canonicalInvoice/invoice/id").asText());
        assertFalse(result.has("steps"));
    }

    @Test
    void anEntryFunctionRejectsAMissingOrOversizedInputWithoutStartingAnything() throws Exception {
        List<String> started = new ArrayList<>();
        WorkflowLambdaHandler handler = entry((arn, input) -> {
            started.add(arn);
            return input;
        });

        JsonNode missing = invoke(handler, "{\"requestContext\": {}, \"body\": \"{}\"}");
        JsonNode oversized = invoke(handler, mapper.writeValueAsString(Map.of("input",
                "x".repeat(WorkflowLambdaHandler.MAX_INPUT_CHARS + 1))));

        assertEquals(400, missing.get("statusCode").asInt());
        assertTrue(oversized.get("message").asText().contains("at most 160 KB"));
        assertTrue(started.isEmpty());
    }

    @Test
    void aFailedExecutionIsA502() throws Exception {
        WorkflowLambdaHandler handler = entry((arn, input) -> {
            throw new IllegalStateException("The workflow execution ended FAILED");
        });

        JsonNode http = invoke(handler, "{\"requestContext\": {}, \"body\": \"{\\\"input\\\": {}}\"}");

        assertEquals(502, http.get("statusCode").asInt());
        assertTrue(http.get("body").asText().contains("ended FAILED"));
    }

    @Test
    void roundTripsACompleteOperationDefinition() throws Exception {
        OperationSpec operation = new OperationSpec(42, "Extractor", WorkflowStage.TRANSFORMATION,
                ArtifactType.PDF_DOCUMENT, ArtifactType.CANONICAL_INVOICE, "VERIFIED", "HTTP",
                new HttpEndpointSpec("POST", null, null, "EXTRACTOR_URL", "application/json", 30),
                "$.fileBase64", null, List.of(), List.of(), null);
        WorkflowInvocation original = new WorkflowInvocation(new WorkflowDefinition(List.of(operation)),
                ArtifactType.PDF_DOCUMENT, "JVBERi0=");

        WorkflowInvocation restored = mapper.readValue(mapper.writeValueAsBytes(original), WorkflowInvocation.class);

        assertEquals(42, restored.workflow().steps().get(0).operationId());
        assertEquals("EXTRACTOR_URL", restored.workflow().steps().get(0).endpoint().endpointUrlRef());
    }

    private WorkflowLambdaHandler entry(StateMachineRunner machine) {
        return new WorkflowLambdaHandler(mapper, executor, null,
                new WorkflowLambdaHandler.Entry(MACHINE, ArtifactType.CANONICAL_INVOICE, 2), () -> machine);
    }

    private static OperationSpec converterSpec() {
        return new OperationSpec(190, "Converter", WorkflowStage.TRANSFORMATION, ArtifactType.CANONICAL_INVOICE,
                ArtifactType.UBL_XML, "VERIFIED", "HTTP",
                new HttpEndpointSpec("POST", "http://converter.test", "/convert", null, "application/json", 30),
                null, "$.xml", List.of(), List.of(), null);
    }

    private JsonNode invoke(WorkflowLambdaHandler handler, String event) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        handler.handleRequest(new ByteArrayInputStream(event.getBytes(StandardCharsets.UTF_8)), output, null);
        return mapper.readTree(output.toByteArray());
    }
}
