package com.bpbuilder.bp_builder.repository;

import com.bpbuilder.bp_builder.entity.ApiOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiOperationRepository extends JpaRepository<ApiOperation, Integer> {
    // api_operations has a UNIQUE(api_id, method, path) constraint.
    Optional<ApiOperation> findByApiIdAndMethodAndPath(Integer apiId, String method, String path);
}
