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
import java.util.List;
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

        // Helper availability window generators
        List<java.time.DayOfWeek> workDays = List.of(
                java.time.DayOfWeek.MONDAY,
                java.time.DayOfWeek.TUESDAY,
                java.time.DayOfWeek.WEDNESDAY,
                java.time.DayOfWeek.THURSDAY,
                java.time.DayOfWeek.FRIDAY
        );

        java.util.function.Supplier<List<com.judicialflow.common.models.JudgeAvailabilityWindow>> judgeWindows = () ->
                workDays.stream().map(d -> com.judicialflow.common.models.JudgeAvailabilityWindow.builder()
                        .dayOfWeek(d)
                        .startTime(java.time.LocalTime.of(9, 0))
                        .endTime(java.time.LocalTime.of(17, 0))
                        .build()).toList();

        java.util.function.Supplier<List<com.judicialflow.common.models.CourtroomAvailabilityWindow>> courtroomWindows = () ->
                workDays.stream().map(d -> com.judicialflow.common.models.CourtroomAvailabilityWindow.builder()
                        .dayOfWeek(d)
                        .startTime(java.time.LocalTime.of(9, 0))
                        .endTime(java.time.LocalTime.of(17, 0))
                        .build()).toList();

        // 1. Ensure existing judges have availability populated
        for (Judge j : judgeRepository.findAll()) {
            if (j.getAvailabilityWindows() == null || j.getAvailabilityWindows().isEmpty()) {
                j.setAvailabilityWindows(judgeWindows.get());
                judgeRepository.save(j);
                log.info("Populated default availability windows for Judge: {}", j.getName());
            }
        }

        // 2. Ensure existing courtrooms have availability populated
        for (Courtroom cr : courtroomRepository.findAll()) {
            if (cr.getAvailability() == null || cr.getAvailability().isEmpty()) {
                cr.setAvailability(courtroomWindows.get());
                courtroomRepository.save(cr);
                log.info("Populated default availability windows for Courtroom: {}", cr.getName());
            }
        }

        // 3. Ensure at least 5 specialized judges exist for proper bench capacity
        if (judgeRepository.count() < 5) {
            List<String[]> devJudges = List.of(
                    new String[]{"Hon. Justice Sharma", "CRIMINAL"},
                    new String[]{"Hon. Justice Mukherjee", "CIVIL"},
                    new String[]{"Hon. Justice Iyer", "BAIL"},
                    new String[]{"Hon. Justice Reddy", "POCSO"},
                    new String[]{"Hon. Justice Patel", "MATRIMONIAL"}
            );
            for (String[] jData : devJudges) {
                if (judgeRepository.findAll().stream().noneMatch(j -> j.getName().equalsIgnoreCase(jData[0]))) {
                    Judge j = judgeRepository.save(Judge.builder()
                            .name(jData[0])
                            .specialization(jData[1])
                            .availabilityWindows(judgeWindows.get())
                            .build());
                    log.info("Seeded bench Judge: '{}' ({}) with id={}", j.getName(), j.getSpecialization(), j.getId());
                }
            }
        }

        // 4. Ensure at least 4 courtrooms exist for concurrent hearing capacity
        if (courtroomRepository.count() < 4) {
            List<String> roomNames = List.of("Court Room 1", "Court Room 2", "Court Room 3", "Court Room 4");
            for (String roomName : roomNames) {
                if (courtroomRepository.findAll().stream().noneMatch(r -> r.getName().equalsIgnoreCase(roomName))) {
                    Courtroom cr = courtroomRepository.save(Courtroom.builder()
                            .name(roomName)
                            .capacity(60)
                            .availability(courtroomWindows.get())
                            .build());
                    log.info("Seeded Courtroom: '{}' with id={}", cr.getName(), cr.getId());
                }
            }
        }

        // Ensure dev judge user is linked to first judge
        Judge devJudge = judgeRepository.findAll().stream().findFirst().orElseThrow();
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
            Courtroom room = courtroomRepository.findAll().stream().findFirst().orElseThrow();

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
