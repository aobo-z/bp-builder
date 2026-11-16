package com.bpbuilder.bp_builder.repository;

import com.bpbuilder.bp_builder.entity.OperationFieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationFieldMappingRepository extends JpaRepository<OperationFieldMapping, Integer> {
    List<OperationFieldMapping> findAllByOperationIdOrderByPriorityAscIdAsc(Integer operationId);
    List<OperationFieldMapping> findAllByReviewStatusOrderByOperationIdAscPriorityAscIdAsc(String reviewStatus);
}
