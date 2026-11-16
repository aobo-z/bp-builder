package com.bpbuilder.bp_builder.controller;

import com.bpbuilder.bp_builder.dto.WorkflowRunRequest;
import com.bpbuilder.bp_builder.dto.WorkflowRunSummary;
import com.bpbuilder.bp_builder.dto.WorkflowRunView;
import com.bpbuilder.bp_builder.service.workflow.WorkflowRunService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/** Test runs of saved workflows. A run calls the real external operations and is stored. */
@RestController
public class WorkflowRunController {

    private final WorkflowRunService runs;

    public WorkflowRunController(WorkflowRunService runs) {
        this.runs = runs;
    }

    /** Runs a READY workflow with the given input; 409 when the workflow is not READY. */
    @PostMapping("/api/workflows/{id:\\d+}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowRunView run(@PathVariable Integer id, @Valid @RequestBody WorkflowRunRequest request) {
        return handle(() -> runs.run(id, request));
    }

    @GetMapping("/api/runs")
    public List<WorkflowRunSummary> list() {
        return runs.list();
    }

    @GetMapping("/api/runs/{id:\\d+}")
    public WorkflowRunView get(@PathVariable Integer id) {
        return handle(() -> runs.get(id));
    }

    private static <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (NoSuchElementException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
    }
}
