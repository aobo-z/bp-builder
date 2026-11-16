package com.bpbuilder.bp_builder.service.workflow;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.canonical.MappingDirection;
import com.bpbuilder.bp_builder.canonical.MappingTransform;
import com.bpbuilder.bp_builder.entity.Api;
import com.bpbuilder.bp_builder.entity.ApiOperation;
import com.bpbuilder.bp_builder.entity.CanonicalField;
import com.bpbuilder.bp_builder.entity.CanonicalModel;
import com.bpbuilder.bp_builder.entity.OperationFieldMapping;
import com.bpbuilder.bp_builder.repository.ApiOperationRepository;
import com.bpbuilder.bp_builder.repository.ApiRepository;
import com.bpbuilder.bp_builder.repository.CanonicalFieldRepository;
import com.bpbuilder.bp_builder.repository.CanonicalModelRepository;
import com.bpbuilder.bp_builder.repository.OperationFieldMappingRepository;
import com.bpbuilder.bp_builder.workflow.definition.FieldMappingSpec;
import com.bpbuilder.bp_builder.workflow.definition.HttpEndpointSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import com.bpbuilder.bp_builder.workflow.definition.OutcomeRuleSpec;
import com.bpbuilder.bp_builder.workflow.definition.WorkflowStage;
import com.bpbuilder.bp_builder.workflow.mapping.CanonicalInvoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the Publisher-reviewed catalog into plain OperationSpec snapshots for
 * the planner and executor. Only APPROVED mappings are used. BP Builder does
 * not infer mappings or reimplement an external validation rule.
 */
