package com.judicialflow.common;

import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaseRepository extends JpaRepository<Case, UUID>, JpaSpecificationExecutor<Case> {

    boolean existsByCaseNumber(String caseNumber);

    boolean existsByCaseNumberAndIdNot(String caseNumber, UUID id);
    
    boolean existsByCaseTypeAndFilingDateAndLitigantContactEmail(CaseType caseType, LocalDate filingDate, String litigantContactEmail);

    @Query(value = "SELECT nextval('case_number_seq')", nativeQuery = true)
    Long getNextCaseNumberSequence();

    Optional<Case> findByIdAndDeletedFalse(UUID id);

    Optional<Case> findByCaseNumber(String caseNumber);
}
