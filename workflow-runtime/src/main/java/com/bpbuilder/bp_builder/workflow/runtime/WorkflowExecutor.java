package com.bpbuilder.bp_builder.workflow.runtime;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.workflow.definition.HttpEndpointSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowDefinition;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;
import com.bpbuilder.bp_builder.workflow.mapping.CanonicalInvoice;
import com.bpbuilder.bp_builder.workflow.mapping.CanonicalMapper;
import com.bpbuilder.bp_builder.workflow.mapping.JsonPath;
import com.bpbuilder.bp_builder.workflow.mapping.MappingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Runs a selected chain around one workflow context: the canonical invoice
 * (with the source of every value), the documents produced so far, and the
 * validation and communication outcomes. Each step's request is built from
 * that context, not from the previous step's raw response, so a communication
 * step sends the UBL produced earlier rather than the validation report.
 *
 * Stops (and reports why) when a required value is missing, validation does
 * not pass, or a call fails. A produced UBL document is handed on unchanged:
 * judging whether it is valid is the validation service's job. Nothing is
 * repaired or retried. The class has no Spring or database dependency, so the
 * same code can run inside AWS Lambda with a serialised WorkflowDefinition.
 */
public final class WorkflowExecutor {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final HttpTransport transport;
    private final Function<String, String> environment;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private final CanonicalMapper mapper = new CanonicalMapper();
    private final OutcomeInterpreter interpreter = new OutcomeInterpreter();
    private final RequestBodyEncoder bodyEncoder = new RequestBodyEncoder(objectMapper);

    public WorkflowExecutor(HttpTransport transport, Function<String, String> environment) {
        this.transport = transport;
        this.environment = environment;
    }

    /**
     * Runs the chain on one input. A CANONICAL_INVOICE input is a canonical invoice JSON object; its fields are
     * loaded into the run's canonical invoice so the first operation's request can be built from them.
     */
    public WorkflowRunResult run(WorkflowDefinition definition, ArtifactType inputType, Object inputValue) {
        List<OperationSpec> steps = definition.steps();
        Run run = new Run(steps, steps.size());
        load(run, inputType, inputValue);
        for (int index = 0; index < steps.size(); index++) {
            WorkflowRunResult stopped = runStep(run, steps.get(index), index + 1);
            if (stopped != null) return stopped;
        }
        return run.finish(WorkflowRunResult.Status.COMPLETED, null, completed(steps.size()));
    }

    /**
     * The state before the first of stepCount steps, with the input loaded.
     * Together with step and finish this runs a workflow one step at a time,
     * with exactly the behaviour of run, e.g. one Lambda function per step.
     */
    public WorkflowState start(int stepCount, ArtifactType inputType, Object inputValue) {
        Run run = new Run(List.of(), stepCount);
        load(run, inputType, inputValue);
        return run.state(null);
    }

    /** Runs one step on the state the previous step left; a run that has stopped is returned unchanged. */
    public WorkflowState step(OperationSpec operation, int sequence, WorkflowState state) {
        if (state.status() != null) return state;
        Run run = Run.from(state);
        return run.state(runStep(run, operation, sequence));
    }

    /** The result of a run made of steps, without per-step records. */
    public WorkflowRunResult finish(WorkflowState state) {
        WorkflowRunResult.Status status = state.status() == null ? WorkflowRunResult.Status.COMPLETED : state.status();
        String message = state.message() == null ? completed(state.stepCount()) : state.message();
        Map<ArtifactType, Object> artifacts = new EnumMap<>(ArtifactType.class);
        if (state.artifacts() != null) {
            state.artifacts().forEach((type, value) -> artifacts.put(ArtifactType.from(type), value));
        }
        return new WorkflowRunResult(status, state.stoppedAtStep(), message, List.of(), state.canonicalInvoice(),
                state.fieldSources(), artifactView(artifacts), state.validation(), state.communication());
    }

    private static String completed(int stepCount) {
        return "All " + stepCount + " steps completed.";
    }

    private static void load(Run run, ArtifactType inputType, Object inputValue) {
        if (inputValue != null) run.artifacts.put(inputType, inputValue);
        if (inputType == ArtifactType.CANONICAL_INVOICE) {
            if (!(inputValue instanceof Map<?, ?> json)) {
                throw new IllegalArgumentException("A CANONICAL_INVOICE input must be a canonical invoice JSON object");
            }
            run.invoice.load(json, CanonicalInvoice.FieldSource.input());
        }
    }

