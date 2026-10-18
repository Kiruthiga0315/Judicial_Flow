import React from 'react';

export interface ScoreBreakdown {
  timePendingScore: number;     // e.g. 0-100
  statutoryUrgencyScore: number; // e.g. 0-100
  adjournmentsScore: number;    // e.g. 0-100
  deadlineScore: number;        // e.g. 0-100
  compositeScore: number;       // e.g. 82.4
}

interface ScoreRingProps {
  score?: number;
  breakdown?: Partial<ScoreBreakdown>;
  size?: number; // size in px, default 130
  label?: string;
  sublabel?: string;
  className?: string;
}

export const ScoreRing: React.FC<ScoreRingProps> = ({
  score = 84.5,
  breakdown = {
    timePendingScore: 75,
    statutoryUrgencyScore: 90,
    adjournmentsScore: 60,
    deadlineScore: 85,
  },
  size = 130,
  label = 'Priority Score',
  sublabel = 'Sample case, synthetic data',
  className = '',
}) => {
  const strokeWidth = 5.5;
  const center = size / 2;

  // Arc configurations (4 concentric arcs from outer to inner)
  const arcs = [
    {
      radius: center - 8,
      percent: breakdown.timePendingScore ?? 75,
      color: 'var(--jf-violet)',
      label: 'Aging / Time Pending',
    },
    {
      radius: center - 16,
      percent: breakdown.statutoryUrgencyScore ?? 90,
      color: 'var(--jf-orange)',
      label: 'Statutory Urgency',
    },
    {
      radius: center - 24,
      percent: breakdown.adjournmentsScore ?? 60,
      color: 'var(--jf-sage)',
      label: 'Adjournments',
    },
    {
      radius: center - 32,
      percent: breakdown.deadlineScore ?? 85,
      color: 'var(--jf-apricot)',
      label: 'Disposal Target',
    },
  ];

  return (
    <div className={`flex flex-col items-center select-none ${className}`}>
      <div className="relative" style={{ width: size, height: size }}>
        <svg
          width={size}
          height={size}
          viewBox={`0 0 ${size} ${size}`}
          className="transform -rotate-90"
          aria-hidden="true"
        >
          {arcs.map((arc, i) => {
            const circumference = 2 * Math.PI * arc.radius;
            const strokeDashoffset = circumference - (arc.percent / 100) * circumference;

            return (
              <g key={i}>
                {/* Background track */}
                <circle
                  cx={center}
                  cy={center}
                  r={arc.radius}
                  fill="transparent"
                  stroke={arc.color}
                  strokeOpacity={0.18}
                  strokeWidth={strokeWidth}
                />
                {/* Active value arc */}
                <circle
                  cx={center}
                  cy={center}
                  r={arc.radius}
                  fill="transparent"
                  stroke={arc.color}
                  strokeWidth={strokeWidth}
                  strokeDasharray={circumference}
                  strokeDashoffset={strokeDashoffset}
                  strokeLinecap="round"
                  className="transition-all duration-700 ease-out"
                />
              </g>
            );
          })}
        </svg>

        {/* Center score readout */}
        <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
          <span className="text-2xl font-bold tracking-tight text-jf-ink font-sans tabular-nums leading-none">
            {score.toFixed(1)}
          </span>
          <span className="text-[9px] uppercase tracking-wider font-semibold text-jf-ink-muted mt-0.5">
            / 100
          </span>
        </div>
      </div>

      {label && (
        <div className="mt-2 text-center">
          <div className="text-xs font-semibold text-jf-ink">{label}</div>
          {sublabel && (
            <div className="text-[11px] text-jf-ink-muted font-medium mt-0.5">
              {sublabel}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
