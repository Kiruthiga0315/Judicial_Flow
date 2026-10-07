package com.judicialflow.common;

import com.judicialflow.common.models.JudgeLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface JudgeLeaveRepository extends JpaRepository<JudgeLeave, UUID> {

    List<JudgeLeave> findByJudgeIdOrderByStartDateAsc(UUID judgeId);

    @Query("SELECT jl FROM JudgeLeave jl WHERE jl.startDate <= :end AND jl.endDate >= :start")
    List<JudgeLeave> findActiveLeavesInRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT jl FROM JudgeLeave jl WHERE jl.judge.id = :judgeId AND jl.startDate <= :end AND jl.endDate >= :start")
    List<JudgeLeave> findByJudgeIdAndDateRange(@Param("judgeId") UUID judgeId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(jl) > 0 FROM JudgeLeave jl WHERE jl.judge.id = :judgeId AND jl.startDate <= :date AND jl.endDate >= :date")
    boolean existsByJudgeIdAndDate(@Param("judgeId") UUID judgeId, @Param("date") LocalDate date);
}
