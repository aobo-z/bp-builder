package com.bpbuilder.bp_builder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** The AWS Lambda function a saved workflow is deployed as; at most one per workflow. */
@Getter
@Setter
@Entity
@Table(name = "workflow_deployments")
public class WorkflowDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "workflow_id", nullable = false, unique = true)
    private Integer workflowId;

    @Column(name = "function_name", nullable = false)
    private String functionName;

    @Column(name = "function_url", nullable = false, columnDefinition = "TEXT")
    private String functionUrl;

    /** The Step Functions state machine that connects the workflow's component functions. */
    @Column(name = "state_machine_arn", columnDefinition = "TEXT")
    private String stateMachineArn;

    @Column(nullable = false)
    private String region;

    /** SHA-256 of the packaged workflow definition, to tell whether the workflow changed since. */
    @Column(name = "definition_hash", nullable = false)
    private String definitionHash;

    @Column(name = "deployed_at", nullable = false)
    private LocalDateTime deployedAt;
}