    /** Documents by artifact type name; an input file is only described, not repeated. */
    private static Map<String, Object> artifactView(Map<ArtifactType, Object> artifacts) {
        Map<String, Object> view = new LinkedHashMap<>();
        artifacts.forEach((type, value) -> view.put(type.name(), type.isFile()
                ? "(" + (type == ArtifactType.PDF_DOCUMENT ? "PDF" : "Image") + ", "
                        + String.valueOf(value).length() + " Base64 characters)" : value));
        return view;
    }

    /** Returns the final result when the workflow has to stop at this step, otherwise null. */
    private WorkflowRunResult runStep(Run run, OperationSpec operation, int sequence) {
        StepBuilder step = new StepBuilder(operation, sequence);

        if (operation.stage() == WorkflowStage.COMMUNICATION
                && (run.validation == null || run.validation.status() != ValidationOutcome.Status.PASSED)) {
            return run.stop(step.done(StepRecord.Status.FAILED, "Communication requires a passed validation earlier "
                    + "in the workflow."), WorkflowRunResult.Status.FAILED, "Communication was blocked because no "
                    + "validation step passed before it.");
        }
        CanonicalMapper.RequestBuild request;
        try {
            request = mapper.buildRequest(operation, run.invoice, run.artifacts, environment);
        } catch (MappingException error) {
            return run.stop(step.done(StepRecord.Status.FAILED, error.getMessage()),
                    WorkflowRunResult.Status.FAILED, "Could not build the request for " + operation.name() + ".");
        }
        step.request = Payloads.compact(request.body());
        if (!request.missing().isEmpty()) {
            List<String> messages = request.missing().stream().map(field -> "Missing required input: " + field).toList();
            return run.stop(step.done(StepRecord.Status.INPUT_REQUIRED, messages),
                    WorkflowRunResult.Status.INPUT_REQUIRED, operation.name() + " was not called because required "
                            + "values are missing.");
        }

        HttpTransport.Response response;
        long started = System.nanoTime();
        String url = null;
        try {
            url = resolveUrl(operation.endpoint(), request.queryParameters());
            RequestBodyEncoder.Encoded encoded = bodyEncoder.encode(operation.endpoint().contentType(), request.body());
            Map<String, String> headers = resolveHeaders(operation.endpoint());
            int timeout = operation.endpoint().timeoutSeconds() > 0
                    ? operation.endpoint().timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
            response = transport.send(operation.endpoint().method(), url, encoded.contentType(), headers,
                    encoded.body(), Duration.ofSeconds(timeout));
        } catch (Exception error) {
            step.durationMs = (System.nanoTime() - started) / 1_000_000;
            // Exception texts can quote the URL, which may be a secret webhook address.
            String reason = String.valueOf(error.getMessage());
            if (url != null) reason = reason.replace(url, "<endpoint URL>");
            return run.stop(step.done(StepRecord.Status.FAILED, "Request failed: " + reason),
                    WorkflowRunResult.Status.FAILED, operation.name() + " could not be called.");
        }
        step.durationMs = (System.nanoTime() - started) / 1_000_000;
        step.httpStatus = response.status();
        Object parsed = parse(response.body());
        step.response = Payloads.compact(parsed);
        boolean validationRejection = operation.stage() == WorkflowStage.VALIDATION
                && operation.outputArtifact() == ArtifactType.VALIDATION_REPORT
                && (response.status() == 400 || response.status() == 422);
        if ((response.status() < 200 || response.status() >= 300) && !validationRejection) {
            return run.stop(step.done(StepRecord.Status.FAILED, "HTTP " + response.status()),
                    WorkflowRunResult.Status.FAILED, operation.name() + " returned HTTP " + response.status() + ".");
        }

        try {
            if (!operation.outputMappings().isEmpty()) {
                mapper.applyOutput(parsed, operation.outputMappings(), run.invoice,
                        CanonicalInvoice.FieldSource.step(sequence, operation.name()));
            }
        } catch (MappingException error) {
            return run.stop(step.done(StepRecord.Status.FAILED, error.getMessage()),
                    WorkflowRunResult.Status.FAILED, "The output of " + operation.name()
                            + " does not match its reviewed mapping.");
        }
        return handleOutput(run, operation, step, parsed);
    }

