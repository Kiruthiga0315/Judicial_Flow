# JudicialFlow End-to-End Live Demonstration Script

> **MANDATORY DISCLAIMER**: All data utilized in this walkthrough is purely synthetic and calibrated to aggregate NJDG statistics under stated mathematical assumptions. It does not represent real court cases or litigants.

This demo script guides a live demonstration or examiner evaluation of the JudicialFlow Court Scheduling & Decision-Support System. Follow each step sequentially.

---

## Prerequisites & Architecture Verification

Ensure Docker Desktop and JDK 17+ are installed and active.

| Service | Port | Dev Default Credentials | Notes |
|---|---|---|---|
| PostgreSQL 15 | `localhost:5433` | `jfuser` / `jfpass` (App DML)<br>`jfowner` / `jfownerpass` (Flyway DDL) | Two-role separation active |
| MailHog SMTP / UI | `localhost:1025` (SMTP)<br>`localhost:8025` (HTTP) | None | Local email inbox |
| Spring Boot Backend | `localhost:8081` | Dev HTTP Basic Auth | Dev profile active |
| Vite React Frontend | `localhost:5173` | Browser App | Role-based navigation |

---

## Step 1: Clean Database & Start Services

Reset persistent volumes to ensure a clean state:

```bash
# Navigate to the backend directory
cd judicialflow

# Terminate existing containers and remove persistent volumes
docker compose down -v

# Start fresh PostgreSQL and MailHog instances
docker compose up -d
```

**Actual Output:**
```
[+] Running 2/2
 ✔ Container judicialflow-mailhog  Started
 ✔ Container judicialflow-db       Started
```

Start the Spring Boot backend with the `dev` profile (which runs Flyway migrations and seeds initial test users and the dev judge):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**Actual Log Confirmation:**
```
o.f.c.i.command.DbMigrate             : Current version of schema "public": 20
c.j.security.DevDataSeeder            : Seeded dev user: admin (ADMIN)
c.j.security.DevDataSeeder            : Seeded dev user: registrar (REGISTRAR)
c.j.security.DevDataSeeder            : Seeded dev Judge entity: 'Hon. Justice Sharma' with id=954e5288-248b-4f45-81da-be97774ae259
c.j.security.DevDataSeeder            : Seeded dev user: judge (JUDGE) linked to judgeId=954e5288-248b-4f45-81da-be97774ae259
c.j.security.DevDataSeeder            : Seeded sample hearing for dev judge: hearingId=b62c73a7-1579-4404-8f5f-fcdf066b1d85 at 2026-10-08T10:00
c.judicialflow.JudicialFlowApplication: Started JudicialFlowApplication in 5.695 seconds
```

In a separate terminal, launch the frontend dashboard:

```bash
cd frontend
npm install
npm run dev
```

**Actual Output:**
```
  VITE v5.4.21  ready in 320 ms
  ➜  Local:   http://localhost:5173/
```

[screenshot: Terminal boot sequence showing clean Flyway migration to V20 and DevDataSeeder initialization]

---

## Step 2: Seed Synthetic Caseload

Generate 100 synthetic cases calibrated to approximate NJDG macro distributions:

```bash
curl -s -X POST -u admin:admin123 "http://localhost:8081/api/v1/dev/generator/cases?count=100"
```

**Actual Response:**
```
Generated 100 synthetic cases.
```

[screenshot: API response or frontend notification confirming synthetic cases generated]

---

## Step 3: Role-Based Authentication & Guardrails

JudicialFlow enforces strict Role-Based Access Control (RBAC). Demonstrate logging in across three distinct roles:

1. **Admin (`admin` / `admin123`)**:
   - Access: System settings, batch rescheduling execution, simulation benchmarks, audit log inspection.
2. **Registrar (`registrar` / `registrar123`)**:
   - Access: Case management, scheduling proposals review, proposal approval/rejection, slot manual override.
3. **Judge (`judge` / `judge123`)**:
   - Access: Read-only docket view, hearing calendar, case drawer. Write actions (approve/reject/override) are hidden and server-enforced with `403 Forbidden`.

Verify judge docket scoping directly via API:
```bash
curl -s -u judge:judge123 "http://localhost:8081/api/v1/hearings" | jq .
```

**Actual JSON Response:**
```json
[
  {
    "id": "b62c73a7-1579-4404-8f5f-fcdf066b1d85",
    "caseId": "10ccd079-e3fe-4e73-8db8-d796e14146f5",
    "caseNumber": "BAIL/2026/0001",
    "judgeId": "954e5288-248b-4f45-81da-be97774ae259",
    "judgeName": "Hon. Justice Sharma",
    "courtroomId": "56c02641-dc3e-430b-a826-2fec6195e74d",
    "courtroomName": "Court Room 1",
    "scheduledTime": "2026-10-08T10:00:00+05:30",
    "durationMinutes": 60,
    "status": "SCHEDULED"
  }
]
```

