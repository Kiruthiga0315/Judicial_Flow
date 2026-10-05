package com.judicialflow.common;

import com.judicialflow.common.models.Judge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JudgeRepository extends JpaRepository<Judge, UUID>, JpaSpecificationExecutor<Judge> {

    Optional<Judge> findByName(String name);

    boolean existsByName(String name);
}