@Service
public class WorkflowCatalogService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCatalogService.class);
    private static final String APPROVED = "APPROVED";

    private final ApiRepository apiRepository;
    private final ApiOperationRepository operationRepository;
    private final OperationFieldMappingRepository mappingRepository;
    private final CanonicalModelRepository modelRepository;
    private final CanonicalFieldRepository fieldRepository;

    public WorkflowCatalogService(ApiRepository apiRepository,
                                  ApiOperationRepository operationRepository,
                                  OperationFieldMappingRepository mappingRepository,
                                  CanonicalModelRepository modelRepository,
                                  CanonicalFieldRepository fieldRepository) {
        this.apiRepository = apiRepository;
        this.operationRepository = operationRepository;
        this.mappingRepository = mappingRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
    }

    /** Every active operation of an active API, as reviewed. */
    @Transactional(readOnly = true)
    public List<OperationSpec> operations() {
        Map<Integer, String> fieldPaths = activeFieldPaths();
        Map<Integer, Api> apis = apiRepository.findAll().stream()
                .collect(Collectors.toMap(Api::getId, Function.identity()));
        Map<Integer, List<OperationFieldMapping>> mappings = mappingRepository
                .findAllByReviewStatusOrderByOperationIdAscPriorityAscIdAsc(APPROVED).stream()
                .collect(Collectors.groupingBy(OperationFieldMapping::getOperationId, LinkedHashMap::new,
                        Collectors.toList()));

        List<OperationSpec> result = new ArrayList<>();
        for (ApiOperation operation : operationRepository.findAll()) {
            Api api = apis.get(operation.getApiId());
            if (Boolean.FALSE.equals(operation.getActive()) || api == null || Boolean.FALSE.equals(api.getActive())) {
                continue;
            }
            result.add(toSpec(operation, api, mappings.getOrDefault(operation.getId(), List.of()), fieldPaths));
        }
        return result;
    }

    /**
     * Checks that a CANONICAL_INVOICE input is a canonical invoice JSON object whose fields all belong to the
     * active canonical model, so a misspelt field is reported instead of silently ignored.
     */
    @Transactional(readOnly = true)
    public void requireCanonicalInvoice(Object input) {
        if (!(input instanceof Map<?, ?> json)) {
            throw new IllegalArgumentException("A CANONICAL_INVOICE input must be a canonical invoice JSON object");
        }
        Set<String> known = Set.copyOf(activeFieldPaths().values());
        List<String> unknown = CanonicalInvoice.fieldPaths(json).stream().filter(path -> !known.contains(path))
                .sorted().toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Not canonical invoice fields: " + unknown);
        }
    }

    private Map<Integer, String> activeFieldPaths() {
        CanonicalModel model = modelRepository.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new NoSuchElementException("No active canonical model"));
        return fieldRepository.findAllByCanonicalModelIdOrderByIdAsc(model.getId()).stream()
                .collect(Collectors.toMap(CanonicalField::getId, CanonicalField::getFieldPath));
    }

    private OperationSpec toSpec(ApiOperation operation, Api api, List<OperationFieldMapping> mappings,
                                 Map<Integer, String> fieldPaths) {
        WorkflowStage stage = WorkflowStage.fromCategory(operation.getOperationCategory());
        if (stage == null) stage = WorkflowStage.fromCategory(api.getCategory());
        String name = api.getName() + " / " + (operation.getOperationId() != null
                ? operation.getOperationId() : operation.getMethod() + " " + operation.getPath());

        List<FieldMappingSpec> inputs = new ArrayList<>();
        List<FieldMappingSpec> outputs = new ArrayList<>();
        for (OperationFieldMapping mapping : mappings) {
            FieldMappingSpec spec = toMappingSpec(mapping, fieldPaths, name);
            if (spec == null) continue;
            if (MappingDirection.CANONICAL_TO_INPUT.name().equals(mapping.getDirection())) inputs.add(spec);
            else if (MappingDirection.OUTPUT_TO_CANONICAL.name().equals(mapping.getDirection())) outputs.add(spec);
        }

        HttpEndpointSpec endpoint = new HttpEndpointSpec(operation.getMethod(), api.getBaseUrl(), operation.getPath(),
                operation.getEndpointUrlRef(), contentType(operation.getRequestMediaTypes()),
                operation.getTimeoutSeconds() == null ? 0 : operation.getTimeoutSeconds(),
                api.getAuthMethod(), operation.getCredentialRef());
        return new OperationSpec(operation.getId(), name, stage,
                ArtifactType.from(operation.getInputArtifactType()), ArtifactType.from(operation.getOutputArtifactType()),
                operation.getVerificationStatus(), operation.getExecutionType(), endpoint,
                operation.getInputArtifactPath(), operation.getOutputArtifactPath(), inputs, outputs,
                OutcomeRuleSpec.from(operation.getOutcomeRule()));
    }

    private FieldMappingSpec toMappingSpec(OperationFieldMapping mapping, Map<Integer, String> fieldPaths,
                                           String operationName) {
        MappingTransform transform;
        try {
            transform = MappingTransform.valueOf(mapping.getTransformType().trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException error) {
            log.warn("Ignoring mapping {} of {}: unknown transform {}", mapping.getId(), operationName,
                    mapping.getTransformType());
            return null;
        }
        String canonicalPath = null;
        if (mapping.getCanonicalFieldId() != null) {
            canonicalPath = fieldPaths.get(mapping.getCanonicalFieldId());
            if (canonicalPath == null) {
                log.warn("Ignoring mapping {} of {}: canonical field {} is not in the active model",
                        mapping.getId(), operationName, mapping.getCanonicalFieldId());
                return null;
            }
        }
        return new FieldMappingSpec(canonicalPath, mapping.getOperationFieldPath(), transform,
                mapping.getTransformConfig(), Boolean.TRUE.equals(mapping.getRequiredByOperation()),
                mapping.getPriority() == null ? 0 : mapping.getPriority());
    }

    private static String contentType(List<String> mediaTypes) {
        if (mediaTypes == null || mediaTypes.isEmpty()) return "application/json";
        return mediaTypes.stream().filter(type -> type != null && type.toLowerCase(Locale.ROOT).contains("json"))
                .findFirst().orElse(mediaTypes.get(0));
    }
}
