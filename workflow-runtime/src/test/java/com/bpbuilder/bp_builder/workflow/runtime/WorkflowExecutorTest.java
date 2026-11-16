package com.bpbuilder.bp_builder.workflow.runtime;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.canonical.MappingTransform;
import com.bpbuilder.bp_builder.workflow.WorkflowFixtures;
import com.bpbuilder.bp_builder.workflow.definition.FieldMappingSpec;
import com.bpbuilder.bp_builder.workflow.definition.HttpEndpointSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.OutcomeRuleSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowDefinition;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;
import com.bpbuilder.bp_builder.workflow.mapping.JsonPath;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs whole chains against an in-memory HTTP fake; no network, no database. */
class WorkflowExecutorTest {

    private static final String SECRET_URL = "https://agents.example.test/v1/webhooks/secret-id/sync";
    private static final String PDF_BASE64 = "JVBERi0xLjQK";
    private static final String VALIDATOR_URL = "http://validator.test/validate?rules=Au-Nz+peppol-1.0.10";

    private final String validUbl = WorkflowFixtures.text(WorkflowFixtures.VALID_UBL);
    private final FakeTransport http = new FakeTransport();
    private final Map<String, String> environment = new LinkedHashMap<>(Map.of(WorkflowFixtures.UPBRAINS_URL_ENV, SECRET_URL));
    private final WorkflowExecutor executor = new WorkflowExecutor(http, environment::get);

    private final List<OperationSpec> fullChain = List.of(WorkflowFixtures.upbrainsExtractor(),
            WorkflowFixtures.jsonToUblConverter(), WorkflowFixtures.validator(), WorkflowFixtures.sender());

    WorkflowExecutorTest() {
        http.on(SECRET_URL, body -> json(WorkflowFixtures.upbrainsResponse()));
        http.on("http://converter.test/convert", body -> json(Map.of("xml", validUbl)));
        http.on(VALIDATOR_URL, body -> json(Map.of("valid", true, "errors", List.of())));
        http.on("http://sender.test/send", body -> json(Map.of("status", "ACCEPTED", "messageId", "MSG-1")));
    }

