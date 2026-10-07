package com.judicialflow.simulation.validation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "simulation")
public class SimulationConfig {
    private boolean enabled = true;
    private int horizonDays = 90;
    private int cadenceDays = 5;
    private int judgesCount = 8;
    private int courtroomsCount = 5;
    private int slotsPerDayPerRoom = 7;
    private int defaultDurationMinutes = 60;
    private int thresholdTDays = 14;
    private List<Integer> sensitivityThresholds = List.of(7, 14, 30);
    private int backlogSize = 350;
    private List<Long> defaultSeeds = List.of(
            42L, 101L, 203L, 305L, 407L, 509L, 611L, 713L, 815L, 917L
    );
    private List<Long> quickSeeds = List.of(42L, 101L, 203L);
    private Map<String, ScenarioConfig> scenarios = new LinkedHashMap<>();
    private Assumptions assumptions = new Assumptions();

    @Data
    public static class ScenarioConfig {
        private String name;
        private double dailyArrivalRate;
        private double capacityMultiplier = 1.0;
        private double targetOfferedLoad;
    }

    @Data
    public static class Assumptions {
        // ASSUMPTION: Adjournment probability per scheduled hearing
        private double adjournmentProbability = 0.45;
        // ASSUMPTION: Minimum days before adjourned case is eligible for next hearing
        private int minimumAdjournmentGapDays = 7;
        // ASSUMPTION: Maximum allowable hearings before disposal
        private int maxHearingsPerCase = 5;
        // ASSUMPTION: In FCFS, adjourned cases retain original filing date (vs re-queued with new date)
        private boolean fcfsPreservesFilingDate = true;
    }
}
