package com.judicialflow.security;

import com.judicialflow.common.CaseRepository;
import com.judicialflow.common.CourtroomRepository;
import com.judicialflow.common.HearingRepository;
import com.judicialflow.common.JudgeRepository;
import com.judicialflow.common.enums.CaseStatus;
import com.judicialflow.common.enums.CaseType;
import com.judicialflow.common.enums.HearingStatus;
import com.judicialflow.common.models.Case;
import com.judicialflow.common.models.Courtroom;
import com.judicialflow.common.models.Hearing;
import com.judicialflow.common.models.Judge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Seeds demo users (one per role) and dev entities ONLY in the "dev" profile.
 * Default and production profiles do not execute this seeder.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final JudgeRepository judgeRepository;
    private final CourtroomRepository courtroomRepository;
    private final CaseRepository caseRepository;
    private final HearingRepository hearingRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("Running DevDataSeeder under 'dev' profile...");

        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(User.builder()
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .role(UserRole.ADMIN)
                    .email("admin@judicialflow.org")
                    .enabled(true)
                    .build());
            log.info("Seeded dev user: admin (ADMIN)");
        }

        if (!userRepository.existsByUsername("registrar")) {
            userRepository.save(User.builder()
                    .username("registrar")
                    .passwordHash(passwordEncoder.encode("registrar123"))
                    .role(UserRole.REGISTRAR)
                    .email("registrar@judicialflow.org")
                    .enabled(true)
                    .build());
            log.info("Seeded dev user: registrar (REGISTRAR)");
        }

        // Ensure at least one Judge record exists for the dev judge user
        Judge devJudge = judgeRepository.findAll().stream().findFirst().orElseGet(() -> {
            Judge j = Judge.builder()
                    .name("Hon. Justice Sharma")
                    .specialization("CRIMINAL")
                    .build();
            Judge saved = judgeRepository.save(j);
            log.info("Seeded dev Judge entity: '{}' with id={}", saved.getName(), saved.getId());
            return saved;
        });

        UUID judgeEntityId = devJudge.getId();

        if (!userRepository.existsByUsername("judge")) {
            userRepository.save(User.builder()
                    .username("judge")
                    .passwordHash(passwordEncoder.encode("judge123"))
                    .role(UserRole.JUDGE)
                    .email("judge@judicialflow.org")
                    .judgeId(judgeEntityId)
                    .enabled(true)
                    .build());
            log.info("Seeded dev user: judge (JUDGE) linked to judgeId={}", judgeEntityId);
        } else {
            userRepository.findByUsername("judge").ifPresent(j -> {
                if (j.getJudgeId() == null || !j.getJudgeId().equals(judgeEntityId)) {
                    j.setJudgeId(judgeEntityId);
                    userRepository.save(j);
                    log.info("Updated dev user 'judge' with linked judgeId={}", judgeEntityId);
                }
            });
        }

        // Seed sample courtroom, case, and hearing for the dev judge if none exist
        if (hearingRepository.count() == 0) {
            Courtroom room = courtroomRepository.findAll().stream().findFirst().orElseGet(() -> {
                Courtroom cr = Courtroom.builder()
                        .name("Court Room 1")
                        .capacity(50)
                        .build();
                return courtroomRepository.save(cr);
            });

            Case devCase = caseRepository.findAll().stream().findFirst().orElseGet(() -> {
                Case c = Case.builder()
                        .caseNumber("BAIL/2026/0001")
                        .caseType(CaseType.BAIL)
                        .filingDate(LocalDate.now().minusDays(15))
                        .currentStatus(CaseStatus.SCHEDULED)
                        .assignedJudge(devJudge)
                        .assignedCourtroom(room)
                        .build();
                return caseRepository.save(c);
            });

            Hearing devHearing = Hearing.builder()
                    .legalCase(devCase)
                    .judge(devJudge)
                    .courtroom(room)
                    .scheduledTime(LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0))
                    .estimatedDurationMinutes(60)
                    .status(HearingStatus.SCHEDULED)
                    .createdByEngine(false)
                    .build();
            hearingRepository.save(devHearing);
            log.info("Seeded sample hearing for dev judge: hearingId={} at {}", devHearing.getId(), devHearing.getScheduledTime());
        }
    }
}
