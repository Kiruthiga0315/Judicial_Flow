# Phase 8: Simulation Methodology & Validation Benchmark

## 1. Purpose & Core Disclaimer

> **MANDATORY DISCLAIMER**:
> Results are simulation outputs on synthetic data under stated assumptions; they are not evidence of real-world court impact. The baseline (FCFS) is a mathematical model of manual scheduling, not empirical court observation.

The purpose of Phase 8 is to provide an empirical, quantifiable comparison of the **JudicialFlow Constraint-Satisfaction Engine** against calibrated **First-Come, First-Served (FCFS)** baselines under identical caseload conditions, using common random numbers (CRN) for outcome isolation.

---

## 2. Model & Architecture

### A. Caseload Model
- **Caseload Source**: Backlog and daily arrivals are synthetically generated and calibrated against approximate National Judicial Data Grid (NJDG) aggregate statistics (`NjdgCalibrationTargets`):
  - BAIL: 10%
  - POCSO: 5%
  - CRIMINAL_OTHER: 60%
  - CIVIL: 20%
  - MATRIMONIAL: 5%
- **Statutory Priority Share**: Statutory urgent matters (BAIL, POCSO, MATRIMONIAL) represent 20.0% of all incoming filings.
- **Isolation via Common Random Numbers (CRN)**:
  - Both arms receive identical initial backlogs and arrival streams, verified by a SHA-256 arrival hash.
  - Every case pre-draws its sequence of hearing outcomes (`ADJOURN` vs `DISPOSE`) based on its `(seed, caseIndex)` identity.
  - Because both arms replay the exact same outcome sequences, observed differences stem solely from the order and timing of hearings scheduled.

### B. Bench Capacity & Clock
- **Simulated Court**: 8 Judges, 5 Courtrooms.
- **Hours & Availability**: Standard weekday court hours (09:00 - 17:00 IST), offering 7 slots per room per day (35 courtroom hearing slots per day) with a 1-hour lunch recess (09:00–12:00 morning session, 13:00–17:00 afternoon session).
- **Total Capacity**: Over the 90-weekday horizon, total court capacity is strictly fixed at $90 \times 35 = 3,150$ slots.
- **Time Stepping**: Simulated weekday clock running on a configurable cadence (default weekly / 5 business days) over a 90-weekday horizon with a pre-existing queue (350 backlog cases).

### C. Arms Compared & Fair Baseline Variants
1. **JudicialFlow Engine**:
   - Calculates priority score dynamically as of the simulated date using `PriorityScoreCalculator`.
   - Schedules cases into feasible courtroom slots using weighted soft-constraint optimization (priority ordering 0.5, workload balancing 0.3, churn minimization 0.2).
   - Enforces all hard constraints via `HardConstraintChecker`.
2. **Fair FCFS Baseline Variants (Mathematical Models of Manual Listing)**:
   - **FCFS-RoundRobin (Headline Fair Baseline)**: Orders cases strictly by filing date ascending, ignoring case type and priority score. Cycles across judges in a deterministic round-robin manner.
   - **FCFS-Tiered (Priority Baseline)**: Schedules statutory priority cases (BAIL, POCSO, MATRIMONIAL) first, ordered strictly by filing date ascending, followed by non-priority matters ordered by filing date ascending. Rotates judges round-robin, ignoring mathematical priority scores.
   - **FCFS-LeastLoaded**: Assigns cases strictly by filing date ascending to the judge with the lowest cumulative hearing count.
   - **FCFS-Naive (Strawman)**: Naive sequential judge scan (always tries Judge 1 first). Documented solely to explain historical judge workload skew.

---

## 3. Stated Assumptions & Load Calibration

### A. Load Calibration Formula
Offered load is explicitly defined as:
$$\text{Offered Load } (L) = \frac{\text{Expected slot demand per day}}{\text{Available slots per day}} = \frac{\lambda \times \mathbb{E}[\text{Hearings}]}{\text{Courtrooms} \times \text{Slots/Room/Day}} = \frac{\lambda \times \frac{1}{1 - p_{\text{adj}}}}{5 \times 7} = \frac{\lambda \times 1.8182}{35} = \frac{\lambda}{19.25}$$

Four calibrated scenarios are evaluated:
1. **Moderate Load (~0.70)**: $\lambda = 13.5$ cases/day ($\text{Offered Load} = 0.70$, Statutory Load = 0.14)
2. **Balanced Load (~1.00)**: $\lambda = 19.25$ cases/day ($\text{Offered Load} = 1.00$, Statutory Load = 0.20)
3. **Overloaded (~1.30)**: $\lambda = 25.0$ cases/day ($\text{Offered Load} = 1.30$, Statutory Load = 0.26)
4. **Severe Overload (~1.60)**: $\lambda = 30.8$ cases/day ($\text{Offered Load} = 1.60$, Statutory Load = 0.32)

*Capacity & Utilization Integrity*:
- Slots/room/day is 7 here (35 slots/day total) to model realistic court sittings with a 1-hour lunch recess (09:00–12:00, 13:00–17:00), whereas the load testing tool used 8 continuous unconstrained 60-min blocks (09:00–17:00 = 40 slots/day).
- Slot utilization is defined as $\text{Utilization} = \frac{\text{Hearings Held}}{\text{Available Slots}}$, which is mathematically bounded by $\le 1.0$. Parity checks enforce that utilization does not exceed 1.0 in either arm.

