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

/** One execution of a saved workflow; the input document itself is not stored. */
@Getter
@Setter
@Entity
@Table(name = "workflow_runs")
public class WorkflowRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "workflow_id", nullable = false)
    private Integer workflowId;

    /** The chain as it ran; the workflow's steps can change afterwards. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operation_ids", nullable = false, columnDefinition = "jsonb")
    private List<Integer> operationIds;

    @Column(name = "input_artifact_type", nullable = false)
    private String inputArtifactType;

    @Column(name = "input_name")
    private String inputName;

    @Column(name = "input_size_bytes")
    private Long inputSizeBytes;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> steps;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> validation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> communication;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canonical_invoice", columnDefinition = "jsonb")
    private Map<String, Object> canonicalInvoice;

    /** Documents by artifact type, e.g. UBL_XML; the input PDF is only noted, not stored. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> artifacts;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
