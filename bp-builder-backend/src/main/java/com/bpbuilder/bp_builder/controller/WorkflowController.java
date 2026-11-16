package com.bpbuilder.bp_builder.controller;

import com.bpbuilder.bp_builder.dto.SketchDescription;
import com.bpbuilder.bp_builder.dto.WorkflowAssessRequest;
import com.bpbuilder.bp_builder.dto.WorkflowInterpretRequest;
import com.bpbuilder.bp_builder.dto.WorkflowInterpretation;
import com.bpbuilder.bp_builder.dto.WorkflowPlanRequest;
import com.bpbuilder.bp_builder.dto.WorkflowPlanResponse;
import com.bpbuilder.bp_builder.service.interpret.InterpretationUnavailableException;
import com.bpbuilder.bp_builder.service.interpret.WorkflowInterpretService;
import com.bpbuilder.bp_builder.service.workflow.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private static final Set<String> SKETCH_MIME_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");

    private final WorkflowService workflowService;
    private final WorkflowInterpretService interpretService;

    public WorkflowController(WorkflowService workflowService, WorkflowInterpretService interpretService) {
        this.workflowService = workflowService;
        this.interpretService = interpretService;
    }

    /** Reads a free-text requirement into the input, goal and optional validator of a plan request. */
    @PostMapping("/interpret")
    public WorkflowInterpretation interpret(@Valid @RequestBody WorkflowInterpretRequest request) {
        return handle(() -> interpretService.interpret(request.text()));
    }

    /** Reads a hand-drawn or photographed sketch into one description, for review before interpreting it. */
    @PostMapping(value = "/describe-sketch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SketchDescription describeSketch(@RequestParam("file") MultipartFile file) {
        return handle(() -> {
            if (file.isEmpty()) throw new IllegalArgumentException("No file was uploaded.");
            String mimeType = file.getContentType();
            if (mimeType == null || !SKETCH_MIME_TYPES.contains(mimeType)) {
                throw new IllegalArgumentException("Only PNG, JPEG or PDF files are accepted, not "
                        + (mimeType == null ? "an unknown file type" : mimeType) + ".");
            }
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException error) {
                throw new IllegalArgumentException("The uploaded file could not be read.");
            }
            return interpretService.describeSketch(bytes, mimeType);
        });
    }

    /** Lists every chain from the input to the goal, with what each one still needs. */
    @PostMapping("/plan")
    public WorkflowPlanResponse plan(@Valid @RequestBody WorkflowPlanRequest request) {
        return handle(() -> workflowService.plan(request));
    }

    /** Checks a chosen or edited chain again, without calling any operation. */
    @PostMapping("/assess")
    public WorkflowPlanResponse.Candidate assess(@Valid @RequestBody WorkflowAssessRequest request) {
        return handle(() -> workflowService.assess(request));
    }

    private static <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (NoSuchElementException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        } catch (InterpretationUnavailableException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage(), error);
        }
    }
}