### B. Core Assumptions
All parameters not directly verified with primary microdata are explicitly labeled **ASSUMPTION**:

1. **ASSUMPTION: Adjournment Probability (45%)**  
   Each held hearing has a 45% probability of concluding with an adjournment rather than final disposal.
2. **ASSUMPTION: Minimum Cooling Gap (7 Days)**  
   When a hearing is adjourned, the case requires a statutory cooling gap of 7 calendar days before becoming eligible for re-scheduling.
3. **ASSUMPTION: Maximum Hearings per Case (5)**  
   Cases are assigned a maximum of 5 hearings in their simulated lifecycle before forced disposal.
4. **ASSUMPTION: FCFS Preserves Filing Date**  
   In the FCFS baseline, an adjourned case preserves its original filing date upon re-entering the scheduling queue.
5. **ASSUMPTION: Availability Windows**  
   All 8 judges and 5 courtrooms are available continuously from 09:00 to 17:00 on weekdays without unscheduled leave or physical facility outages.
6. **ASSUMPTION: Arrival Filing Flow vs Pending Stock**  
   NJDG publicly aggregates cumulative pending case stock, not incoming filing flow. A pending stock ratio is not a filing arrival ratio. In the absence of jurisdiction-level filing microdata, case type arrival shares (BAIL 10%, POCSO 5%, MATRIMONIAL 5%, CIVIL 20%, CRIMINAL_OTHER 60%) are treated as simulation ASSUMPTIONS.

### C. Verified Empirical Source Registry (User Placeholder)
*Record verified empirical sources below when official court filing microdata is obtained:*
| Parameter | Empirically Verified Value | Verified Source URL | Primary Court Registry / Table | Date Accessed |
|---|---|---|---|---|
| Incoming Criminal vs Civil Filing Flow | _[Record value]_ | _[Record URL]_ | _[Record table/registry]_ | _[Record date]_ |
| Statutory Sub-type Filing Split | _[Record value]_ | _[Record URL]_ | _[Record table/registry]_ | _[Record date]_ |
| Empirical Disposal Probability | _[Record value]_ | _[Record URL]_ | _[Record table/registry]_ | _[Record date]_ |
| Adjournment Distribution | _[Record value]_ | _[Record URL]_ | _[Record table/registry]_ | _[Record date]_ |


---

## 4. Metric Definitions & Cohort Isolation

- **Cohorts**:
  - **Arrivals Cohort (Primary Metric)**: Calendar wait from case filing to first hearing for all cases arriving during the 90-day simulation horizon.
  - **Backlog Cohort**: Calendar wait from simulation start date to first hearing for all pre-existing backlog cases.
  - **Right-Censoring**: Any case unheard at the end of the 90-day horizon with waiting age $> T$ (14 days) is explicitly counted as delayed past threshold $T$. Censored counts are reported per cohort to prevent survivor bias.
- **Outcome Labels**:
  - Derived strictly from paired 95% Confidence Intervals:
    - **Improved**: CI strictly excludes 0 in the favorable direction.
    - **Worse**: CI strictly excludes 0 in the unfavorable direction.
    - **No significant difference**: CI spans or includes 0.
- **Pendency & Disposals by Case Type**:
  - `Mean Time to Disposal`: Reported separately for Statutory and Non-Priority disposed matters.
  - `Mean Pending Age`: Reported separately for Statutory and Non-Priority pending cases at horizon end.
- **Trade-offs & Capacity Parity**:
  - `Non-Priority Wait (Arrivals vs Backlog Max Wait)`: Arrivals non-priority max wait is bounded by horizon ($\le 90$ days), while backlog max wait reaches ~180 days due to pre-simulation filing age.
  - `Total Hearings Held` and `Slot Utilization Rate`: Parity checks with explicit tolerances ($\pm 10$ hearings, $\pm 0.05$ utilization).
  - `Judge Workload Standard Deviation`: Workload balance across judges across all variants.

---

## 5. Statistical Rigor & Sensitivity Analysis

- **Multi-Seed Replications**: 10 independent seeds executed across a 90-weekday horizon with a pre-existing queue (350 backlog cases).
- **Paired Differences**: For each seed and metric $M$, the paired difference $\Delta = M_{\text{Engine}} - M_{\text{Baseline}}$ is computed.
- **95% Confidence Intervals**: Derived using Student's $t$-distribution critical values:
  $$\text{CI}_{95\%} = \bar{\Delta} \pm t_{0.025, \, n-1} \cdot \frac{s_{\Delta}}{\sqrt{n}}$$
- **Sensitivity Analysis**: Evaluated on Balanced and Overloaded scenarios across:
  - Adjournment probability: $0.25$, $0.45$, $0.65$
  - Minimum cooling gap: $3$, $7$, $14$ days
  - FCFS re-queue policy: preserve filing date vs re-queued with new date
  - Scheduling cadence: $1$, $5$, $10$ days
  - Each variation reports paired 95% CIs, percentage points (`pp`) for delayed cases, and CI-based outcome labels across 10 seeds.

---

## 6. Limitations

- Does not model lawyer availability conflicts or advocate strikes.
- Does not model unexpected courtroom hardware or infrastructural failure.
- Synthetic caseload arrivals follow an idealized Poisson process calibrated to aggregate statistics rather than jurisdictional micro-patterns.
