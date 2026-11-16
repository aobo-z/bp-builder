package com.bpbuilder.bp_builder.repository;

import com.bpbuilder.bp_builder.entity.WorkflowRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, Integer> {
    List<WorkflowRun> findAllByWorkflowIdOrderByStartedAtDesc(Integer workflowId);

    List<WorkflowRun> findTop100ByOrderByStartedAtDesc();

    Optional<WorkflowRun> findFirstByWorkflowIdOrderByStartedAtDesc(Integer workflowId);
}
