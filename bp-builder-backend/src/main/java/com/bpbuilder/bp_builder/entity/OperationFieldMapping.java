package com.bpbuilder.bp_builder.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "operation_field_mappings")
public class OperationFieldMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "operation_id", nullable = false)
    private Integer operationId;

    /** Nullable for CONSTANT mappings that do not read a canonical field. */
    @Column(name = "canonical_field_id")
    private Integer canonicalFieldId;

    @Column(nullable = false)
    private String direction;

    @Column(name = "operation_field_path", nullable = false)
    private String operationFieldPath;

    @Column(name = "transform_type", nullable = false)
    private String transformType = "DIRECT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transform_config", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> transformConfig;

    @Column(name = "required_by_operation", nullable = false)
    private Boolean requiredByOperation = false;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(name = "review_status", nullable = false)
    private String reviewStatus = "DRAFT";

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String note;
}
