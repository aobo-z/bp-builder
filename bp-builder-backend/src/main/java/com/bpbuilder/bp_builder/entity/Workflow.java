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
import java.util.Map;

/** A workflow saved in BP Builder; its steps are WorkflowStep rows. */
@Getter
@Setter
@Entity
@Table(name = "workflows")
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(name = "requirement_text", columnDefinition = "TEXT")
    private String requirementText;

    @Column(name = "input_artifact_type", nullable = false)
    private String inputArtifactType;

    @Column(name = "goal_artifact_type", nullable = false)
    private String goalArtifactType;

    @Column(name = "validation_operation_id")
    private Integer validationOperationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> interpretation;

    @Column(name = "assessment_status")
    private String assessmentStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
