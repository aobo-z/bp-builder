package com.bpbuilder.lambda_runner;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.runtime.JdkHttpTransport;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowExecutor;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowRunResult;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowState;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * AWS Lambda adapter around the framework-independent workflow runtime. What a
 * deployed function does depends on the file packaged with it:
 *
 * - component.json: a component function for one catalog operation. A Step
 *   Functions task sends {"sequence": n, "state": {...}}; it runs that step and
 *   returns the next WorkflowState.
 * - entry.json: the entry function of one workflow. Its function URL (or a
 *   direct invocation) takes {"input": ...}, starts the workflow's state
 *   machine and answers with the run result once it has finished.
 *
 * Without either file the event is a complete WorkflowInvocation, run in this
 * function.
 */
public final class WorkflowLambdaHandler implements RequestStreamHandler {

    public static final String COMPONENT = "component.json";
    public static final String ENTRY = "entry.json";
    /** Step Functions passes at most 256 KB between steps, so the input is kept well below that. */
    static final int MAX_INPUT_CHARS = 160_000;

    /** The packaged definition of a component function. */
    public record Component(OperationSpec operation) {
    }

    /** The packaged definition of an entry function. */
    public record Entry(String stateMachineArn, ArtifactType inputArtifactType, int stepCount) {
    }

    private final ObjectMapper objectMapper;
    private final WorkflowExecutor executor;
    private final Component component;
    private final Entry entry;
    private final Supplier<StateMachineRunner> stateMachines;
    private StateMachineRunner runner;

    public WorkflowLambdaHandler() {
        // A lambda, not a constructor reference, so a component function (packaged without the AWS SDK)
        // never loads SfnStateMachineRunner.
        this(mapper(), new WorkflowExecutor(new JdkHttpTransport(), System::getenv), null, null,
                () -> new SfnStateMachineRunner());
    }

    WorkflowLambdaHandler(ObjectMapper objectMapper, WorkflowExecutor executor, Component component, Entry entry,
                          Supplier<StateMachineRunner> stateMachines) {
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.component = component != null ? component : packaged(objectMapper, COMPONENT, Component.class);
        this.entry = entry != null ? entry : packaged(objectMapper, ENTRY, Entry.class);
        this.stateMachines = stateMachines;
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        JsonNode event = objectMapper.readTree(input);
        if (component != null) {
            WorkflowState state = objectMapper.treeToValue(event.get("state"), WorkflowState.class);
            objectMapper.writeValue(output, executor.step(component.operation(), event.path("sequence").asInt(), state));
        } else if (entry != null) {
            runEntry(event, output);
        } else {
            WorkflowInvocation invocation = objectMapper.treeToValue(event, WorkflowInvocation.class);
            objectMapper.writeValue(output, executor.run(invocation.workflow(), invocation.inputArtifactType(),
                    invocation.input()));
        }
    }

    private void runEntry(JsonNode event, OutputStream output) throws IOException {
        boolean viaUrl = event.has("requestContext") || event.has("body");
        int status;
        Object body;
        try {
            JsonNode request = viaUrl ? requestBody(event) : event;
            if (request == null || !request.hasNonNull("input")) {
                throw new IllegalArgumentException("Send a JSON object {\"input\": ...} with the "
                        + entry.inputArtifactType() + " for this workflow.");
            }
            int size = objectMapper.writeValueAsString(request.get("input")).length();
            if (size > MAX_INPUT_CHARS) {
                throw new IllegalArgumentException("The input is " + size / 1000 + " KB; a deployed workflow accepts "
                        + "at most " + MAX_INPUT_CHARS / 1000 + " KB.");
            }
            Object value = objectMapper.treeToValue(request.get("input"), Object.class);
            WorkflowState first = executor.start(entry.stepCount(), entry.inputArtifactType(), value);
            if (runner == null) runner = stateMachines.get();
            String last = runner.run(entry.stateMachineArn(), objectMapper.writeValueAsString(first));
            status = 200;
            body = response(executor.finish(objectMapper.readValue(last, WorkflowState.class)));
        } catch (IllegalArgumentException | IOException error) {
            status = 400;
            body = Map.of("message", String.valueOf(error.getMessage()));
        } catch (IllegalStateException error) {
            status = 502;
            body = Map.of("message", String.valueOf(error.getMessage()));
        }
        if (!viaUrl) {
            objectMapper.writeValue(output, body);
            return;
        }
        Map<String, Object> http = new LinkedHashMap<>();
        http.put("statusCode", status);
        http.put("headers", Map.of("Content-Type", "application/json"));
        http.put("body", objectMapper.writeValueAsString(body));
        objectMapper.writeValue(output, http);
    }

    /** The JSON request of a function URL event; null when the body is missing. */
    private JsonNode requestBody(JsonNode event) {
        JsonNode body = event.get("body");
        if (body == null || body.isNull()) return null;
        String text = body.asText();
        if (event.path("isBase64Encoded").asBoolean(false)) {
            text = new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
        }
        try {
            return objectMapper.readTree(text);
        } catch (IOException error) {
            throw new IllegalArgumentException("The request body is not JSON.");
        }
    }

    /** What the caller needs from a run; per-step records are left out. */
    private static Map<String, Object> response(WorkflowRunResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.status());
        response.put("message", result.message());
        response.put("canonicalInvoice", result.canonicalInvoice());
        response.put("artifacts", result.artifacts());
        response.put("validation", result.validation());
        response.put("communication", result.communication());
        return response;
    }

    static ObjectMapper mapper() {
        return new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    private static <T> T packaged(ObjectMapper objectMapper, String file, Class<T> type) {
        try (InputStream stream = WorkflowLambdaHandler.class.getClassLoader().getResourceAsStream(file)) {
            return stream == null ? null : objectMapper.readValue(stream, type);
        } catch (IOException error) {
            throw new IllegalStateException("The packaged " + file + " cannot be read", error);
        }
    }
}