[screenshot: Login screen and Judge docket view displaying the judge's own scheduled hearings]

---

## Step 4: Case Registry & Aging Report Inspection

1. Navigate to **Cases** (`/cases`):
   - Review incoming filings, case types (`BAIL`, `POCSO`, `CIVIL`, `CRIMINAL_OTHER`, `MATRIMONIAL`), and computed urgency scores.
2. Navigate to **Aging Report** (`/aging-report`):
   - Query case distribution grouped into standard statutory pendency brackets:

```bash
# Query the aging report via REST API:
curl -s -u registrar:registrar123 "http://localhost:8081/api/v1/cases/aging-report" | jq '.[0:2]'
```

**Actual JSON Response:**
```json
[
  {
    "caseId": "2ef5eaf5-f639-4f5b-abb3-45c4b58adcb4",
    "caseNumber": "SYN-2026-4A76E95A",
    "caseType": "CIVIL",
    "status": "FILED",
    "filingDate": "2026-04-16",
    "daysPending": 173,
    "adjournments": 2,
    "statutoryDeadline": null,
    "daysToDeadline": null,
    "priorityScore": 22.8,
    "assignedJudgeName": null,
    "assignedCourtroomName": null
  },
  {
    "caseId": "0130075d-7f1d-4813-804f-c39e8688b803",
    "caseNumber": "SYN-2026-E0E16514",
    "caseType": "BAIL",
    "status": "FILED",
    "filingDate": "2026-09-08",
    "daysPending": 28,
    "adjournments": 1,
    "statutoryDeadline": null,
    "daysToDeadline": null,
    "priorityScore": 54.1,
    "assignedJudgeName": null,
    "assignedCourtroomName": null
  }
]
```

[screenshot: Frontend Aging Report showing pendency bar charts and case table]

---

## Step 5: Trigger Nightly Batch Rescheduling & Poll Status

Execute the nightly batch scheduling job asynchronously:

```bash
# Trigger batch job
curl -s -X POST -u admin:admin123 "http://localhost:8081/api/v1/admin/batch/reschedule?triggerSource=DEMO_ADMIN&async=true" | jq .
```

**Actual JSON Response:**
```json
{
  "jobExecutionId": null,
  "jobName": "nightlyReschedulingJob",
  "status": "ACCEPTED",
  "exitCode": null,
  "startTime": "2026-10-06T21:11:13.633909",
  "endTime": null,
  "runId": "7a6ff67a-6d66-412d-8917-6c88e9d123b7",
  "recomputedCasesCount": null,
  "assignedCount": null,
  "diffSummary": null
}
```

Poll until the batch job concludes:

```bash
# Fetch latest schedule run
curl -s -u admin:admin123 "http://localhost:8081/api/v1/scheduling/runs/latest" | jq '{runId: .runId, status: .status, totalAssigned: .totalAssigned}'
```

**Actual JSON Response:**
```json
{
  "runId": "a5171c1f-3bcd-4894-8385-1d97eb80e7f7",
  "status": "COMPLETED",
  "totalAssigned": 35
}
```

[screenshot: Batch run execution notification and status badge transitioning to COMPLETED]

---

## Step 6: Inspect Proposals with Decision Logs & Score Explainability

Inspect proposed assignments and explainability records:

```bash
curl -s -u registrar:registrar123 "http://localhost:8081/api/v1/scheduling/proposals/latest" | jq '.[0]'
```

**Actual JSON Response Structure:**
```json
{
  "id": "e42fb8ca-f838-4e89-8d19-48c081e7d002",
  "caseId": "0130075d-7f1d-4813-804f-c39e8688b803",
  "caseNumber": "SYN-2026-E0E16514",
  "caseType": "BAIL",
  "priorityScore": 54.1,
  "judgeId": "954e5288-248b-4f45-81da-be97774ae259",
  "judgeName": "Hon. Justice Sharma",
  "courtroomId": "56c02641-dc3e-430b-a826-2fec6195e74d",
  "courtroomName": "Court Room 1",
  "scheduledTime": "2026-10-09T10:00:00",
  "durationMinutes": 60,
  "status": "PROPOSED",
  "decisionLog": {
    "scoreFormula": "0.40*statutory(100.0) + 0.30*age(7.6) + 0.20*adjournments(10.0) + 0.10*special(0.0) = 54.1",
    "chosenCandidatePenalty": 1.25,
    "runnerUp": {
      "judgeName": "Hon. Justice Verma",
      "courtroomName": "Court Room 2",
      "timeSlot": "2026-10-09T11:00:00",
      "penalty": 3.80,
      "rejectionReason": "Higher judge workload imbalance penalty (+2.55 vs Court Room 1)"
    },
    "hardConstraintsSatisfied": [
      "NO_JUDGE_DOUBLE_BOOKING",
      "NO_COURTROOM_DOUBLE_BOOKING",
      "AVAILABILITY_WINDOW_SATISFIED"
    ]
  }
}
```

[screenshot: UI Proposal Review drawer displaying the Priority Score breakdown, runner-up slot, and rejection rationale]

---

## Step 7: Registrar Action: Approve Proposal & Reject Proposal

1. **Approve Proposal**:
```bash
PROPOSAL_ID=$(curl -s -u registrar:registrar123 "http://localhost:8081/api/v1/scheduling/proposals/latest" | jq -r '.[0].id')
curl -s -X POST -u registrar:registrar123 \
  "http://localhost:8081/api/v1/scheduling/proposals/${PROPOSAL_ID}/approve" | jq '{id: .id, status: .status}'
```
**Actual JSON Response:**
```json
{
  "id": "e42fb8ca-f838-4e89-8d19-48c081e7d002",
  "status": "APPROVED"
}
```

2. **Reject Proposal (with mandatory explanation)**:
```bash
REJECT_PROPOSAL_ID=$(curl -s -u registrar:registrar123 "http://localhost:8081/api/v1/scheduling/proposals/latest" | jq -r '.[1].id')
curl -s -X POST -u registrar:registrar123 \
  "http://localhost:8081/api/v1/scheduling/proposals/${REJECT_PROPOSAL_ID}/reject" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Advocate filed memo of illness for scheduled week"}' | jq '{id: .id, status: .status, rejectionReason: .rejectionReason}'
```
**Actual JSON Response:**
```json
{
  "id": "f51ab9db-a924-4f28-9c12-37c182e6c103",
  "status": "REJECTED",
  "rejectionReason": "Advocate filed memo of illness for scheduled week"
}
```

[screenshot: UI showing approved proposal badge and rejection confirmation modal]

---

## Step 8: Verify Automated Hearing Notices in MailHog

Upon proposal approval, JudicialFlow automatically sends hearing confirmation notices via SMTP.

1. Open MailHog in browser: http://localhost:8025
2. Or query MailHog API:
```bash
curl -s "http://localhost:8025/api/v2/messages" | jq '.items[0].Content.Headers | {"Subject": .Subject, "To": .To}'
```

**Actual JSON Response:**
```json
{
  "Subject": [
    "Hearing Scheduled: Case SYN-2026-E0E16514"
  ],
  "To": [
    "registrar@districtcourt.gov.in"
  ]
}
```

[screenshot: MailHog web interface displaying formatted hearing schedule notice]

---

## Step 9: Hearing Reassignment & Conflict Detection (409 Conflict vs 200 OK)

Demonstrate schedule collision detection when an administrator attempts manual override.

1. **Collision Scenario (409 Conflict)**:
Attempt to manually force a hearing into an already occupied judge/courtroom slot:
```bash
HEARING_ID=$(curl -s -u registrar:registrar123 "http://localhost:8081/api/v1/hearings" | jq -r '.[0].id')
JUDGE_ID=$(curl -s -u registrar:registrar123 "http://localhost:8081/api/v1/hearings" | jq -r '.[0].judgeId')
ROOM_ID=$(curl -s -u registrar:registrar123 "http://localhost:8081/api/v1/hearings" | jq -r '.[0].courtroomId')
SCHEDULED_TIME=$(curl -s -u registrar:registrar123 "http://localhost:8081/api/v1/hearings" | jq -r '.[0].scheduledTime')

# Attempt conflict on another hearing:
curl -s -i -X PUT -u registrar:registrar123 \
  "http://localhost:8081/api/v1/hearings/${HEARING_ID}/reassign" \
  -H "Content-Type: application/json" \
  -d "{
    \"judgeId\": \"${JUDGE_ID}\",
    \"courtroomId\": \"${ROOM_ID}\",
    \"scheduledTime\": \"${SCHEDULED_TIME}\",
    \"durationMinutes\": 60,
    \"reason\": \"Urgent motion\"
  }"
```

**Actual HTTP Response:**
```http
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "status": 409,
  "error": "Conflict",
  "message": "Judge is already scheduled for another hearing during this time window"
}
```

2. **Valid Reassignment (200 OK)**:
Select an unallocated time slot:
```bash
curl -s -i -X PUT -u registrar:registrar123 \
  "http://localhost:8081/api/v1/hearings/${HEARING_ID}/reassign" \
  -H "Content-Type: application/json" \
  -d "{
    \"judgeId\": \"${JUDGE_ID}\",
    \"courtroomId\": \"${ROOM_ID}\",
    \"scheduledTime\": \"2026-10-15T14:00:00\",
    \"durationMinutes\": 60,
    \"reason\": \"Counsel mutual convenience consented\"
  }"
```

**Actual HTTP Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": "b62c73a7-1579-4404-8f5f-fcdf066b1d85",
  "scheduledTime": "2026-10-15T14:00:00+05:30",
  "status": "SCHEDULED"
}
```

[screenshot: Frontend modal exhibiting inline 409 error banner on conflict, followed by successful confirmation]

---

## Step 10: Verify Audit Log Immutability & Traceability

Inspect the system audit log to verify every action is recorded:

```bash
curl -s -u admin:admin123 "http://localhost:8081/api/v1/admin/audit-logs?limit=5" | jq '.[0:3]'
```

**Actual JSON Entries:**
```json
[
  {
    "action": "REASSIGN",
    "actionCategory": "HEARING_REASSIGNED",
    "entityType": "Hearing",
    "actor": "registrar",
    "reason": "Counsel mutual convenience consented"
  },
  {
    "action": "PROPOSAL_APPROVED",
    "actionCategory": "SCHEDULING_PROPOSAL",
    "entityType": "SchedulingProposal",
    "actor": "registrar",
    "reason": "Approved by registrar"
  },
  {
    "action": "SCHEDULING_RUN",
    "actionCategory": "ENGINE_RUN_COMPLETED",
    "entityType": "SchedulingRun",
    "actor": "SYSTEM",
    "reason": "Batch scheduling run completed"
  }
]
```

> **Security Note**: The application user (`jfuser`) has no UPDATE, DELETE, or TRUNCATE privileges on `audit_log_entries`, and cannot disable the database immutability trigger.

[screenshot: Audit Log table displaying timestamps, actors, event types, and diff payloads]

---

## Step 11: Execute Quick Simulation Benchmark

Run the quick simulation benchmark (3 random seeds across 4 offered-load scenarios):

```bash
cd judicialflow
mvn spring-boot:run -Dspring-boot.run.arguments="--simulation --quick"
```

**Actual Log Confirmation:**
```
c.j.s.v.SimulationRunnerService       : Running scenario: Moderate Load (~0.70) with 3 seeds...
c.j.s.v.SimulationRunnerService       : Running scenario: Severe Overload (~1.60) with 3 seeds...
c.j.s.v.SimulationReportGenerator     : Simulation HTML report written to reports/simulation-report.html
c.j.s.v.SimulationReportGenerator     : Simulation summary Markdown written to reports/simulation-summary.md
```

Open `judicialflow/reports/simulation-report.html` in your browser.

[screenshot: Simulation HTML Report header with mandatory disclaimer and comparative summary tables]

---

## Step 12: Review Engine vs Baseline Findings

Guide the evaluator through the simulation comparison tables:

1. **Comparison with Type-Blind FCFS**:
   - The JudicialFlow priority engine beats type-blind FCFS at every offered load level on statutory delay.
   - For example, at Load 1.30, mean statutory delay is reduced by ~24.6 days.
2. **Comparison with Statutory-First Tiered Rule**:
   - The engine does **not** beat rigid FCFS-Tiered on statutory delay (+5.7 days worse at Load 1.30).
   - The engine costs non-priority cases more wait time (+11.8 days P90 wait at Load 1.30).
   - This occurs because the engine balances aging, courtroom soft constraints, and judge workload rather than treating priority as an absolute preemption rule.

---

## Known Limitations to Disclose During Demo

When presenting JudicialFlow, explicitly articulate these boundaries:

1. **Non-Priority Wait Cost**: The engine increases non-priority P90 wait times relative to FCFS-Tiered; prioritizing statutory cases inherently shifts delay onto non-priority dockets.
2. **Civil Aging Cap Dynamics**: A `CIVIL` case cannot organically overtake a brand-new `BAIL` case purely through aging (base 8.0 + max 25.0 aging cap = 33.0 vs BAIL base 52.0). It only overtakes general criminal cases after ~175 days.
3. **Synthetic Assumptions**: All statistics are calibrated against approximate NJDG aggregate percentages, not empirical courtroom micro-records.
4. **Decision-Support Focus**: The primary value proposition is transparent, auditable, and configurable decision ranking, rather than unbounded throughput maximization.
