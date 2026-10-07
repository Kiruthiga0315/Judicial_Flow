package com.judicialflow.ingestion.services;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
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
    private final com.judicialflow.audit.AuditService auditService;
    private final com.judicialflow.priority.repository.PriorityScoreRepository priorityScoreRepository;

    private java.util.Map<String, Object> caseToState(Case c) {
        if (c == null) return null;
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", c.getId().toString());
        map.put("caseNumber", c.getCaseNumber());
        map.put("caseType", c.getCaseType() != null ? c.getCaseType().name() : null);
        map.put("filingDate", c.getFilingDate() != null ? c.getFilingDate().toString() : null);
        map.put("currentStatus", c.getCurrentStatus() != null ? c.getCurrentStatus().name() : null);
        map.put("priorAdjournments", c.getPriorAdjournments());
        map.put("statutoryDeadline", c.getStatutoryDeadline() != null ? c.getStatutoryDeadline().toString() : null);
        map.put("assignedJudgeId", c.getAssignedJudge() != null ? c.getAssignedJudge().getId().toString() : null);
        map.put("assignedCourtroomId", c.getAssignedCourtroom() != null ? c.getAssignedCourtroom().getId().toString() : null);
        map.put("litigantContactEmail", c.getLitigantContactEmail());
        map.put("deleted", c.isDeleted());
        return map;
    }

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

        CaseStatus status = request.getCurrentStatus() != null ? request.getCurrentStatus() : CaseStatus.FILED;

        Case newCase = Case.builder()
                .caseNumber(request.getCaseNumber().trim())
                .caseType(request.getCaseType())
                .filingDate(request.getFilingDate())
                .currentStatus(status)
                .priorAdjournments(request.getPriorAdjournments())
                .linkedCase(linkedCase)
                .assignedJudge(assignedJudge)
                .statutoryDeadline(request.getStatutoryDeadline())
                .litigantContactEmail(request.getLitigantContactEmail() != null ? request.getLitigantContactEmail().trim() : null)
                .deleted(false)
                .build();

        Case saved = caseRepository.save(newCase);
        auditService.log("Case", saved.getId().toString(), "CREATE", "CASE_CREATED",
                null, caseToState(saved), "Created case " + saved.getCaseNumber());
        return CaseResponse.fromEntity(saved);
    }

    @Transactional
    public CaseResponse updateCase(UUID id, UpdateCaseRequest request) {
        log.info("Updating case with id: {}", id);

        Case existing = caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found with id: " + id));

        java.util.Map<String, Object> beforeState = caseToState(existing);

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
        existing.setStatutoryDeadline(request.getStatutoryDeadline());
        existing.setLitigantContactEmail(request.getLitigantContactEmail() != null ? request.getLitigantContactEmail().trim() : null);

        Case saved = caseRepository.save(existing);
        auditService.log("Case", saved.getId().toString(), "UPDATE", "CASE_UPDATED",
                beforeState, caseToState(saved), "Updated case " + saved.getCaseNumber());
        return CaseResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCaseById(UUID id, boolean includeDeleted) {
        Case c = includeDeleted
                ? caseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Case not found with id: " + id))
                : caseRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> new ResourceNotFoundException("Case not found with id: " + id));
        Double score = priorityScoreRepository.findLatestByCaseId(c.getId())
                .map(ps -> ps.getTotalScore().doubleValue())
                .orElse(null);
        return CaseResponse.fromEntity(c, score);
    }

    @Transactional(readOnly = true)
    public PageResponse<CaseResponse> listCases(CaseFilterCriteria criteria, Pageable pageable) {
        Specification<Case> spec = createSpecification(criteria);
        Page<CaseResponse> page = caseRepository.findAll(spec, pageable)
                .map(c -> {
                    Double score = priorityScoreRepository.findLatestByCaseId(c.getId())
                            .map(ps -> ps.getTotalScore().doubleValue())
                            .orElse(null);
                    return CaseResponse.fromEntity(c, score);
                });
        return PageResponse.of(page);
    }

    @Transactional(readOnly = true)
    public List<com.judicialflow.ingestion.dto.AgingReportItem> getAgingReport(CaseType caseType, Integer limit) {
        int maxResults = (limit != null && limit > 0) ? limit : 100;
        List<Case> cases = caseRepository.findAll().stream()
                .filter(c -> !c.isDeleted() && c.getCurrentStatus() != CaseStatus.DISPOSED)
                .filter(c -> caseType == null || c.getCaseType() == caseType)
                .toList();

        LocalDate today = LocalDate.now();
        List<com.judicialflow.ingestion.dto.AgingReportItem> report = cases.stream()
                .map(c -> {
                    Double score = priorityScoreRepository.findLatestByCaseId(c.getId())
                            .map(ps -> ps.getTotalScore().doubleValue())
                            .orElse(0.0);
                    long daysPending = java.time.temporal.ChronoUnit.DAYS.between(c.getFilingDate(), today);
                    Long daysToDeadline = c.getStatutoryDeadline() != null
                            ? java.time.temporal.ChronoUnit.DAYS.between(today, c.getStatutoryDeadline())
                            : null;

                    return com.judicialflow.ingestion.dto.AgingReportItem.builder()
                            .caseId(c.getId())
                            .caseNumber(c.getCaseNumber())
                            .caseType(c.getCaseType())
                            .status(c.getCurrentStatus())
                            .filingDate(c.getFilingDate())
                            .daysPending(daysPending)
                            .adjournments(c.getPriorAdjournments())
                            .statutoryDeadline(c.getStatutoryDeadline())
                            .daysToDeadline(daysToDeadline)
                            .priorityScore(score)
                            .assignedJudgeName(c.getAssignedJudge() != null ? c.getAssignedJudge().getName() : null)
                            .assignedCourtroomName(c.getAssignedCourtroom() != null ? c.getAssignedCourtroom().getName() : null)
                            .build();
                })
                .sorted((a, b) -> {
                    double scoreA = a.getPriorityScore() != null ? a.getPriorityScore() : 0.0;
                    double scoreB = b.getPriorityScore() != null ? b.getPriorityScore() : 0.0;
                    int scoreCmp = Double.compare(scoreB, scoreA);
                    if (scoreCmp != 0) return scoreCmp;

                    int daysCmp = Long.compare(b.getDaysPending(), a.getDaysPending());
                    if (daysCmp != 0) return daysCmp;

                    int adjCmp = Integer.compare(b.getAdjournments(), a.getAdjournments());
                    if (adjCmp != 0) return adjCmp;

                    String numA = a.getCaseNumber() != null ? a.getCaseNumber() : "";
                    String numB = b.getCaseNumber() != null ? b.getCaseNumber() : "";
                    return numA.compareTo(numB);
                })
                .limit(maxResults)
                .toList();

        return report;
    }

    @Transactional
    public void softDeleteCase(UUID id) {
        log.info("Soft-deleting case with id: {}", id);
        Case existing = caseRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found with id: " + id));

        java.util.Map<String, Object> beforeState = caseToState(existing);

        existing.setDeleted(true);
        existing.setDeletedAt(LocalDateTime.now());
        Case saved = caseRepository.save(existing);

        auditService.log("Case", saved.getId().toString(), "DELETE", "CASE_DELETED",
                beforeState, caseToState(saved), "Soft-deleted case " + saved.getCaseNumber());
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

            if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
                String pattern = "%" + criteria.getSearch().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("caseNumber")), pattern));
            }

            if (criteria.getCaseNumber() != null && !criteria.getCaseNumber().isBlank()) {
                String pattern = "%" + criteria.getCaseNumber().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("caseNumber")), pattern));
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
