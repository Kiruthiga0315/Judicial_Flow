package com.judicialflow.ingestion.services;

import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.models.Judge;
import com.judicialflow.common.models.JudgeAvailabilityWindow;
import com.judicialflow.ingestion.dto.*;
import com.judicialflow.ingestion.exceptions.ResourceNotFoundException;
import com.judicialflow.ingestion.exceptions.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JudgeService {

    private final JudgeRepository judgeRepository;

    @Transactional
    public JudgeResponse createJudge(CreateJudgeRequest request) {
        log.info("Creating new judge: {}", request.getName());
        validateAvailabilityWindows(request.getAvailabilityWindows());

        Judge judge = Judge.builder()
                .name(request.getName().trim())
                .specialization(request.getSpecialization())
                .availabilityWindows(request.getAvailabilityWindows() != null ? request.getAvailabilityWindows() : List.of())
                .build();

        Judge saved = judgeRepository.save(judge);
        return JudgeResponse.fromEntity(saved);
    }

    @Transactional
    public JudgeResponse updateJudge(UUID id, UpdateJudgeRequest request) {
        log.info("Updating judge with id: {}", id);
        Judge judge = judgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + id));

        judge.setName(request.getName().trim());
        judge.setSpecialization(request.getSpecialization());

        Judge saved = judgeRepository.save(judge);
        return JudgeResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public JudgeResponse getJudgeById(UUID id) {
        Judge judge = judgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + id));
        return JudgeResponse.fromEntity(judge);
    }

    @Transactional(readOnly = true)
    public PageResponse<JudgeResponse> listJudges(Pageable pageable) {
        Page<JudgeResponse> page = judgeRepository.findAll(pageable)
                .map(JudgeResponse::fromEntity);
        return PageResponse.of(page);
    }

    @Transactional
    public JudgeResponse setAvailabilityWindows(UUID id, SetJudgeAvailabilityRequest request) {
        log.info("Setting availability windows for judge id: {}", id);
        Judge judge = judgeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + id));

        validateAvailabilityWindows(request.getAvailabilityWindows());
        judge.setAvailabilityWindows(request.getAvailabilityWindows() != null ? request.getAvailabilityWindows() : List.of());

        Judge saved = judgeRepository.save(judge);
        return JudgeResponse.fromEntity(saved);
    }

    private void validateAvailabilityWindows(List<JudgeAvailabilityWindow> windows) {
        if (windows == null || windows.isEmpty()) return;
        for (JudgeAvailabilityWindow w : windows) {
            if (w.getStartTime() != null && w.getEndTime() != null) {
                if (!w.getStartTime().isBefore(w.getEndTime())) {
                    throw new ValidationException(String.format(
                            "Invalid availability window on %s: startTime (%s) must be before endTime (%s)",
                            w.getDayOfWeek(), w.getStartTime(), w.getEndTime()));
                }
            }
        }
    }
}
