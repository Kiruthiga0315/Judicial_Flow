package com.judicialflow.duration.repository;

import com.judicialflow.duration.model.DurationEstimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DurationEstimateRepository extends JpaRepository<DurationEstimate, UUID> {
    Optional<DurationEstimate> findByCourtCaseId(UUID caseId);
}
