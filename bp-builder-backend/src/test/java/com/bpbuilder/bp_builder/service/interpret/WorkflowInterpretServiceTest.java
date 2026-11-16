package com.bpbuilder.bp_builder.service.interpret;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.dto.OperationCatalogItem;
import com.bpbuilder.bp_builder.dto.WorkflowInterpretation;
import com.bpbuilder.bp_builder.service.workflow.OperationCatalogService;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;
import com.bpbuilder.bp_builder.workflow.runtime.HttpTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowInterpretServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEXT = "Extract a PDF invoice, convert it to UBL, validate it against PINT A-NZ "
            + "and email it to the buyer if it is valid.";

    private final Map<String, String> environment = new HashMap<>(Map.of("GEMINI_API_KEY", "test-key"));
    private Map<String, String> sentHeaders;
    private String sentBody;
    private String sentUrl;

    @Test
    void anExplicitSendRequestAndANamedStandardBecomeTheGoalAndValidator() {
        WorkflowInterpretation result = service(answer("PDF_DOCUMENT", "VALIDATION_REPORT", true,
                "email it to the buyer", "207")).interpret(TEXT);

        assertEquals("PDF_DOCUMENT", result.inputArtifactType());
        assertEquals("DELIVERY_RECEIPT", result.goalArtifactType());
        assertEquals(207, result.validationOperationId());
        assertEquals("Validate a UBL invoice against Peppol PINT A-NZ Billing", result.validationOperationName());
        assertEquals("email it to the buyer", result.deliveryPhrase());
        assertTrue(result.questions().isEmpty());
        assertEquals("gemini-3.8-flash", result.model());
        assertEquals("test-key", sentHeaders.get("x-goog-api-key"));
        assertFalse(sentUrl.contains("test-key"));
        assertTrue(sentBody.contains("206: Validate a UBL invoice against EN16931"));
    }

    @Test
    void sendingIsNotAddedWhenTheQuotedWordsAreNotInTheRequirement() {
        WorkflowInterpretation result = service(answer("PDF_DOCUMENT", "VALIDATION_REPORT", true,
                "send it via Peppol", "NONE")).interpret("Validate my PDF invoice.");

        assertEquals("VALIDATION_REPORT", result.goalArtifactType());
        assertNull(result.deliveryPhrase());
        assertNull(result.validationOperationId());
    }

    @Test
    void unclearValuesBecomeQuestionsAndUnknownValidatorsAreIgnored() {
        WorkflowInterpretation result = service(answer("UNCLEAR", "UNCLEAR", false, "", "999"))
                .interpret("Make an invoice workflow.");

        assertNull(result.inputArtifactType());
        assertNull(result.goalArtifactType());
        assertNull(result.validationOperationId());
        assertEquals(2, result.questions().size());
    }

    @Test
    void aGoalThatDoesNotFollowTheInputIsQuestioned() {
        WorkflowInterpretation result = service(answer("UBL_XML", "CANONICAL_INVOICE", false, "", "NONE"))
                .interpret("Turn my UBL invoice into invoice data.");

        assertEquals("UBL_XML", result.inputArtifactType());
        assertNull(result.goalArtifactType());
        assertEquals(1, result.questions().size());
    }

    @Test
    void aMissingKeyOrAFailedCallIsReportedAsUnavailable() {
        environment.remove("GEMINI_API_KEY");
        assertThrows(InterpretationUnavailableException.class, () -> service(answer("PDF_DOCUMENT",
                "UBL_XML", false, "", "NONE")).interpret(TEXT));

        environment.put("GEMINI_API_KEY", "test-key");
        WorkflowInterpretService failing = service(new HttpTransport.Response(404, "application/json",
                "{\"error\":{\"message\":\"model not found\"}}"));
        InterpretationUnavailableException error = assertThrows(InterpretationUnavailableException.class,
                () -> failing.interpret(TEXT));
        assertEquals("Gemini answered HTTP 404: model not found", error.getMessage());
    }

    private WorkflowInterpretService service(HttpTransport.Response response) {
        HttpTransport transport = new HttpTransport() {
            @Override
            public Response send(String method, String url, String contentType, byte[] body,
                                 java.time.Duration timeout) {
                return send(method, url, contentType, Map.of(), body, timeout);
            }

            @Override
            public Response send(String method, String url, String contentType, Map<String, String> headers,
                                 byte[] body, java.time.Duration timeout) {
                sentUrl = url;
                sentHeaders = headers;
                sentBody = new String(body, StandardCharsets.UTF_8);
                return response;
            }
        };
        OperationCatalogService catalog = mock(OperationCatalogService.class);
        when(catalog.items()).thenReturn(List.of(
                validator(206, "Validate a UBL invoice against EN16931"),
                validator(207, "Validate a UBL invoice against Peppol PINT A-NZ Billing")));
        return new WorkflowInterpretService(new GeminiClient(transport, environment::get), catalog);
    }

    private static HttpTransport.Response answer(String input, String goal, boolean delivery, String phrase,
                                                 String validator) {
        try {
            String answer = MAPPER.writeValueAsString(Map.of("inputArtifact", input, "goalArtifact", goal,
                    "deliveryRequested", delivery, "deliveryPhrase", phrase, "validatorOperationId", validator,
                    "questions", List.of()));
            String body = MAPPER.writeValueAsString(Map.of("candidates", List.of(Map.of("content",
                    Map.of("role", "model", "parts", List.of(Map.of("text", answer)))))));
            return new HttpTransport.Response(200, "application/json", body);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static OperationCatalogItem validator(int id, String name) {
        return new OperationCatalogItem(id, "validate" + id, name, "ESS API", name,
                null, WorkflowStage.VALIDATION, ArtifactType.UBL_XML, ArtifactType.VALIDATION_REPORT, "VERIFIED",
                "OPENAPI", "HTTP", "POST", "application/json", "X_API_KEY", 3, 3, 0, true, 120, true);
    }
}
