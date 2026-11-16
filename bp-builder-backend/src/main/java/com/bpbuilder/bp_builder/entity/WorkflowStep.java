package com.bpbuilder.bp_builder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One step of a saved workflow: a reference to a catalog operation, not a copy of it. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "workflow_steps")
public class WorkflowStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "workflow_id", nullable = false)
    private Integer workflowId;

    @Column(nullable = false)
    private Integer sequence;

    @Column(name = "operation_id", nullable = false)
    private Integer operationId;

    public WorkflowStep(Integer workflowId, Integer sequence, Integer operationId) {
        this.workflowId = workflowId;
        this.sequence = sequence;
        this.operationId = operationId;
    }
}
