package com.bpbuilder.bp_builder.controller;

import com.bpbuilder.bp_builder.dto.OperationCatalogItem;
import com.bpbuilder.bp_builder.dto.OperationMappingView;
import com.bpbuilder.bp_builder.service.workflow.OperationCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/operations")
public class OperationCatalogController {

    private final OperationCatalogService catalogService;

    public OperationCatalogController(OperationCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** Lists the active operations BP Builder can choose from, without endpoint or credential details. */
    @GetMapping
    public List<OperationCatalogItem> operations() {
        return catalogService.items();
    }

    /** Lists the approved field mappings of one active operation, without transform configuration. */
    @GetMapping("/{operationId}/mappings")
    public List<OperationMappingView> mappings(@PathVariable Integer operationId) {
        try {
            return catalogService.mappings(operationId);
        } catch (NoSuchElementException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        }
    }
}
