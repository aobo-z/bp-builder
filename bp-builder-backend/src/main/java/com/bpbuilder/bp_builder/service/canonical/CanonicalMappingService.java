package com.bpbuilder.bp_builder.service.canonical;

import com.bpbuilder.bp_builder.entity.CanonicalField;
import com.bpbuilder.bp_builder.entity.CanonicalModel;
import com.bpbuilder.bp_builder.entity.OperationFieldMapping;
import com.bpbuilder.bp_builder.repository.ApiOperationRepository;
import com.bpbuilder.bp_builder.repository.CanonicalFieldRepository;
import com.bpbuilder.bp_builder.repository.CanonicalModelRepository;
import com.bpbuilder.bp_builder.repository.OperationFieldMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class CanonicalMappingService {

    private final CanonicalModelRepository modelRepository;
    private final CanonicalFieldRepository fieldRepository;
    private final OperationFieldMappingRepository mappingRepository;
    private final ApiOperationRepository operationRepository;

    public CanonicalMappingService(CanonicalModelRepository modelRepository,
                                   CanonicalFieldRepository fieldRepository,
                                   OperationFieldMappingRepository mappingRepository,
                                   ApiOperationRepository operationRepository) {
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.mappingRepository = mappingRepository;
        this.operationRepository = operationRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> activeCatalog() {
        CanonicalModel model = modelRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new NoSuchElementException("No active canonical model"));
        List<CanonicalField> fields = fieldRepository.findAllByCanonicalModelIdOrderByIdAsc(model.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", model);
        result.put("fields", fields);
        return result;
    }

    @Transactional(readOnly = true)
    public List<OperationFieldMapping> mappings(Integer operationId) {
        requireOperation(operationId);
        return mappingRepository.findAllByOperationIdOrderByPriorityAscIdAsc(operationId);
    }

    private void requireOperation(Integer operationId) {
        if (!operationRepository.existsById(operationId)) {
            throw new NoSuchElementException("API operation not found: " + operationId);
        }
    }
}
