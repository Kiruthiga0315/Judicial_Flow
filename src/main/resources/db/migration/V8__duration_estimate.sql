CREATE TABLE duration_estimates (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    predicted_duration_days DOUBLE PRECISION NOT NULL,
    min_duration_days INT NOT NULL,
    max_duration_days INT NOT NULL,
    basis VARCHAR(255) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    top_features VARCHAR(255),
    computed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (case_id) REFERENCES cases(id)
);
