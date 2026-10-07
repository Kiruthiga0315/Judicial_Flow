# Case Duration Estimator (Phase 5 & Phase 9.B)

> **Disclaimer**: All models are trained and evaluated on synthetic data generated under declared assumptions. Metrics reflect synthetic generator dynamics, not empirical court delay forecasting.

## Overview
The Duration Estimator provides an expected-resolution timeline for cases in JudicialFlow. For interpretability and algorithmic auditability, we use Ordinary Least Squares (OLS) **Simple Linear Regression** grouped by `CaseType`, predicting case resolution timeline from filing date to disposal date based on `priorAdjournments`.

## Training and Validation Split
To prevent overoptimistic evaluation, models are trained and validated using stratified historical disposed cases:
1. **Large Subsets ($\ge 30$ cases)**:
   - Evaluated using a strict **80/20 train/test holdout split** shuffled with a fixed reproducible random seed (default: 42).
   - Test MAE is computed strictly on the held-out 20% test slice.
2. **Small Subsets (8 to 29 cases)**:
   - Evaluated using **Leave-One-Out Cross-Validation (LOOCV)** across all available cases.
3. **Insufficient Data ($< 8$ cases)**:
   - No regression model is trained. The system uses a generic default (180 days predicted, 90 days MAE window) and explicitly labels the output as `"fallback, insufficient data"`.

## Baseline Comparison and Fallback Rule
For each case type, the regression model's Test MAE is compared against a **naive per-type-mean baseline** (predicting the mean duration of the training set for every case):
$$\text{Baseline MAE} = \frac{1}{|D_{\text{test}}|} \sum_{i \in D_{\text{test}}} |y_i - \bar{y}_{\text{train}}|$$

- **If the model beats the baseline ($\text{Test MAE} < \text{Baseline MAE}$)**:
  - The OLS regression equation is used for predictions: $\hat{y} = \beta_0 + \beta_1 \cdot \text{adjournments}$.
  - `beatsBaseline` is reported as `true`.
- **If the model does NOT beat the baseline ($\text{Test MAE} \ge \text{Baseline MAE}$)**:
  - The model **remains strictly on the naive per-type-mean baseline**: $\hat{y} = \bar{y}_{\text{train}}$, with MAE set to the baseline MAE.
  - `beatsBaseline` is reported as `false`.
  - The `basis` explanation explicitly states: `"Model did not beat naive per-type-mean baseline (...); keeping type on naive mean baseline (... days) based on N training cases."`

## API Endpoints
1. `GET /api/v1/estimates/cases/{caseId}`
   - `predictedDurationDays`: Estimated resolution days (or baseline mean).
   - `minDurationDays` / `maxDurationDays`: Clamped lower and upper bounds $[\max(1, \hat{y} - \text{MAE}), \max(1, \hat{y} + \text{MAE})]$.
   - `basis`: Full disclosure text including training sample count, holdout size, validation method, and baseline comparison.
   - `testMae`: Test MAE on the holdout evaluation set.
   - `baselineMaeDays`: Naive per-type-mean baseline MAE.
   - `beatsBaseline`: Boolean flag indicating whether the regression beat the baseline.
   - `trainingSampleCount`: Exact number of disposed cases used for training.
2. `GET /api/v1/estimates/models`
   - Returns a dictionary of all case types with their evaluation status, sample sizes, Test MAE, Baseline MAE, and whether the naive baseline is active.
3. `POST /api/v1/estimates/train`
   - Triggers model retraining on demand.

## Known Limitations & Circularity Caveat
- **Synthetic Data**: Predictions reflect synthetic caseload relationships, not real-world judicial processes.
- **Circular Feature Generation**: In the synthetic caseload generator (`SyntheticCaseGeneratorController.java`), `priorAdjournments` for disposed cases is generated directly as a function of disposal duration: `adjournments = (duration / 60) + noise`. Consequently, when linear regression fits `duration` on `priorAdjournments`, the predictive performance is an artifact of the generator's formula. This gain is circular and must not be interpreted as empirical predictive power.
- **Survivor / Feature Leakage**: Active cases have fewer adjournments at filing than at disposal; estimating an active case's total duration from intermediate adjournment count introduces feature leakage. This is disclosed directly in the API's `topFeatures` string.

