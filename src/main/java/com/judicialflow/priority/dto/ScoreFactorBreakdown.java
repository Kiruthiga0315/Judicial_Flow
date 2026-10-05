package com.judicialflow.priority.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Represents a single factor's contribution to the overall priority score.
 *
 * <p>The dashboard renders this breakdown directly so that the registrar can
 * understand <em>why</em> a case has a particular score without reading code.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code factorName}  – human-readable name, e.g. "Case Type Urgency"</li>
 *   <li>{@code rawValue}    – the measured value before weighting, e.g. "180 days pending"</li>
 *   <li>{@code weight}      – the configured weight applied to the raw value</li>
 *   <li>{@code contribution}– {@code rawValue × weight} (capped where applicable),
 *                             i.e., how many points this factor added to the total</li>
 *   <li>{@code explanation} – one plain-English sentence the dashboard can display verbatim</li>
 * </ul>
 */
@Value
@Builder
@Schema(description = "Breakdown of a single factor contributing to the priority score")
public class ScoreFactorBreakdown {

    @Schema(description = "Human-readable factor name", example = "Case Type Urgency")
    String factorName;

    @Schema(description = "Raw measured value before weighting", example = "180.0")
    BigDecimal rawValue;

    @Schema(description = "Configured weight applied to the raw value", example = "0.10")
    BigDecimal weight;

    @Schema(description = "Score points this factor contributed (raw × weight, capped if applicable)", example = "18.00")
    BigDecimal contribution;

    @Schema(description = "Plain-English explanation for the registrar dashboard",
            example = "Case has been pending for 180 days; contributes 18.00 points (capped at 40.00).")
    String explanation;
}
