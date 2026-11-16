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
@Table(name = "canonical_fields")
public class CanonicalField {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "canonical_model_id", nullable = false)
    private Integer canonicalModelId;

    @Column(name = "field_path", nullable = false)
    private String fieldPath;

    @Column(name = "data_type", nullable = false)
    private String dataType;

    @Column(nullable = false)
    private String cardinality;

    @Column(name = "ubl_xpath", columnDefinition = "TEXT")
    private String ublXpath;

    @Column(name = "xsd_required", nullable = false)
    private Boolean xsdRequired = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> constraints;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
