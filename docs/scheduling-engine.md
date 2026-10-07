# Scheduling Engine Algorithm & Constraints

## Algorithm Overview
The JudicialFlow scheduling engine relies on a **Greedy Weighted Assignment with Local-Search Repair** (Hill Climbing) algorithm. 
- **Why this choice?** Trial court scheduling requires producing valid initial assignments extremely fast with priority-first guarantees. A greedy pass sorting by priority score ensures that the most urgent cases (bail, POCSO) take the earliest valid slots. Simulated Annealing is overkill for the complexity of constraints here; a deterministic hill-climbing local-search phase is chosen because it allows for fast, bounded optimization of soft constraints (like reducing schedule churn and balancing workload) by swapping neighbors without risking hard constraint violations, ensuring explainability.

## Constraints

### Hard Constraints
Hard constraints are absolute rules that, if violated, make a case unschedulable for a given slot.
1. **Judge Availability**: Judges are only assigned cases during their specified availability windows (e.g., standard business hours).
2. **Courtroom Availability**: Courtrooms must be open and available for the time slot.
3. **No Judge Double-Booking**: A judge cannot handle more than one hearing concurrently.
4. **No Courtroom Double-Booking**: A courtroom can only host one hearing concurrently (capacity = 1 hearing).
5. **Linked Case Sequencing**: A case linked to a prerequisite case must be scheduled at a time strictly equal to or after the prerequisite case's hearing.

### Soft Constraints (Objective Function)
Soft constraints determine the "penalty" or "cost" of a valid slot. The objective function minimizes this total weighted soft cost: `Cost = W1*P1 + W2*P2 + W3*P3`.
1. **Priority Ordering (Weight: 0.5)**: High-priority cases should get earlier time slots in the horizon. Penalty scales based on slot lateness relative to the horizon start. Note: Adjournment history factors strictly into priority score computation (as a boost), which in turn feeds into this penalty to pull frequently-adjourned cases earlier.
2. **Workload Balance (Weight: 0.3)**: Distributes cases evenly across judges. Penalty is assigned when a judge exceeds the current average caseload.
3. **Schedule Churn (Weight: 0.2)**: Penalizes changes to assignments from a previous scheduling run, minimizing disruptive reassignments.

## Objective Function
The engine returns and stores the `totalWeightedSoftCost`, which is the sum of penalties across all scheduled assignments, broken down by constraint type (priority, workload, churn) for tracking algorithmic improvements.

## Reproducibility & Seed Behavior
The algorithm guarantees determinism. For a given input state (cases, judges, courtrooms, prior assignments) and a given random `seed`:
- The greedy pass behaves identically (sorted strictly by score).
- The local-search phase uses the `seed` for its pseudo-random sequence of assignment selections and slot candidate evaluations.
- Output will always be identical across identical inputs + seeds.

## Known Limitations
- The greedy pass processes sequentially, so a low-priority case with large duration may be blocked if high-priority cases fragment the schedule (bin-packing fragmentation).
- Local search is currently basic hill-climbing and prone to getting stuck in local minima, unlike simulated annealing which allows temporary cost increases to escape them.
- Time blocks are fixed (e.g., 30 min intervals), limiting packing efficiency for very short 5-minute administrative hearings.

## Case Status Lifecycle & Master Brief Alignment
The system aligns case status values strictly with the JudicialFlow master brief:
- **`FILED`**: Case newly ingested and awaiting hearing assignment.
- **`PENDING`**: Historical synonym for `FILED` maintained for backward compatibility.
- **`SCHEDULED`**: Proposed hearing has been approved/committed to a judge and courtroom slot.
- **`HEARD`**: Hearing was conducted by the assigned judge.
- **`ADJOURNED`**: Hearing concluded with an adjournment, bumping prior adjournments count and returning the case to the eligible scheduling pool.
- **`DISPOSED`**: Case has concluded (judgment delivered or dismissed), completely excluded from scheduling.

Eligible cases for the scheduling engine are filtered using `CaseStatus.isSchedulable()` which includes `FILED`, `PENDING`, and `ADJOURNED`.

## Concurrency Guard Architecture
The scheduling engine and batch rescheduling pipeline are protected by `SchedulingConcurrencyGuard`:
- **Current Single-Instance Model**: Employs an in-memory `ReentrantLock` with volatile tracking of the active run ID to avoid inter-thread races with sub-millisecond overhead. This is augmented with a database-level query for active `RUNNING` rows in `scheduling_runs` and a 15-minute stale-run watchdog that cleans up after ungraceful server terminations.
- **Distributed / Multi-Instance Migration Path**: For horizontal scaling across multiple container replicas, the guard is designed to transition directly to PostgreSQL advisory locking (`SELECT pg_try_advisory_lock(hashtext('judicialflow_scheduling_guard'))`) or ShedLock, ensuring cluster-wide transactional mutual exclusion directly inside PostgreSQL.
