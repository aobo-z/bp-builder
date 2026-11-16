package com.bpbuilder.bp_builder.repository;

import com.bpbuilder.bp_builder.entity.WorkflowDeployment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowDeploymentRepository extends JpaRepository<WorkflowDeployment, Integer> {
    Optional<WorkflowDeployment> findByWorkflowId(Integer workflowId);
}
