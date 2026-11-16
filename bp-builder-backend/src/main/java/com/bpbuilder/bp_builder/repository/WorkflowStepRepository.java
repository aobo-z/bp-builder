package com.bpbuilder.bp_builder.repository;

import com.bpbuilder.bp_builder.entity.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, Integer> {
    List<WorkflowStep> findAllByWorkflowIdOrderBySequenceAsc(Integer workflowId);

    List<WorkflowStep> findAllByWorkflowIdInOrderByWorkflowIdAscSequenceAsc(List<Integer> workflowIds);

    /** Runs immediately, so new steps with the same sequence numbers can be inserted afterwards. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from WorkflowStep step where step.workflowId = :workflowId")
    void deleteAllOfWorkflow(@Param("workflowId") Integer workflowId);
}
