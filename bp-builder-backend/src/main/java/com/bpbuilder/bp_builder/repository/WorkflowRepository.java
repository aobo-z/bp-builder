package com.bpbuilder.bp_builder.repository;

import com.bpbuilder.bp_builder.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRepository extends JpaRepository<Workflow, Integer> {
    List<Workflow> findAllByOrderByUpdatedAtDesc();
}
