package com.bpbuilder.bp_builder.service.workflow;

import com.bpbuilder.bp_builder.controller.WorkflowRunController;
import com.bpbuilder.bp_builder.dto.WorkflowRunRequest;
import com.bpbuilder.bp_builder.dto.WorkflowRunView;
import com.bpbuilder.bp_builder.entity.Workflow;
import com.bpbuilder.bp_builder.entity.WorkflowRun;
import com.bpbuilder.bp_builder.entity.WorkflowStep;
import com.bpbuilder.bp_builder.repository.WorkflowRepository;
import com.bpbuilder.bp_builder.repository.WorkflowRunRepository;
import com.bpbuilder.bp_builder.repository.WorkflowStepRepository;
import com.bpbuilder.bp_builder.workflow.WorkflowFixtures;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRunServiceTest {

    private final List<String> calledUrls = new ArrayList<>();
    private final WorkflowRepository workflows = mock(WorkflowRepository.class);
    private final WorkflowStepRepository steps = mock(WorkflowStepRepository.class);
    private final WorkflowRunRepository runs = mock(WorkflowRunRepository.class);
    private WorkflowCatalogService catalog;
    private WorkflowRunController controller;

    @BeforeEach
    void setUp() {
        catalog = mock(WorkflowCatalogService.class);
        when(catalog.operations()).thenReturn(List.of(WorkflowFixtures.upbrainsExtractor(),
                WorkflowFixtures.jsonToUblConverter(), WorkflowFixtures.validator(), WorkflowFixtures.sender()));
        when(runs.save(any(WorkflowRun.class))).thenAnswer(call -> {
            WorkflowRun run = call.getArgument(0);
            run.setId(7);
            return run;
        });

        String validUbl = WorkflowFixtures.text(WorkflowFixtures.VALID_UBL);
        HttpTransport transport = (method, url, contentType, body, timeout) -> {
            calledUrls.add(url);
            Object response = url.contains("converter") ? Map.of("xml", validUbl)
                    : url.contains("validator") ? Map.of("valid", true, "errors", List.of())
                    : WorkflowFixtures.upbrainsResponse();
            return new HttpTransport.Response(200, "application/json",
                    JsonMapper.builder().build().writeValueAsString(response));
        };
        WorkflowExecutor executor = new WorkflowExecutor(transport, name -> "https://extractor.example.test/sync");
        ChainPlanner planner = new ChainPlanner();
        WorkflowService workflowService = new WorkflowService(catalog, planner, new CompletenessChecker(planner),
                executor);
        controller = new WorkflowRunController(new WorkflowRunService(workflows, steps, runs, workflowService));
    }

    @Test
    void aReadyWorkflowRunIsStoredWithWhatItProduced() {
        saved(1, "PDF_DOCUMENT", 1, 2, 3);

        WorkflowRunView run = controller.run(1, new WorkflowRunRequest("JVBERi0=", "invoice.pdf"));

        assertEquals(7, run.id());
        assertEquals("COMPLETED", run.status(), run.message());
        assertEquals("invoice.pdf", run.inputName());
        assertEquals(5L, run.inputSizeBytes());
        assertEquals(List.of(1, 2, 3), run.operationIds());
        assertEquals("TRANSFORMATION", run.steps().get(0).get("stage"));
        assertEquals("VALIDATION", run.steps().get(2).get("stage"));
        assertEquals("UBL_XML", run.steps().get(1).get("outputArtifact"));
        assertTrue(String.valueOf(run.canonicalInvoice()).contains("TEST-BP-20261002-002"));
        assertEquals(WorkflowFixtures.text(WorkflowFixtures.VALID_UBL), run.artifacts().get("UBL_XML"));
        assertEquals("PASSED", run.validation().get("status"));
        assertEquals(3, calledUrls.size());
        verify(runs).save(any(WorkflowRun.class));
    }

    @Test
    void aCanonicalInvoiceInputIsCheckedAgainstTheCatalogBeforeTheRun() {
        saved(1, "PDF_DOCUMENT", 1, 2, 3);
        Map<String, Object> canonical = controller.run(1, new WorkflowRunRequest("JVBERi0=", null)).canonicalInvoice();
        calledUrls.clear();
        saved(2, "CANONICAL_INVOICE", 2, 3);

        WorkflowRunView run = controller.run(2, new WorkflowRunRequest(canonical, "invoice.json"));

        assertEquals("COMPLETED", run.status(), run.message());
        verify(catalog).requireCanonicalInvoice(canonical);
        assertEquals(2, calledUrls.size());
    }

    @Test
    void aCanonicalInvoiceInputWithUnknownFieldsIsABadRequestAndNotStored() {
        saved(2, "CANONICAL_INVOICE", 2, 3);
        Map<String, Object> input = Map.of("invoice", Map.of("number", "INV-1"));
        doThrow(new IllegalArgumentException("Not canonical invoice fields: [invoice.number]"))
                .when(catalog).requireCanonicalInvoice(input);

        ResponseStatusException rejected = assertThrows(ResponseStatusException.class,
                () -> controller.run(2, new WorkflowRunRequest(input, null)));

        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
        assertTrue(calledUrls.isEmpty());
        verify(runs, never()).save(any(WorkflowRun.class));
    }

    @Test
    void aWorkflowThatIsNotReadyIsNotRun() {
        saved(3, "PDF_DOCUMENT", 1, 3);

        ResponseStatusException rejected = assertThrows(ResponseStatusException.class,
                () -> controller.run(3, new WorkflowRunRequest("JVBERi0=", null)));

        assertEquals(HttpStatus.CONFLICT, rejected.getStatusCode());
        assertTrue(calledUrls.isEmpty());
        verify(runs, never()).save(any(WorkflowRun.class));
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
}
