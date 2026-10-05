# Case Duration Estimator (Phase 5)

## Overview
The Duration Estimator provides a realistic expected-resolution-timeline estimate for cases in the JudicialFlow system. Instead of simply generating an arbitrary "next date", this module calculates a predicted duration (in days) using historical data.

## Methodology
To ensure interpretability and defensibility, we use a **Simple Linear Regression** model. We chose *not* to build a complex ML pipeline to avoid over-engineering. The model is implemented directly in pure Java, avoiding external dependencies and complex matrix inversions by using Ordinary Least Squares (OLS) regression for a single variable grouped by case type.

### How it Works
1. **Grouping by Case Type:** A separate linear regression model is trained for each `CaseType` (e.g., CIVIL, CRIMINAL, BAIL).
2. **Feature & Target:** 
   * **Feature (X):** `priorAdjournments` count.
   * **Target (Y):** Actual duration from `filingDate` to `disposedDate` (in days).
3. **Training Data:** The models are trained on the synthetic dataset generated in Phase 1 (i.e., cases with `status = DISPOSED`).
4. **Prediction:** Given a new or active case, the system retrieves the model for that `CaseType` and predicts the duration based on its current `priorAdjournments`.

### Endpoint
The REST endpoint `GET /api/v1/estimates/cases/{caseId}` returns a realistic estimate, including:
* `predictedDurationDays`: The exact output of the linear regression.
* `minDurationDays` / `maxDurationDays`: A date range created using the Mean Absolute Error (MAE) of the model.
* `basis`: Explainability text indicating the sample size and model features.
* `topFeatures`: Highlights the variables driving the estimate (CaseType and PriorAdjournments).

## Honest Evaluation & Limitations
- **Data Source Limitation:** The model learns from *synthetic* data. Therefore, the predictions heavily reflect the data generator's assumptions from Phase 1 rather than real-world court dynamics.
- **Model Simplicity:** As a simple linear regression based solely on prior adjournments, the model assumes a linear relationship between adjournments and total case duration. It does not account for complex non-linear dynamics, varying judge loads across different jurisdictions, or case-specific complexities.
- **Accuracy (MAE):** Based on evaluations with the synthetic data, the Mean Absolute Error varies by case type (often predicting within a reasonable window, but defaulting to a generic 90-day MAE fallback when insufficient historical data exists). This error metric provides a bounding box for the `minDurationDays` and `maxDurationDays`.

*Computed using plain Java Simple Linear Regression. No external ML dependencies.*
