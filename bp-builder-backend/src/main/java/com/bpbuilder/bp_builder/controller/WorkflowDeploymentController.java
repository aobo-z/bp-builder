package com.bpbuilder.bp_builder.controller;

import com.bpbuilder.bp_builder.dto.WorkflowDeploymentView;
import com.bpbuilder.bp_builder.service.deploy.DeploymentException;
import com.bpbuilder.bp_builder.service.deploy.WorkflowDeploymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

/** Deploys a saved workflow as its own AWS Lambda function with a public function URL. */
@RestController
@RequestMapping("/api/workflows/{id:\\d+}/deployment")
public class WorkflowDeploymentController {

    private final WorkflowDeploymentService deployments;

    public WorkflowDeploymentController(WorkflowDeploymentService deployments) {
        this.deployments = deployments;
    }

    /** Creates the function, or updates it to the workflow's current definition. */
    @PostMapping
    public WorkflowDeploymentView deploy(@PathVariable Integer id) {
        return handle(() -> deployments.deploy(id));
    }

    /** The current deployment; 204 when the workflow has not been deployed. */
    @GetMapping
    public ResponseEntity<WorkflowDeploymentView> get(@PathVariable Integer id) {
        return handle(() -> deployments.get(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build()));
    }

    static <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (NoSuchElementException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        } catch (IllegalStateException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        } catch (DeploymentException error) {
            throw new ResponseStatusException(error.unavailable() ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.BAD_GATEWAY, error.getMessage(), error);
        }
    }
}
