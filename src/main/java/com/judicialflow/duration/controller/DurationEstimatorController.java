package com.judicialflow.duration.controller;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.models.Case;
import com.judicialflow.duration.dto.DurationEstimateResponse;
import com.judicialflow.duration.service.DurationEstimatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.judicialflow.ingestion.exceptions.ResourceNotFoundException;

@RestController
@RequestMapping("/api/v1/estimates")
@RequiredArgsConstructor
public class DurationEstimatorController {

    private final DurationEstimatorService estimatorService;
    private final CaseRepository caseRepository;

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<DurationEstimateResponse> getEstimateForCase(@PathVariable UUID caseId) {
        Case courtCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found"));
        
        DurationEstimateResponse response = estimatorService.estimateForCase(courtCase);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/train")
    public ResponseEntity<String> forceRetrain() {
        estimatorService.trainModels();
        return ResponseEntity.ok("Models retrained successfully");
    }
}
