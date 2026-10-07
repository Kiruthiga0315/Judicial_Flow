package com.judicialflow;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.Judge;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class DatabaseIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("judicialflow_test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Force flyway to use testcontainers
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        // Turn off h2 overrides if they exist in active profile
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private JudgeRepository judgeRepository;

    @Test
    void testPostgresSchemaAndConstraints() {
        // 1. Save and read a Case with a Judge
        Judge judge = new Judge();
        judge.setName("Hon. Integration Test");
        Judge savedJudge = judgeRepository.save(judge);

        Case legalCase = Case.builder()
                .caseNumber("TEST-DB-001")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now())
                .currentStatus(CaseStatus.FILED)
                .assignedJudge(savedJudge)
                .build();
        
        Case savedCase = caseRepository.save(legalCase);
        
        assertNotNull(savedCase.getId());
        assertEquals("TEST-DB-001", savedCase.getCaseNumber());
        assertEquals(savedJudge.getId(), savedCase.getAssignedJudge().getId());

        // 2. Verify unique case_number constraint
        Case duplicateCase = Case.builder()
                .caseNumber("TEST-DB-001") // Duplicate!
                .caseType(CaseType.BAIL)
                .filingDate(LocalDate.now())
                .currentStatus(CaseStatus.FILED)
                .build();
        
        assertThrows(DataIntegrityViolationException.class, () -> {
            caseRepository.saveAndFlush(duplicateCase);
        });

        // 3. Verify foreign-key constraint (assigned_judge_id)
        Judge fakeJudge = new Judge();
        fakeJudge.setId(UUID.randomUUID()); // Does not exist in DB

        Case invalidCase = Case.builder()
                .caseNumber("TEST-DB-002")
                .caseType(CaseType.CIVIL)
                .filingDate(LocalDate.now())
                .currentStatus(CaseStatus.FILED)
                .assignedJudge(fakeJudge)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            caseRepository.saveAndFlush(invalidCase);
        });
    }
}
