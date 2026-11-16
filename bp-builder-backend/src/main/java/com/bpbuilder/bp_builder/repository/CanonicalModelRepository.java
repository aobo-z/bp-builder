package com.bpbuilder.bp_builder.repository;

import com.bpbuilder.bp_builder.entity.CanonicalModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CanonicalModelRepository extends JpaRepository<CanonicalModel, Integer> {
    Optional<CanonicalModel> findFirstByActiveTrueOrderByIdAsc();
}
