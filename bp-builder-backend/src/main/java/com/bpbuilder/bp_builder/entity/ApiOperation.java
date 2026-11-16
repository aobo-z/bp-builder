package com.bpbuilder.bp_builder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "api_operations")
public class ApiOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "api_id", nullable = false)
    private Integer apiId;

    @Column(name = "operation_id")
    private String operationId;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String path;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "operation_category")
    private String operationCategory;

    /** Human-reviewed capability such as EXTRACT_INVOICE or CONVERT_TO_UBL. */
    @Column(name = "operation_type")
    private String operationType;

    /** HTTP for external APIs; INTERNAL is retained for existing built-in tools. */
    @Column(name = "execution_type", nullable = false)
    private String executionType = "HTTP";

    @Column(name = "input_artifact_type")
    private String inputArtifactType;

    @Column(name = "output_artifact_type")
    private String outputArtifactType;

    /** Path of the logical input/output artifact inside the HTTP body. */
    @Column(name = "input_artifact_path")
    private String inputArtifactPath;

    @Column(name = "output_artifact_path")
    private String outputArtifactPath;

    /** Name of an environment variable containing a complete endpoint URL. */
    @Column(name = "endpoint_url_ref")
    private String endpointUrlRef;

    @Column(name = "credential_ref")
    private String credentialRef;

    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus = "UNREVIEWED";

    @Column(name = "contract_source", nullable = false)
    private String contractSource = "OPENAPI";

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    /**
     * Human-reviewed rule for reading a validation or communication response,
     * e.g. {"passedPath":"$.valid","passedValues":[true],"errorsPath":"$.errors[*].message"}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outcome_rule", columnDefinition = "jsonb")
    private Map<String, Object> outcomeRule;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_media_types")
    private List<String> requestMediaTypes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_media_types")
    private Map<String, List<String>> responseMediaTypes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
