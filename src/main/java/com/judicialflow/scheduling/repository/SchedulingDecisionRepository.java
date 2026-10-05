package com.judicialflow.scheduling.repository;

import com.judicialflow.scheduling.model.SchedulingDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchedulingDecisionRepository extends JpaRepository<SchedulingDecision, UUID> {

    Optional<SchedulingDecision> findByProposalId(UUID proposalId);

    List<SchedulingDecision> findByProposalRunId(UUID runId);
}
