package com.judicialflow.scheduling.repository;

import com.judicialflow.scheduling.model.ProposalStatus;
import com.judicialflow.scheduling.model.SchedulingProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SchedulingProposalRepository extends JpaRepository<SchedulingProposal, UUID> {

    List<SchedulingProposal> findByRunId(UUID runId);

    List<SchedulingProposal> findByRunIdAndStatus(UUID runId, ProposalStatus status);

    List<SchedulingProposal> findByLegalCaseIdAndStatus(UUID caseId, ProposalStatus status);

    @Modifying
    @Query("UPDATE SchedulingProposal p SET p.status = :newStatus WHERE p.legalCase.id = :caseId AND p.status = :oldStatus")
    int updateStatusByCaseIdAndOldStatus(
            @Param("caseId") UUID caseId,
            @Param("oldStatus") ProposalStatus oldStatus,
            @Param("newStatus") ProposalStatus newStatus);

    @Modifying
    @Query("UPDATE SchedulingProposal p SET p.status = :newStatus WHERE p.legalCase.id IN :caseIds AND p.status = :oldStatus")
    int updateStatusByCaseIdsAndOldStatus(
            @Param("caseIds") java.util.Collection<UUID> caseIds,
            @Param("oldStatus") ProposalStatus oldStatus,
            @Param("newStatus") ProposalStatus newStatus);
}
