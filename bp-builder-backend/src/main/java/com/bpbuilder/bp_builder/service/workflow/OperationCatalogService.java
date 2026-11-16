package com.bpbuilder.bp_builder.service.workflow;

import com.bpbuilder.bp_builder.canonical.ArtifactType;
import com.bpbuilder.bp_builder.canonical.MappingDirection;
import com.bpbuilder.bp_builder.canonical.MappingTransform;
import com.bpbuilder.bp_builder.dto.OperationCatalogItem;
import com.bpbuilder.bp_builder.dto.OperationMappingView;
import com.bpbuilder.bp_builder.entity.Api;
import com.bpbuilder.bp_builder.entity.ApiOperation;
import com.bpbuilder.bp_builder.repository.ApiOperationRepository;
import com.bpbuilder.bp_builder.repository.ApiRepository;
import com.bpbuilder.bp_builder.workflow.definition.FieldMappingSpec;
import com.bpbuilder.bp_builder.workflow.definition.OperationSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only view of the reviewed catalog for the Operation Catalog page. */
@Service
public class OperationCatalogService {

    private final WorkflowCatalogService catalog;
    private final ApiRepository apiRepository;
    private final ApiOperationRepository operationRepository;

    public OperationCatalogService(WorkflowCatalogService catalog, ApiRepository apiRepository,
                                   ApiOperationRepository operationRepository) {
        this.catalog = catalog;
        this.apiRepository = apiRepository;
        this.operationRepository = operationRepository;
    }

    /** Every active operation of an active API, the same set the planner searches. */
    @Transactional(readOnly = true)
    public List<OperationCatalogItem> items() {
        Map<Integer, Api> apis = apiRepository.findAll().stream()
                .collect(Collectors.toMap(Api::getId, Function.identity()));
        Map<Integer, ApiOperation> rows = operationRepository.findAll().stream()
                .collect(Collectors.toMap(ApiOperation::getId, Function.identity()));
        return catalog.operations().stream()
                .map(spec -> item(spec, rows.get(spec.operationId()), apis))
                .sorted(Comparator.comparing((OperationCatalogItem item) -> item.stage() == null
                                ? Integer.MAX_VALUE : item.stage().ordinal())
                        .thenComparing(OperationCatalogItem::provider)
                        .thenComparing(OperationCatalogItem::name))
                .toList();
    }

    /** The approved mappings the workflow engine uses for one active operation. */
    @Transactional(readOnly = true)
    public List<OperationMappingView> mappings(Integer operationId) {
        OperationSpec spec = catalog.operations().stream()
                .filter(operation -> operation.operationId().equals(operationId)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Active operation not found: " + operationId));
        List<OperationMappingView> views = new ArrayList<>();
        spec.inputMappings().forEach(mapping -> views.add(view(MappingDirection.CANONICAL_TO_INPUT, mapping)));
        spec.outputMappings().forEach(mapping -> views.add(view(MappingDirection.OUTPUT_TO_CANONICAL, mapping)));
        return views;
    }

    private static OperationMappingView view(MappingDirection direction, FieldMappingSpec mapping) {
        Object fixedValue = mapping.transform() == MappingTransform.CONSTANT ? mapping.config().get("value") : null;
        String artifact = null;
        List<String> encodings = null;
        if (mapping.transform() == MappingTransform.ARTIFACT) {
            artifact = String.valueOf(mapping.config().get("artifact"));
            encodings = mapping.config().get("encodings") instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList() : List.of();
        }
        return new OperationMappingView(direction, mapping.canonicalPath(), mapping.operationPath(),
                mapping.transform(), mapping.required(), fixedValue, artifact, encodings);
    }

    private static OperationCatalogItem item(OperationSpec spec, ApiOperation row, Map<Integer, Api> apis) {
        Api api = apis.get(row.getApiId());
        String key = row.getOperationId() != null ? row.getOperationId() : row.getMethod() + " " + row.getPath();
        boolean chainable = spec.stage() != null && spec.inputArtifact() != ArtifactType.UNKNOWN
                && spec.outputArtifact() != ArtifactType.UNKNOWN;
        return new OperationCatalogItem(spec.operationId(), key, displayName(row, key), api.getName(),
                row.getSummary(), row.getDescription(), spec.stage(), spec.inputArtifact(), spec.outputArtifact(),
                spec.verificationStatus(), row.getContractSource(), spec.executionType(), row.getMethod(),
                spec.endpoint().contentType(), spec.endpoint().authMethod(), spec.inputMappings().size(),
                (int) spec.inputMappings().stream().filter(FieldMappingSpec::required).count(),
                spec.outputMappings().size(), spec.outcomeRule() != null && spec.outcomeRule().passedPath() != null,
                spec.endpoint().timeoutSeconds(), chainable);
    }

    private static String displayName(ApiOperation row, String key) {
        String summary = row.getSummary();
        return summary == null || summary.isBlank() || summary.length() > 80 ? key : summary.trim();
    }
}
