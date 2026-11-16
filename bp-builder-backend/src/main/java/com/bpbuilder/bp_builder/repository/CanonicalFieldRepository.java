package com.bpbuilder.bp_builder.repository;

import com.bpbuilder.bp_builder.entity.CanonicalField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CanonicalFieldRepository extends JpaRepository<CanonicalField, Integer> {
    List<CanonicalField> findAllByCanonicalModelIdOrderByIdAsc(Integer canonicalModelId);
}
