package com.bpbuilder.bp_builder.repository;

import com.bpbuilder.bp_builder.entity.Api;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiRepository extends JpaRepository<Api, Integer> {
    // apis.name has a UNIQUE constraint, so this is guaranteed to resolve at
    // most one row.
    Optional<Api> findByName(String name);
}
