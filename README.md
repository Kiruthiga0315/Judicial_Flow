# JudicialFlow

A Java / Spring Boot decision-support system that optimizes trial court hearing scheduling.

## Prerequisites

- Docker Desktop (or Docker Engine)
- JDK 17+
- Maven

## Local Development Setup

1. **Start PostgreSQL**: We use Docker to run a local PostgreSQL instance. Run the following command from the root of the `judicialflow` directory:
   ```bash
   docker compose up -d
   ```
   This starts the DB on the configured `DB_PORT` (default `5433`).

2. **Run the Application**: 
   ```bash
   mvn spring-boot:run
   ```
   The application will connect to the PostgreSQL instance and automatically apply the schema migrations via Flyway.

3. **Run the Tests**:
   ```bash
   mvn clean test
   ```
   Integration tests run against a real PostgreSQL 15 container managed via Testcontainers.

## Environment Variables

- `SERVER_PORT`: Application port (default: 8081)
- `DB_HOST`: Host for postgres (default: localhost)
- `DB_PORT`: Port for postgres (default: 5433)
- `DB_NAME`: Database name (default: judicialflow)
- `DB_USER`: Database user (default: jfuser)
- `DB_PASSWORD`: Database password (default: jfpass)
- `generator.seed`: Seed for deterministic case generation (default: 12345)

## Synthetic Case Generator

The system uses purely synthetic data calibrated to match approximate published NJDG (National Judicial Data Grid) aggregate statistics. No real case or individual data is used. The targets are illustrative approximations of published NJDG aggregates, not exact figures. We use a deterministic generator that targets these approximate NJDG statistics within a ±1.5 points tolerance.

To generate a sample synthetic caseload, trigger the dev-only REST endpoint once the application is running:

```bash
curl -X POST "http://localhost:8081/api/dev/generator/cases?count=100"
```
This will insert 100 cases into the database with case types and pendency distributions matching the NJDG aggregates.

## API Documentation (Swagger UI)

When the application is running, the interactive OpenAPI Swagger UI and schema are available at:
- **Swagger UI**: [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
- **OpenAPI JSON**: [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs)

## Phase 4: Scheduling Engine

### Algorithm

The scheduling engine uses a **greedy weighted assignment with local-search repair** algorithm:

1. **Greedy Pass**: Cases are sorted by priority score (descending). For each case, all possible (judge × courtroom × time slot) candidates are generated, filtered by hard constraints, and scored by soft constraints. The lowest-penalty candidate is selected.

2. **Deferred Queue**: Cases with linked-case dependencies are deferred until their prerequisite is scheduled, then processed.

3. **Complexity**: O(C × J × R × T) where C = cases, J = judges, R = courtrooms, T = time slots.

### Hard Constraints (never violated)
- No judge double-booking
- No courtroom double-booking
- Judge availability window enforcement
- Courtroom availability window enforcement
- Linked case sequencing (prerequisite must be scheduled first)

### Soft Constraints (weighted optimization)
- **Priority ordering** (weight 0.5): Higher-priority cases get earlier slots
- **Workload balance** (weight 0.3): Even distribution across judges
- **Schedule churn** (weight 0.2): Minimize changes from previous run

### API Endpoints

**Trigger a scheduling run** (returns proposals, does NOT auto-commit):
```bash
curl -X POST http://localhost:8081/api/scheduling/run \
  -H "Content-Type: application/json" \
  -d '{"horizonDays": 5, "defaultDurationMinutes": 60}'
```

**Retrieve a past run:**
```bash
curl http://localhost:8081/api/scheduling/runs/{runId}
```

**Manual override:**
```bash
curl -X POST http://localhost:8081/api/scheduling/override \
  -H "Content-Type: application/json" \
  -d '{"caseId":"...","judgeId":"...","courtroomId":"...","scheduledTime":"2026-09-21T10:00:00","reason":"Registrar requested","overriddenBy":"Registrar Kumar"}'
```

### Decision Log

Every assignment includes an explainability record showing:
- **Chosen slot**: judge, courtroom, time, soft score
- **Runner-up**: the next-best option that was passed over
- **Rejection reason**: why the runner-up scored worse
- **Constraints satisfied**: list of hard constraints verified

## Phase 5: Case Duration Estimator

The Case Duration Estimator predicts the total expected resolution time (in days) from filing to disposal for a given case.

### Methodology and Train/Test Validation
We implement a **Simple Linear Regression** grouped by `CaseType`, using `priorAdjournments` as the sole explanatory feature (X) and total days to disposal as the target (Y). 
Instead of a complex ML pipeline, this relies on Ordinary Least Squares computed in plain Java to guarantee total interpretability.

When the application boots (or trains on-demand), it splits the `DISPOSED` synthetic historical cases into an **80/20 train/test split**. 
* **Training:** The model coefficients (intercept, slope) are derived purely from the 80% training set.
* **Validation (MAE):** The model predicts the duration for the remaining 20% holdout test set, and calculates the **Mean Absolute Error (MAE)**. This test MAE directly determines the bounding box (`minDurationDays`, `maxDurationDays`) around the estimate returned to the client.

### Explainability
Because we use isolated simple linear regressions per `CaseType`, the influential features driving any given prediction are inherently:
1. **Case Type** (which dictates *which* regression model is applied).
2. **Prior Adjournments** (the numerical value input into the selected linear equation).

### Honest Evaluation & Limitations
* **Synthetic Data Bias:** The model relies entirely on the synthetic case data generated in Phase 1. Thus, its predictions heavily reflect the data generator's assumptions rather than the dynamics of real-world court dockets.
* **Limited Features:** Because it is a simple linear regression based solely on prior adjournments, it fails to account for complex, non-linear case nuances or dynamically changing judge caseloads.
* **Insufficient Data Fallbacks:** If a `CaseType` has fewer than 5 historical disposed cases, the system defaults to a generic fallback (180 days ± 90 days) since a robust train/test split is mathematically unfeasible.
