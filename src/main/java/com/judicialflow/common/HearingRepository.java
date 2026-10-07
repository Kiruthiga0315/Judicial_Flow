package com.judicialflow.common;

import com.judicialflow.common.enums.HearingStatus;
import com.judicialflow.common.models.Hearing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface HearingRepository extends JpaRepository<Hearing, UUID>, JpaSpecificationExecutor<Hearing> {

    @Query("SELECT h FROM Hearing h WHERE h.judge.id = :judgeId AND h.status = :status AND h.scheduledTime >= :from AND h.scheduledTime < :to")
    List<Hearing> findByJudgeAndStatusBetween(@Param("judgeId") UUID judgeId, @Param("status") HearingStatus status,
                                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT h FROM Hearing h WHERE h.courtroom.id = :courtroomId AND h.status = :status AND h.scheduledTime >= :from AND h.scheduledTime < :to")
    List<Hearing> findByCourtroomAndStatusBetween(@Param("courtroomId") UUID courtroomId, @Param("status") HearingStatus status,
                                                  @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT h FROM Hearing h WHERE h.status = :status AND h.scheduledTime >= :from AND h.scheduledTime < :to")
    List<Hearing> findAllByStatusBetween(@Param("status") HearingStatus status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT h FROM Hearing h WHERE h.legalCase.id = :caseId AND h.status = :status ORDER BY h.scheduledTime DESC")
    List<Hearing> findByCaseIdAndStatus(@Param("caseId") UUID caseId, @Param("status") HearingStatus status);

    List<Hearing> findAllByStatus(HearingStatus status);

    @Query("SELECT h FROM Hearing h WHERE h.legalCase.id = :caseId ORDER BY h.scheduledTime DESC")
    List<Hearing> findByCaseIdOrderByScheduledTimeDesc(@Param("caseId") UUID caseId);

    @Query("SELECT h FROM Hearing h WHERE h.status = :status AND (h.judge.id = :judgeId OR h.courtroom.id = :courtroomId) " +
           "AND h.scheduledTime >= :windowStart AND h.scheduledTime <= :windowEnd")
    List<Hearing> findPotentialConflicts(
            @Param("judgeId") UUID judgeId,
            @Param("courtroomId") UUID courtroomId,
            @Param("status") HearingStatus status,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd);
}
