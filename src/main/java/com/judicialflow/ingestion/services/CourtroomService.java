package com.judicialflow.ingestion.services;

import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.models.Courtroom;
import com.judicialflow.common.models.CourtroomAvailabilityWindow;
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
public class CourtroomService {

    private final CourtroomRepository courtroomRepository;

    @Transactional
    public CourtroomResponse createCourtroom(CreateCourtroomRequest request) {
        log.info("Creating courtroom: {}", request.getName());
        if (request.getCapacity() <= 0) {
            throw new ValidationException("Courtroom capacity must be greater than zero");
        }
        validateAvailabilityWindows(request.getAvailability());

        Courtroom courtroom = Courtroom.builder()
                .name(request.getName().trim())
                .capacity(request.getCapacity())
                .availability(request.getAvailability() != null ? request.getAvailability() : List.of())
                .build();

        Courtroom saved = courtroomRepository.save(courtroom);
        return CourtroomResponse.fromEntity(saved);
    }

    @Transactional
    public CourtroomResponse updateCourtroom(UUID id, UpdateCourtroomRequest request) {
        log.info("Updating courtroom id: {}", id);
        Courtroom courtroom = courtroomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courtroom not found with id: " + id));

        if (request.getCapacity() <= 0) {
            throw new ValidationException("Courtroom capacity must be greater than zero");
        }

        courtroom.setName(request.getName().trim());
        courtroom.setCapacity(request.getCapacity());

        Courtroom saved = courtroomRepository.save(courtroom);
        return CourtroomResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public CourtroomResponse getCourtroomById(UUID id) {
        Courtroom courtroom = courtroomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courtroom not found with id: " + id));
        return CourtroomResponse.fromEntity(courtroom);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourtroomResponse> listCourtrooms(Pageable pageable) {
        Page<CourtroomResponse> page = courtroomRepository.findAll(pageable)
                .map(CourtroomResponse::fromEntity);
        return PageResponse.of(page);
    }

    @Transactional
    public CourtroomResponse setCapacityAndAvailability(UUID id, SetCourtroomAvailabilityRequest request) {
        log.info("Setting capacity and availability for courtroom id: {}", id);
        Courtroom courtroom = courtroomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Courtroom not found with id: " + id));

        if (request.getCapacity() != null) {
            if (request.getCapacity() <= 0) {
                throw new ValidationException("Courtroom capacity must be greater than zero");
            }
            courtroom.setCapacity(request.getCapacity());
        }

        if (request.getAvailability() != null) {
            validateAvailabilityWindows(request.getAvailability());
            courtroom.setAvailability(request.getAvailability());
        }

        Courtroom saved = courtroomRepository.save(courtroom);
        return CourtroomResponse.fromEntity(saved);
    }

    private void validateAvailabilityWindows(List<CourtroomAvailabilityWindow> windows) {
        if (windows == null || windows.isEmpty()) return;
        for (CourtroomAvailabilityWindow w : windows) {
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
