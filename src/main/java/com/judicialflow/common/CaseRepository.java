package com.judicialflow.common;

import com.judicialflow.common.models.Case;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaseRepository extends JpaRepository<Case, UUID>, JpaSpecificationExecutor<Case> {

    boolean existsByCaseNumber(String caseNumber);

    boolean existsByCaseNumberAndIdNot(String caseNumber, UUID id);

    Optional<Case> findByIdAndDeletedFalse(UUID id);

    Optional<Case> findByCaseNumber(String caseNumber);
}
