# System Limitations and Empirical Findings

> **MANDATORY DISCLAIMER**: All findings, benchmarks, and metrics reported here are simulation outputs conducted exclusively on synthetic caseload data under stated mathematical assumptions. They are not empirical observations of live court dockets and do not represent evidence of real-world court impact.

---

## 1. Simulation Performance Findings

Simulations were run across multiple offered loads (Moderate: 0.70, Balanced: 1.00, Overloaded: 1.30, Severe: 1.60) over a 90-weekday horizon (~126 calendar days) with 8 Judges, 5 Courtrooms, and an initial backlog of 350 cases.

### What the Engine Achieves (vs. Type-Blind FCFS)
* **Beats Type-Blind FCFS at Every Load**: Across all evaluated traffic intensities, the JudicialFlow weighted priority engine statistically significantly outperforms type-blind FCFS on primary statutory delay:
  * Mean delay for statutory priority cases (BAIL, POCSO, MATRIMONIAL) is reduced substantially:
    * Load 0.70: -1.78 days (95% CI [-2.11, -1.45])
    * Load 1.00: -11.90 days (95% CI [-12.59, -11.20])
    * Load 1.30: -24.65 days (95% CI [-25.96, -23.35])
    * Load 1.60: -36.43 days (95% CI [-38.30, -34.56])
  * Percent of statutory cases delayed past 30 days is dramatically decreased (e.g., -60.8 percentage points at load 1.30).

### What the Engine Does NOT Achieve (vs. Statutory-First Tiered Rule)
* **Does NOT Beat FCFS-Tiered on Statutory Delay**: A rigid, statutory-first tiered rule (where priority cases always precede non-priority cases unconditionally) achieves equal or slightly lower statutory first-hearing delay than the weighted engine:
  * At Load 1.00: Engine is +0.36 days worse than FCFS-Tiered (95% CI [+0.33, +0.40])
  * At Load 1.30: Engine is +0.24 days worse than FCFS-Tiered (95% CI [+0.22, +0.26])
  * At Load 1.60: Engine is +0.26 days worse than FCFS-Tiered (95% CI [+0.22, +0.30])
* **CIVIL Starvation vs CRIMINAL_OTHER Asymmetry**:
  * The engine does NOT prevent non-priority wait inflation versus `FCFS-tiered`. Specifically, engine vs tiered is **drastically worse for CIVIL matters** but **better for CRIMINAL_OTHER matters**:
    * At Load 1.00: CIVIL P90 wait is **+23.73 days worse** (95% CI [+15.38, +32.08]), while CRIMINAL_OTHER P90 wait is **-1.55 days better** (95% CI [-1.91, -1.19]).
    * At Load 1.30: CIVIL P90 wait is **+60.74 days worse** (95% CI [+52.92, +68.56]), while CRIMINAL_OTHER P90 wait is **-3.54 days better** (95% CI [-6.47, -0.61]).
    * At Load 1.60: CIVIL P90 wait is **+58.26 days worse** (95% CI [+55.81, +60.71]), while CRIMINAL_OTHER P90 wait is **-17.90 days better** (95% CI [-19.18, -16.62]).
  * *Root Cause*: In JudicialFlow's scoring formula, brand-new CRIMINAL_OTHER cases enter with score 20.00, continually preempting CIVIL cases (score 10.00) until civil cases age past 100 calendar days. In contrast, `FCFS-tiered` schedules all non-priority matters strictly in FIFO filing order.
* **Aging Cap Boundary vs Statutory Filings**:
  * An unadjourned CIVIL case starts at score 10.00 and accumulates 0.10 points/day, capped at +40.00 points (reached at 400 days).
  * Maximum possible score for an unadjourned civil case is $10.00 + 40.00 = 50.00$.
  * A brand-new BAIL or POCSO filing starts at score 50.00.
  * Consequently, an unadjourned CIVIL case **can NEVER overtake** a freshly filed BAIL or POCSO case purely through aging, regardless of how long it waits.

---

## 2. The Core Value Claim: Configurable, Explainable, Auditable Ranking

JudicialFlow is **not** designed as an unconstrained throughput optimizer. The true systemic value proposition lies in:
1. **Explainable Decision Logic**: Every proposed hearing assignment includes a runner-up comparison, detailing why specific alternative slots were rejected and providing full visibility into constraint satisfaction and soft-penalty breakdowns.
2. **Configurable Policy Weights**: Policy weights (statutory urgency, pendency age, adjournments) are centrally declared and configurable by court administration rather than embedded in ad-hoc, opaque clerk heuristics.
3. **Immutability and Audit Integrity**: Strict database-level role separation ensures application credentials cannot tamper with historical audit trails (`audit_log_entries`), preventing post-hoc justification of schedule tampering.
4. **Human Decision Support**: All schedule runs generate proposals for registrar and judge review; no automatic dispositive changes are forced without explicit oversight or manual override capabilities.

---

## 3. Operational & Algorithmic Limitations

1. **Greedy Heuristic with Local Repair**:
   * The scheduling engine executes a greedy weighted assignment followed by local-search repair. It does not compute a provably global optimum.
2. **Circular Feature Generation in Duration Estimator**:
   * In the synthetic caseload generator (`SyntheticCaseGeneratorController.java`), `priorAdjournments` is generated directly as a function of case duration: $\text{adjournments} = \lfloor \text{duration} / 60 \rfloor + \text{noise}$.
   * When linear regression predicts duration from adjournments, the model merely learns this synthetic formula. The performance over naive baseline is **circular** and must not be cited as validated predictive accuracy.
3. **Feature Leakage for Active Cases**:
   * Estimating an active case's total disposal duration from current adjournment count introduces optimistic bias for early-stage matters.
4. **Unverified NJDG Status (Pending Stock vs Filing Flow)**:
   * NJDG public dashboards aggregate cumulative pending case stock, not incoming filing arrival flow. A pending-stock ratio is not an arrival ratio.
   * Without primary court registry filing microdata, all case-type shares, arrival rates, disposal percentages, and adjournment probabilities are **simulation ASSUMPTIONS**.
5. **Out of Scope Capabilities**:
   * The system does not support real-time WebSocket/SSE streaming, SMS/WhatsApp notifications, cryptographic blockchain evidence-chain hashing, multi-district federation, or production high-availability clustering.

