package com.judicialflow.scheduling.repository;

import com.judicialflow.scheduling.model.RunStatus;
import com.judicialflow.scheduling.model.SchedulingRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchedulingRunRepository extends JpaRepository<SchedulingRun, UUID> {

    Optional<SchedulingRun> findTopByStatusOrderByTriggeredAtDesc(RunStatus status);

    List<SchedulingRun> findTop2ByStatusOrderByTriggeredAtDesc(RunStatus status);
}
