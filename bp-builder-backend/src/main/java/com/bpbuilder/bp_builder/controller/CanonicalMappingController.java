package com.bpbuilder.bp_builder.controller;

import com.bpbuilder.bp_builder.entity.OperationFieldMapping;
import com.bpbuilder.bp_builder.service.canonical.CanonicalMappingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/canonical")
public class CanonicalMappingController {

    private final CanonicalMappingService mappingService;

    public CanonicalMappingController(CanonicalMappingService mappingService) {
        this.mappingService = mappingService;
    }

    @GetMapping("/models/active")
    public Map<String, Object> activeModel() {
        try {
            return mappingService.activeCatalog();
        } catch (NoSuchElementException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        }
    }

    @GetMapping("/operations/{operationId}/mappings")
    public List<OperationFieldMapping> mappings(@PathVariable Integer operationId) {
        try {
            return mappingService.mappings(operationId);
        } catch (NoSuchElementException error) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        }
    }

}