    @Test
    void communicationSendsTheUblFromTheConverterNotTheValidationReport() throws Exception {
        WorkflowRunResult result = run(fullChain);

        assertEquals(WorkflowRunResult.Status.COMPLETED, result.status(), result.message());
        Map<?, ?> sent = http.body("http://sender.test/send");
        assertEquals(validUbl, sent.get("document"));

        String base64 = Base64.getEncoder().encodeToString(validUbl.getBytes(StandardCharsets.UTF_8));
        Map<?, ?> validated = http.body(VALIDATOR_URL);
        assertEquals(base64, validated.get("content"));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                .digest(base64.getBytes(StandardCharsets.UTF_8))), validated.get("checksum"));

        assertEquals(PDF_BASE64, http.body(SECRET_URL).get("fileBase64"));
        assertEquals("TEST-BP-20261002-002", JsonPath.read(result.canonicalInvoice(), "invoice.id"));
        assertEquals(ValidationOutcome.Status.PASSED, result.validation().status());
        assertEquals(CommunicationOutcome.Status.ACCEPTED, result.communication().status());
        assertEquals("MSG-1", result.communication().reference());
        assertEquals(validUbl, result.artifacts().get("UBL_XML"));
    }

    @Test
    void theSecretEndpointUrlNeverAppearsInTheResult() throws Exception {
        WorkflowRunResult result = run(fullChain);

        String serialised = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);
        assertFalse(serialised.contains("secret-id"));
        assertTrue(result.steps().get(0).endpoint().contains(WorkflowFixtures.UPBRAINS_URL_ENV));
    }

    @Test
    void transportErrorsQuotingTheSecretUrlAreRedacted() {
        http.on(SECRET_URL, body -> {
            throw new IllegalArgumentException("Illegal character in path at index 5: " + SECRET_URL);
        });

        WorkflowRunResult result = run(fullChain);

        assertEquals(WorkflowRunResult.Status.FAILED, result.status());
        String message = result.steps().get(0).messages().get(0);
        assertFalse(message.contains("secret-id"));
        assertTrue(message.contains("<endpoint URL>"));
    }

    @Test
    void anInvalidInvoiceEndsTheRunBeforeCommunicationAsAnExpectedVerdict() {
        http.on(VALIDATOR_URL, body -> json(Map.of("valid", false,
                "errors", List.of("[BR-01] An Invoice shall have a Specification identifier."))));

        WorkflowRunResult result = run(fullChain);

        assertEquals(WorkflowRunResult.Status.INVOICE_INVALID, result.status());
        assertFalse(http.called("http://sender.test/send"));
        assertEquals(StepRecord.Status.SUCCEEDED, result.steps().get(2).status());
        assertEquals(List.of("Verdict: invalid · 1 rule violation."), result.steps().get(2).messages());
        assertEquals(StepRecord.Status.NOT_RUN, result.steps().get(3).status());
        assertEquals(List.of("Not run because the invoice did not pass validation."), result.steps().get(3).messages());
        assertEquals(List.of(new ValidationOutcome.Issue("[BR-01] An Invoice shall have a Specification identifier.", null)),
                result.validation().errors());
        assertNull(result.communication());
    }

    @Test
    void anUnreadableVerdictIsAFailureNotAPass() {
        http.on(VALIDATOR_URL, body -> json(Map.of("report", "done")));

        WorkflowRunResult result = run(fullChain);

        assertEquals(WorkflowRunResult.Status.FAILED, result.status());
        assertEquals(StepRecord.Status.FAILED, result.steps().get(2).status());
        assertEquals(ValidationOutcome.Status.UNDETERMINED, result.validation().status());
        assertFalse(http.called("http://sender.test/send"));
    }

    @Test
    void ublFromTheConverterIsHandedToValidationUnchangedAndTheValidatorDecides() {
        String questionable = "<ubl:Invoice xmlns:ubl=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"/>";
        http.on("http://converter.test/convert", body -> json(Map.of("xml", questionable)));
        http.on(VALIDATOR_URL, body -> json(Map.of("valid", false,
                "errors", List.of("[XSD] The document is not a complete invoice."))));

        WorkflowRunResult result = run(fullChain);

        String base64 = Base64.getEncoder().encodeToString(questionable.getBytes(StandardCharsets.UTF_8));
        assertEquals(base64, http.body(VALIDATOR_URL).get("content"));
        assertEquals(WorkflowRunResult.Status.INVOICE_INVALID, result.status());
        assertEquals(3, result.stoppedAtStep());
        assertEquals(StepRecord.Status.SUCCEEDED, result.steps().get(1).status());
        assertEquals("[XSD] The document is not a complete invoice.", result.validation().errors().get(0).message());
        assertFalse(http.called("http://sender.test/send"));
    }

    @Test
    void aConverterThatReturnsNoDocumentStillStopsTheRun() {
        http.on("http://converter.test/convert", body -> json(Map.of("xml", "")));

        WorkflowRunResult result = run(fullChain);

        assertEquals(WorkflowRunResult.Status.FAILED, result.status());
        assertEquals(2, result.stoppedAtStep());
        assertFalse(http.called(VALIDATOR_URL));
    }

    @Test
    void theSelectedRuleIsSentToTheValidatorAndTheValidatorOwnsTheBusinessVerdict() {
        WorkflowRunResult result = run(fullChain);

        assertEquals(WorkflowRunResult.Status.COMPLETED, result.status(), result.message());
        assertTrue(http.called(VALIDATOR_URL));
    }

    @Test
    void aCanonicalInvoiceInputIsLoadedIntoTheFirstOperationsRequest() {
        // The canonical invoice an extraction produced is itself a valid canonical JSON input.
        Map<String, Object> canonical = run(fullChain).canonicalInvoice();

        WorkflowRunResult result = executor.run(new WorkflowDefinition(List.of(WorkflowFixtures.jsonToUblConverter(),
                WorkflowFixtures.validator())), ArtifactType.CANONICAL_INVOICE, canonical);

        assertEquals(WorkflowRunResult.Status.COMPLETED, result.status(), result.message());
        String request = http.rawBody("http://converter.test/convert");
        assertTrue(request.contains("TEST-BP-20261002-002"), request);
        assertTrue(request.contains("Example Supplier Pty Ltd"), request);
    }

    @Test
    void aCanonicalInvoiceInputMustBeAJsonObject() {
        assertThrows(IllegalArgumentException.class, () -> executor.run(new WorkflowDefinition(
                List.of(WorkflowFixtures.jsonToUblConverter())), ArtifactType.CANONICAL_INVOICE, "not json"));
    }

    @Test
    void aMissingEndpointVariableFailsWithoutCallingAnything() {
        environment.clear();

        WorkflowRunResult result = run(fullChain);

        assertEquals(WorkflowRunResult.Status.FAILED, result.status());
        assertTrue(result.steps().get(0).messages().get(0).contains("UPBRAINS_INVOICE_EXTRACT_URL is not set"));
        assertTrue(http.calls.isEmpty());
    }

    @Test
    void outputThatBreaksAReviewedMappingStopsTheRun() {
        Map<String, Object> response = WorkflowFixtures.upbrainsResponse();
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) JsonPath.read(response,
                "$.extractor_result.documents[0].fields.VendorAddress.details");
        details.put("country_region", "Narnia");
        http.on(SECRET_URL, body -> json(response));

        WorkflowRunResult result = run(fullChain);

        assertEquals(WorkflowRunResult.Status.FAILED, result.status());
        assertTrue(result.steps().get(0).messages().get(0).contains("Narnia"));
        assertFalse(http.called("http://converter.test/convert"));
    }

    @Test
    void aServiceErrorStopsTheRun() {
        http.on(SECRET_URL, body -> new HttpTransport.Response(500, "text/plain", "upstream error"));

        WorkflowRunResult result = run(fullChain);

        assertEquals(WorkflowRunResult.Status.FAILED, result.status());
        assertEquals(500, result.steps().get(0).httpStatus());
        assertEquals(4, result.steps().size());
    }

    @Test
    void smartDocFlowApiKeyAndHttp400ProduceAReadableValidationFailure() {
        String url = "https://validator.test/schematron/ubl";
        environment.put("SMARTDOCFLOW_API_KEY", "test-key");
        http.on(url, body -> new HttpTransport.Response(400, "application/json", """
                {"status":"error","details":{"failures":[
                  {"message":"missing required attribute 'currencyID'","location":"/ubl:Invoice/cac:TaxTotal/cbc:TaxAmount"},
                  {"message":"missing required attribute 'currencyID'","location":"/ubl:Invoice/cac:InvoiceLine[1]/cbc:LineExtensionAmount"}]}}
                """));
        OperationSpec validator = new OperationSpec(30, "SmartDocFlow / EN16931", WorkflowStage.VALIDATION,
                ArtifactType.UBL_XML, ArtifactType.VALIDATION_REPORT, "VERIFIED_STAGING", "HTTP",
                new HttpEndpointSpec("POST", "https://validator.test", "/schematron/ubl", null,
                        "application/json", 30, "X_API_KEY", "SMARTDOCFLOW_API_KEY"),
                null, "$", List.of(
                new FieldMappingSpec(null, "$.filename", MappingTransform.CONSTANT,
                        Map.of("value", "invoice.xml"), true, 0),
                new FieldMappingSpec(null, "$.content", MappingTransform.ARTIFACT,
                        Map.of("artifact", "UBL_XML", "encodings", List.of("BASE64")), true, 0),
                new FieldMappingSpec(null, "$.profile", MappingTransform.CONSTANT,
                        Map.of("value", "ubl-en16931"), true, 0)), List.of(),
                new OutcomeRuleSpec("$.status", List.of("success"),
                        "$.details.failures[*]", null, null, "location"));

        WorkflowRunResult result = executor.run(new WorkflowDefinition(List.of(validator)),
                ArtifactType.UBL_XML, validUbl);

        assertEquals(WorkflowRunResult.Status.INVOICE_INVALID, result.status());
        assertEquals("The validator found the invoice invalid.", result.message());
        // The same message about two different elements stays two issues, each with its location.
        assertEquals(List.of(
                new ValidationOutcome.Issue("missing required attribute 'currencyID'", "/ubl:Invoice/cac:TaxTotal/cbc:TaxAmount"),
                new ValidationOutcome.Issue("missing required attribute 'currencyID'",
                        "/ubl:Invoice/cac:InvoiceLine[1]/cbc:LineExtensionAmount")), result.validation().errors());
        assertEquals(List.of("Verdict: invalid · 2 rule violations."), result.steps().get(0).messages());
        assertEquals("test-key", http.headers(url).get("x-api-key"));
        assertTrue(http.rawBody(url).contains("ubl-en16931"));
    }

    @Test
    void mailgunRequestUsesBasicAuthEnvironmentFieldsAndXmlAttachment() {
        String url = "https://api.mailgun.test/v3/example/messages";
        environment.put("MAILGUN_MESSAGES_URL", url);
        environment.put("MAILGUN_API_KEY", "mailgun-key");
        environment.put("MAILGUN_FROM", "BP Builder <sender@example.test>");
        environment.put("MAILGUN_TO", "recipient@example.test");
        http.on(url, body -> json(Map.of("id", "mail-1", "message", "Queued. Thank you.")));
        OperationSpec sender = new OperationSpec(40, "Mailgun / sendValidatedUblInvoice",
                WorkflowStage.COMMUNICATION, ArtifactType.UBL_XML, ArtifactType.DELIVERY_RECEIPT,
                "CONTRACT_REVIEWED", "HTTP",
                new HttpEndpointSpec("POST", "https://api.mailgun.net", "/v3/{domain}/messages",
                        "MAILGUN_MESSAGES_URL", "multipart/form-data", 30,
                        "BASIC_API_KEY", "MAILGUN_API_KEY"),
                null, "$", List.of(
                new FieldMappingSpec(null, "$.from", MappingTransform.ENVIRONMENT,
                        Map.of("name", "MAILGUN_FROM"), true, 0),
                new FieldMappingSpec(null, "$.to", MappingTransform.ENVIRONMENT,
                        Map.of("name", "MAILGUN_TO"), true, 0),
                new FieldMappingSpec(null, "$.subject", MappingTransform.CONSTANT,
                        Map.of("value", "Validated UBL invoice"), true, 0),
                new FieldMappingSpec(null, "$.attachment.filename", MappingTransform.CONSTANT,
                        Map.of("value", "invoice.xml"), true, 0),
                new FieldMappingSpec(null, "$.attachment.contentType", MappingTransform.CONSTANT,
                        Map.of("value", "application/xml"), true, 0),
                new FieldMappingSpec(null, "$.attachment.content", MappingTransform.ARTIFACT,
                        Map.of("artifact", "UBL_XML"), true, 0)), List.of(),
                new OutcomeRuleSpec("$.message", List.of("Queued. Thank you."), "$.message", null, "$.id"));

        WorkflowRunResult result = executor.run(new WorkflowDefinition(List.of(WorkflowFixtures.validator(), sender)),
                ArtifactType.UBL_XML, validUbl);

        assertEquals(WorkflowRunResult.Status.COMPLETED, result.status(), result.message());
        assertTrue(http.headers(url).get("Authorization").startsWith("Basic "));
        assertTrue(http.contentType(url).startsWith("multipart/form-data; boundary="));
        String body = http.rawBody(url);
        assertTrue(body.contains("name=\"from\""));
        assertTrue(body.contains("recipient@example.test"));
        assertTrue(body.contains("name=\"attachment\"; filename=\"invoice.xml\""));
        assertTrue(body.contains(validUbl));
        assertEquals("mail-1", result.communication().reference());
    }

    @Test
    void runningOneStepAtATimeThroughJsonGivesTheSameResultAsOneRun() throws Exception {
        WorkflowRunResult whole = run(fullChain);

        WorkflowRunResult stepped = runStepByStep(fullChain);

        assertEquals(WorkflowRunResult.Status.COMPLETED, stepped.status(), stepped.message());
        assertEquals(whole.message(), stepped.message());
        assertEquals(whole.canonicalInvoice().toString(), stepped.canonicalInvoice().toString());
        assertEquals(validUbl, stepped.artifacts().get("UBL_XML"));
        assertEquals(ValidationOutcome.Status.PASSED, stepped.validation().status());
        assertEquals("MSG-1", stepped.communication().reference());
    }

    @Test
    void aSteppedRunStopsAtAnInvalidInvoiceAndLeavesTheLaterStepsAlone() throws Exception {
        http.on(VALIDATOR_URL, body -> json(Map.of("valid", false, "errors", List.of("[BR-01] Missing id."))));

        WorkflowRunResult stepped = runStepByStep(fullChain);

        assertEquals(WorkflowRunResult.Status.INVOICE_INVALID, stepped.status());
        assertEquals(3, stepped.stoppedAtStep());
        assertEquals("The validator found the invoice invalid, so the remaining steps were not run.", stepped.message());
        assertFalse(http.called("http://sender.test/send"));
    }

    /** Runs each step on a state that went through JSON, as it does between Lambda functions. */
    private WorkflowRunResult runStepByStep(List<OperationSpec> chain) throws Exception {
        ObjectMapper json = new ObjectMapper();
        WorkflowState state = executor.start(chain.size(), ArtifactType.PDF_DOCUMENT, PDF_BASE64);
        for (int index = 0; index < chain.size(); index++) {
            state = json.readValue(json.writeValueAsString(state), WorkflowState.class);
            state = executor.step(chain.get(index), index + 1, state);
        }
        return executor.finish(json.readValue(json.writeValueAsString(state), WorkflowState.class));
    }

    private WorkflowRunResult run(List<OperationSpec> chain) {
        return executor.run(new WorkflowDefinition(chain), ArtifactType.PDF_DOCUMENT, PDF_BASE64);
    }

    private static HttpTransport.Response json(Object body) {
        try {
            return new HttpTransport.Response(200, "application/json", new ObjectMapper().writeValueAsString(body));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static final class FakeTransport implements HttpTransport {
        private final Map<String, Function<String, Response>> handlers = new LinkedHashMap<>();
        private final List<Call> calls = new ArrayList<>();

        void on(String url, Function<String, Response> handler) {
            handlers.put(url, handler);
        }

        boolean called(String url) {
            return calls.stream().anyMatch(call -> call.url().equals(url));
        }

        String rawBody(String url) {
            return call(url).body();
        }

        Map<?, ?> body(String url) {
            return jsonBody(url);
        }

        Map<String, String> headers(String url) {
            return call(url).headers();
        }

        String contentType(String url) {
            return call(url).contentType();
        }

        private Call call(String url) {
            return calls.stream().filter(call -> call.url().equals(url)).reduce((first, second) -> second)
                    .orElseThrow(() -> new AssertionError("not called: " + url));
        }

        Map<?, ?> jsonBody(String url) {
            try {
                return new ObjectMapper().readValue(rawBody(url), Map.class);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }

        @Override
        public Response send(String method, String url, String contentType, byte[] body, Duration timeout) {
            return send(method, url, contentType, Map.of(), body, timeout);
        }

        @Override
        public Response send(String method, String url, String contentType, Map<String, String> headers,
                             byte[] body, Duration timeout) {
            String text = new String(body, StandardCharsets.UTF_8);
            calls.add(new Call(url, contentType, Map.copyOf(headers), text));
            Function<String, Response> handler = handlers.get(url);
            if (handler == null) throw new IllegalStateException("unexpected call to " + url);
            return handler.apply(text);
        }

        private record Call(String url, String contentType, Map<String, String> headers, String body) {
        }
    }
}
