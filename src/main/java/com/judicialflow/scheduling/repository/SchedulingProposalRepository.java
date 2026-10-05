package com.judicialflow.scheduling.repository;

import com.judicialflow.scheduling.model.ProposalStatus;
import com.judicialflow.scheduling.model.SchedulingProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SchedulingProposalRepository extends JpaRepository<SchedulingProposal, UUID> {

    List<SchedulingProposal> findByRunId(UUID runId);

    List<SchedulingProposal> findByRunIdAndStatus(UUID runId, ProposalStatus status);
}
