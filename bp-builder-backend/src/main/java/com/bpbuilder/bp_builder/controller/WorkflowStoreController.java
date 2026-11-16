package com.bpbuilder.bp_builder.controller;

import com.bpbuilder.bp_builder.dto.SaveWorkflowRequest;
import com.bpbuilder.bp_builder.dto.SavedWorkflowView;
import com.bpbuilder.bp_builder.dto.UpdateWorkflowRequest;
import com.bpbuilder.bp_builder.dto.WorkflowSummary;
import com.bpbuilder.bp_builder.service.workflow.WorkflowStoreService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/** Saved workflows. Planning, checking and running live in WorkflowController under the same path. */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowStoreController {

    private final WorkflowStoreService store;

    public WorkflowStoreController(WorkflowStoreService store) {
        this.store = store;
    }

    @GetMapping
    public List<WorkflowSummary> list() {
        return store.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedWorkflowView create(@Valid @RequestBody SaveWorkflowRequest request) {
        return handle(() -> store.create(request));
    }

    @GetMapping("/{id:\\d+}")
    public SavedWorkflowView get(@PathVariable Integer id) {
        return handle(() -> store.get(id));
    }

    /** Renames the workflow and/or replaces its steps, then checks it again. */
    @PutMapping("/{id:\\d+}")
    public SavedWorkflowView update(@PathVariable Integer id, @Valid @RequestBody UpdateWorkflowRequest request) {
        return handle(() -> store.update(id, request));
    }

    /** Also deletes the workflow's Lambda function; 503/502 when that cannot be done, and nothing is deleted. */
    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        handle(() -> WorkflowDeploymentController.handle(() -> {
            store.delete(id);
            return null;
        }));
    }

    private static <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (NoSuchElementException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
    }
}