    private Map<String, String> resolveHeaders(HttpEndpointSpec endpoint) {
        String method = endpoint.authMethod() == null ? "" : endpoint.authMethod().trim()
                .toUpperCase().replace('-', '_').replace(' ', '_');
        if (method.isBlank() || "NONE".equals(method) || "ENDPOINT_URL_IS_SECRET".equals(method)) {
            return Map.of();
        }
        String reference = endpoint.credentialRef();
        if (reference == null || reference.isBlank()) {
            throw new IllegalStateException("the operation has no credential environment variable reference");
        }
        String credential = environment.apply(reference);
        if (credential == null || credential.isBlank()) {
            throw new IllegalStateException("environment variable " + reference + " is not set");
        }
        return switch (method) {
            case "X_API_KEY" -> Map.of("x-api-key", credential.trim());
            case "BASIC_API_KEY" -> Map.of("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(("api:" + credential.trim()).getBytes(StandardCharsets.UTF_8)));
            default -> throw new IllegalStateException("unsupported authentication method " + endpoint.authMethod());
        };
    }

    private WorkflowRunResult handleOutput(Run run, OperationSpec operation, StepBuilder step, Object parsed) {
        ArtifactType output = operation.outputArtifact();
        Object artifact = operation.outputArtifactPath() == null ? parsed
                : JsonPath.read(parsed, operation.outputArtifactPath());

        switch (output) {
            case UBL_XML -> {
                if (!(artifact instanceof String xml) || xml.isBlank()) {
                    return run.stop(step.done(StepRecord.Status.FAILED, "No UBL document at "
                                    + operation.outputArtifactPath() + " in the response."),
                            WorkflowRunResult.Status.FAILED, operation.name() + " returned no UBL document.");
                }
                run.artifacts.put(ArtifactType.UBL_XML, xml);
            }
            case VALIDATION_REPORT -> {
                run.artifacts.put(ArtifactType.VALIDATION_REPORT, artifact);
                run.validation = interpreter.validation(operation.outcomeRule(), parsed, step.sequence);
                if (run.validation.status() == ValidationOutcome.Status.UNDETERMINED) {
                    String detail = run.validation.detail() == null
                            ? "The response did not contain the expected verdict." : run.validation.detail();
                    return run.stop(step.done(StepRecord.Status.FAILED, detail), WorkflowRunResult.Status.FAILED,
                            "The validation verdict could not be read; later steps were not run.",
                            "Not run because the validation verdict could not be read.");
                }
                if (run.validation.status() == ValidationOutcome.Status.FAILED) {
                    // An invalid invoice is a verdict, not a failure: the run ends here as expected.
                    int violations = run.validation.errors().size();
                    String verdict = "Verdict: invalid" + (violations == 0 ? "."
                            : " · " + violations + (violations == 1 ? " rule violation." : " rule violations."));
                    boolean more = step.sequence < run.stepCount;
                    return run.stop(step.done(StepRecord.Status.SUCCEEDED, verdict),
                            WorkflowRunResult.Status.INVOICE_INVALID, "The validator found the invoice invalid"
                                    + (more ? ", so the remaining steps were not run." : "."),
                            "Not run because the invoice did not pass validation.");
                }
            }
            case DELIVERY_RECEIPT -> {
                run.artifacts.put(ArtifactType.DELIVERY_RECEIPT, artifact);
                run.communication = interpreter.communication(operation.outcomeRule(), parsed, step.sequence);
                if (run.communication.status() == CommunicationOutcome.Status.REJECTED) {
                    return run.stop(step.done(StepRecord.Status.FAILED, run.communication.errors()),
                            WorkflowRunResult.Status.FAILED, operation.name() + " did not accept the invoice.");
                }
            }
            case CANONICAL_INVOICE -> {
                // Its content now lives in the canonical invoice via the output mappings.
            }
            default -> run.artifacts.put(output, artifact);
        }
        run.records.add(step.done(StepRecord.Status.SUCCEEDED, List.of()));
        return null;
    }

    private String resolveUrl(HttpEndpointSpec endpoint, Map<String, List<String>> queryParameters) {
        String url;
        if (endpoint.endpointUrlRef() != null && !endpoint.endpointUrlRef().isBlank()) {
            url = environment.apply(endpoint.endpointUrlRef());
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("environment variable " + endpoint.endpointUrlRef() + " is not set");
            }
            url = url.trim();
        } else {
            if (endpoint.baseUrl() == null || endpoint.baseUrl().isBlank()) {
                throw new IllegalStateException("the operation has no base URL");
            }
            if (endpoint.path() != null && endpoint.path().contains("{")) {
                throw new IllegalStateException("path parameters are not supported yet: " + endpoint.path());
            }
            url = endpoint.baseUrl().replaceAll("/+$", "") + (endpoint.path() == null ? "" : endpoint.path());
        }
        if (queryParameters.isEmpty()) return url;
        List<String> encoded = new ArrayList<>();
        queryParameters.forEach((key, values) -> values.forEach(value -> encoded.add(
                URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8))));
        return url + (url.contains("?") ? "&" : "?") + String.join("&", encoded);
    }

    private Object parse(String body) {
        if (body == null || body.isBlank()) return null;
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return objectMapper.readValue(trimmed, Object.class);
            } catch (Exception ignored) {
                return body;
            }
        }
        return body;
    }

    private static final class StepBuilder {
        final OperationSpec operation;
        final int sequence;
        Object request;
        Object response;
        Integer httpStatus;
        Long durationMs;

        StepBuilder(OperationSpec operation, int sequence) {
            this.operation = operation;
            this.sequence = sequence;
        }

        StepRecord done(StepRecord.Status status, String message) {
            return done(status, List.of(message));
        }

        StepRecord done(StepRecord.Status status, List<String> messages) {
            return new StepRecord(sequence, operation.operationId(), operation.name(),
                    operation.endpoint() == null ? null : operation.endpoint().displayName(), status, httpStatus,
                    durationMs, request, response, messages);
        }
    }

    private static final class Run {
        /** The steps listed as not run when the run stops; empty when steps run one at a time. */
        final List<OperationSpec> steps;
        final int stepCount;
        CanonicalInvoice invoice = new CanonicalInvoice();
        final Map<ArtifactType, Object> artifacts = new EnumMap<>(ArtifactType.class);
        final List<StepRecord> records = new ArrayList<>();
        ValidationOutcome validation;
        CommunicationOutcome communication;

        Run(List<OperationSpec> steps, int stepCount) {
            this.steps = steps;
            this.stepCount = stepCount;
        }

        static Run from(WorkflowState state) {
            Run run = new Run(List.of(), state.stepCount());
            run.invoice = CanonicalInvoice.restore(state.canonicalInvoice() == null ? Map.of() : state.canonicalInvoice(),
                    state.fieldSources());
            if (state.artifacts() != null) {
                state.artifacts().forEach((type, value) -> run.artifacts.put(ArtifactType.from(type), value));
            }
            run.validation = state.validation();
            run.communication = state.communication();
            return run;
        }

        /** This run as a state for the next step; stopped is the result when this step ended the run. */
        WorkflowState state(WorkflowRunResult stopped) {
            Map<String, Object> documents = new LinkedHashMap<>();
            artifacts.forEach((type, value) -> documents.put(type.name(), value));
            return new WorkflowState(stepCount, invoice.toJson(), invoice.fieldSources(), documents, validation,
                    communication, stopped == null ? null : stopped.status(),
                    stopped == null ? null : stopped.stoppedAtStep(), stopped == null ? null : stopped.message(),
                    records.isEmpty() ? null : records.get(0));
        }

        WorkflowRunResult stop(StepRecord failed, WorkflowRunResult.Status status, String message) {
            return stop(failed, status, message, "Not run because the workflow stopped at step " + failed.sequence() + ".");
        }

        WorkflowRunResult stop(StepRecord failed, WorkflowRunResult.Status status, String message, String skipReason) {
            records.add(failed);
            for (int index = failed.sequence(); index < steps.size(); index++) {
                OperationSpec skipped = steps.get(index);
                records.add(new StepRecord(index + 1, skipped.operationId(), skipped.name(),
                        skipped.endpoint() == null ? null : skipped.endpoint().displayName(),
                        StepRecord.Status.NOT_RUN, null, null, null, null,
                        List.of(skipReason)));
            }
            return finish(status, failed.sequence(), message);
        }

        WorkflowRunResult finish(WorkflowRunResult.Status status, Integer stoppedAt, String message) {
            return new WorkflowRunResult(status, stoppedAt, message, List.copyOf(records), invoice.toJson(),
                    invoice.fieldSources(), artifactView(artifacts), validation, communication);
        }
    }
}
