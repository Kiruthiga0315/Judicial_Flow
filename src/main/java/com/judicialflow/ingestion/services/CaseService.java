package com.judicialflow.ingestion.services;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.Hearing;
import com.judicialflow.common.models.Judge;
import com.judicialflow.ingestion.dto.*;
import com.judicialflow.ingestion.exceptions.CircularReferenceException;
import com.judicialflow.ingestion.exceptions.ConflictException;
import com.judicialflow.ingestion.exceptions.ResourceNotFoundException;
import com.judicialflow.ingestion.exceptions.ValidationException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseService {

    private final CaseRepository caseRepository;
    private final JudgeRepository judgeRepository;

    @Transactional
    public CaseResponse createCase(CreateCaseRequest request) {
        log.info("Creating case with number: {}", request.getCaseNumber());

        if (request.getFilingDate().isAfter(LocalDate.now())) {
            throw new ValidationException("Filing date cannot be in the future");
        }

        if (caseRepository.existsByCaseNumber(request.getCaseNumber().trim())) {
            throw new ConflictException("Case with number '" + request.getCaseNumber().trim() + "' already exists");
        }

        Case linkedCase = null;
        if (request.getLinkedCaseId() != null) {
            linkedCase = caseRepository.findById(request.getLinkedCaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Linked case not found with id: " + request.getLinkedCaseId()));
        }

        Judge assignedJudge = null;
        if (request.getAssignedJudgeId() != null) {
            assignedJudge = judgeRepository.findById(request.getAssignedJudgeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + request.getAssignedJudgeId()));
        }

        CaseStatus status = request.getCurrentStatus() != null ? request.getCurrentStatus() : CaseStatus.PENDING;

        Case newCase = Case.builder()
                .caseNumber(request.getCaseNumber().trim())
                .caseType(request.getCaseType())
                .filingDate(request.getFilingDate())
                .currentStatus(status)
                .priorAdjournments(request.getPriorAdjournments())
                .linkedCase(linkedCase)
                .assignedJudge(assignedJudge)
                .deleted(false)
                .build();

        Case saved = caseRepository.save(newCase);
        return CaseResponse.fromEntity(saved);
    }

    @Transactional
    public CaseResponse updateCase(UUID id, UpdateCaseRequest request) {
        log.info("Updating case with id: {}", id);

        Case existing = caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found with id: " + id));

        if (request.getFilingDate().isAfter(LocalDate.now())) {
            throw new ValidationException("Filing date cannot be in the future");
        }

        if (caseRepository.existsByCaseNumberAndIdNot(request.getCaseNumber().trim(), id)) {
            throw new ConflictException("Case with number '" + request.getCaseNumber().trim() + "' already exists");
        }

        Case linkedCase = null;
        if (request.getLinkedCaseId() != null) {
            validateNoCircularReference(id, request.getLinkedCaseId());
            linkedCase = caseRepository.findById(request.getLinkedCaseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Linked case not found with id: " + request.getLinkedCaseId()));
        }

        Judge assignedJudge = null;
        if (request.getAssignedJudgeId() != null) {
            assignedJudge = judgeRepository.findById(request.getAssignedJudgeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Judge not found with id: " + request.getAssignedJudgeId()));
        }

        existing.setCaseNumber(request.getCaseNumber().trim());
        existing.setCaseType(request.getCaseType());
        existing.setFilingDate(request.getFilingDate());
        existing.setCurrentStatus(request.getCurrentStatus());
        existing.setPriorAdjournments(request.getPriorAdjournments());
        existing.setLinkedCase(linkedCase);
        existing.setAssignedJudge(assignedJudge);

        Case saved = caseRepository.save(existing);
        return CaseResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCaseById(UUID id, boolean includeDeleted) {
        Case c = includeDeleted
                ? caseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Case not found with id: " + id))
                : caseRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Case not found with id: " + id));
        return CaseResponse.fromEntity(c);
    }

    @Transactional(readOnly = true)
    public PageResponse<CaseResponse> listCases(CaseFilterCriteria criteria, Pageable pageable) {
        Specification<Case> spec = createSpecification(criteria);
        Page<CaseResponse> page = caseRepository.findAll(spec, pageable)
                .map(CaseResponse::fromEntity);
        return PageResponse.of(page);
    }

    @Transactional
    public void softDeleteCase(UUID id) {
        log.info("Soft-deleting case with id: {}", id);
        Case existing = caseRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found with id: " + id));

        existing.setDeleted(true);
        existing.setDeletedAt(LocalDateTime.now());
        caseRepository.save(existing);
    }

    private void validateNoCircularReference(UUID caseId, UUID proposedLinkedCaseId) {
        if (caseId.equals(proposedLinkedCaseId)) {
            throw new CircularReferenceException("Circular linked-case reference detected: a case cannot link to itself.");
        }

        Set<UUID> visited = new HashSet<>();
        visited.add(caseId);

        UUID currentId = proposedLinkedCaseId;
        while (currentId != null) {
            if (!visited.add(currentId)) {
                throw new CircularReferenceException(String.format(
                        "Circular linked-case reference detected: linking case %s to %s creates a cycle in dependency chain.",
                        caseId, proposedLinkedCaseId));
            }
            Case parent = caseRepository.findById(currentId).orElse(null);
            currentId = (parent != null && parent.getLinkedCase() != null) ? parent.getLinkedCase().getId() : null;
        }
    }

    private Specification<Case> createSpecification(CaseFilterCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!criteria.isIncludeDeleted()) {
                predicates.add(cb.isFalse(root.get("deleted")));
            }

            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("currentStatus"), criteria.getStatus()));
            }

            if (criteria.getCaseType() != null) {
                predicates.add(cb.equal(root.get("caseType"), criteria.getCaseType()));
            }

            if (criteria.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("filingDate"), criteria.getStartDate()));
            }

            if (criteria.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("filingDate"), criteria.getEndDate()));
            }

            if (criteria.getJudgeId() != null) {
                Predicate assignedJudgePredicate = cb.equal(root.get("assignedJudge").get("id"), criteria.getJudgeId());

                Subquery<UUID> hearingSubquery = query.subquery(UUID.class);
                Root<Hearing> hearingRoot = hearingSubquery.from(Hearing.class);
                hearingSubquery.select(hearingRoot.get("legalCase").get("id"))
                        .where(cb.and(
                                cb.equal(hearingRoot.get("legalCase").get("id"), root.get("id")),
                                cb.equal(hearingRoot.get("judge").get("id"), criteria.getJudgeId())
                        ));

                predicates.add(cb.or(assignedJudgePredicate, cb.exists(hearingSubquery)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
