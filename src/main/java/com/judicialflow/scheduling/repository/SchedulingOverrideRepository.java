package com.judicialflow.scheduling.repository;

import com.judicialflow.scheduling.model.SchedulingOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SchedulingOverrideRepository extends JpaRepository<SchedulingOverride, UUID> {

    List<SchedulingOverride> findByLegalCaseId(UUID caseId);
}
