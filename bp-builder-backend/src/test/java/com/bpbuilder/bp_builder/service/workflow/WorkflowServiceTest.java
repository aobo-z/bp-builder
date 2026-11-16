package com.bpbuilder.bp_builder.service.workflow;

import com.bpbuilder.bp_builder.controller.WorkflowController;
import com.bpbuilder.bp_builder.dto.WorkflowAssessRequest;
import com.bpbuilder.bp_builder.dto.WorkflowPlanRequest;
import com.bpbuilder.bp_builder.dto.WorkflowPlanResponse;
import com.bpbuilder.bp_builder.service.interpret.WorkflowInterpretService;
import com.bpbuilder.bp_builder.workflow.WorkflowFixtures;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.planning.ChainAssessment;
import com.bpbuilder.bp_builder.workflow.planning.ChainPlanner;
import com.bpbuilder.bp_builder.workflow.planning.CompletenessChecker;
import com.bpbuilder.bp_builder.workflow.runtime.HttpTransport;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowServiceTest {

    private final List<String> calledUrls = new ArrayList<>();
    private WorkflowController controller;
    private WorkflowService service;

    @BeforeEach
    void setUp() {
        WorkflowCatalogService catalog = mock(WorkflowCatalogService.class);
        when(catalog.operations()).thenReturn(List.of(WorkflowFixtures.upbrainsExtractor(),
                WorkflowFixtures.jsonToUblConverter(), WorkflowFixtures.validator(), WorkflowFixtures.sender(),
                secondValidator()));

        String validUbl = WorkflowFixtures.text(WorkflowFixtures.VALID_UBL);
        HttpTransport transport = (method, url, contentType, body, timeout) -> {
            calledUrls.add(url);
            Object response = url.contains("converter") ? Map.of("xml", validUbl)
                    : url.contains("validator") ? Map.of("valid", true, "errors", List.of())
                    : url.contains("sender") ? Map.of("status", "ACCEPTED", "messageId", "MSG-1")
                    : WorkflowFixtures.upbrainsResponse();
            return new HttpTransport.Response(200, "application/json",
                    JsonMapper.builder().build().writeValueAsString(response));
        };
        WorkflowExecutor executor = new WorkflowExecutor(transport, name -> "https://extractor.example.test/sync");
        ChainPlanner planner = new ChainPlanner();
        service = new WorkflowService(catalog, planner, new CompletenessChecker(planner), executor);
        controller = new WorkflowController(service, mock(WorkflowInterpretService.class));
    }

    @Test
    void transformationCanBePlannedWithoutAValidator() {
        WorkflowPlanResponse response = controller.plan(
                new WorkflowPlanRequest("PDF_DOCUMENT", "UBL_XML", null, null));

        assertEquals(1, response.candidates().size());
        assertEquals(ChainAssessment.Status.READY, response.candidates().get(0).assessment().status());
    }

    @Test
    void planningValidationOffersEveryValidatorUnlessOneIsChosen() {
        WorkflowPlanResponse all = controller.plan(new WorkflowPlanRequest("PDF_DOCUMENT", "VALIDATION_REPORT",
                null, null));
        assertEquals(List.of(3, 5), all.candidates().stream()
                .map(candidate -> candidate.steps().get(2).operationId()).sorted().toList());

        WorkflowPlanResponse chosen = controller.plan(new WorkflowPlanRequest("PDF_DOCUMENT", "VALIDATION_REPORT",
                5, null));
        assertEquals(5, chosen.validationOperationId());
        assertEquals(1, chosen.candidates().size());
        assertEquals(5, chosen.candidates().get(0).steps().get(2).operationId());
    }

    @Test
    void choosingAnOperationThatIsNotAValidatorIsRejected() {
        ResponseStatusException notValidator = assertThrows(ResponseStatusException.class,
                () -> controller.plan(new WorkflowPlanRequest("PDF_DOCUMENT", "VALIDATION_REPORT",
                        2, null)));
        assertEquals(HttpStatus.BAD_REQUEST, notValidator.getStatusCode());
        assertTrue(calledUrls.isEmpty());
    }

    @Test
    void assessingAnEditedChainChecksItWithoutCallingAnything() {
        WorkflowPlanResponse.Candidate swapped = controller.assess(new WorkflowAssessRequest(List.of(1, 2, 5),
                "PDF_DOCUMENT"));
        assertEquals(ChainAssessment.Status.READY, swapped.assessment().status());
        assertEquals(5, swapped.steps().get(2).operationId());

        WorkflowPlanResponse.Candidate outOfOrder = controller.assess(new WorkflowAssessRequest(List.of(1, 3),
                "PDF_DOCUMENT"));
        assertEquals(ChainAssessment.Status.NOT_SUPPORTED, outOfOrder.assessment().status());
        assertTrue(calledUrls.isEmpty());
    }

    @Test
    void aSavedWorkflowWithAnInactiveOperationIsReportedNotSupportedInsteadOfFailing() {
        WorkflowPlanResponse.Candidate checked = service.assessSaved(List.of(1, 99, 3), "PDF_DOCUMENT",
                service.activeOperations());

        assertEquals(ChainAssessment.Status.NOT_SUPPORTED, checked.assessment().status());
        assertEquals(2, checked.assessment().issues().get(0).step());
        assertEquals(List.of(1, 99, 3), checked.steps().stream().map(step -> step.operationId()).toList());
    }

    @Test
    void badTypesAndUnknownOperationsBecomeHttpErrors() {
        ResponseStatusException badType = assertThrows(ResponseStatusException.class,
                () -> controller.plan(new WorkflowPlanRequest("WORD_FILE", "UBL_XML", null, null)));
        assertEquals(HttpStatus.BAD_REQUEST, badType.getStatusCode());

        ResponseStatusException unknownOperation = assertThrows(ResponseStatusException.class,
                () -> controller.assess(new WorkflowAssessRequest(List.of(99), "PDF_DOCUMENT")));
        assertEquals(HttpStatus.NOT_FOUND, unknownOperation.getStatusCode());
    }

    /** Another validator for the same input, as the catalog has for EN16931 and PINT A-NZ. */
    private static OperationSpec secondValidator() {
        OperationSpec base = WorkflowFixtures.validator();
        return new OperationSpec(5, "Second Validator / validate", base.stage(), base.inputArtifact(),
                base.outputArtifact(), base.verificationStatus(), base.executionType(), base.endpoint(),
                base.inputArtifactPath(), base.outputArtifactPath(), base.inputMappings(), base.outputMappings(),
                base.outcomeRule());
    }
}
