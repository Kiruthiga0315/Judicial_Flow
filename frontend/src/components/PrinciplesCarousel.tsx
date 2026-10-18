import React, { useState, useEffect } from 'react';

interface Principle {
  quote: string;
  detail: string;
}

const PRINCIPLES: Principle[] = [
  {
    quote: 'Every score shows its reasoning.',
    detail: 'Complete multi-factor decomposition for statutory urgency, pending age, and bench capacity.',
  },
  {
    quote: 'Every override leaves an audit trail.',
    detail: 'Cryptographically linked entries record the exact justification for any manual listing alteration.',
  },
  {
    quote: 'Same inputs, same schedule.',
    detail: 'Deterministic multi-criteria optimization guarantees reproducible court rosters and eliminates bias.',
  },
];

export const PrinciplesCarousel: React.FC<{ className?: string }> = ({ className = '' }) => {
  const [activeIndex, setActiveIndex] = useState(0);
  const [isHovered, setIsHovered] = useState(false);

  useEffect(() => {
    if (isHovered) return;

    const interval = setInterval(() => {
      setActiveIndex((prev) => (prev + 1) % PRINCIPLES.length);
    }, 6000);

    return () => clearInterval(interval);
  }, [isHovered]);

  const active = PRINCIPLES[activeIndex];

  return (
    <div
      className={`relative flex items-end justify-between gap-4 p-5 rounded-2xl bg-jf-cream/90 backdrop-blur-md border border-jf-border-subtle shadow-card-soft transition-all duration-200 hover:shadow-[0_12px_28px_-6px_rgba(80,78,118,0.16)] hover:border-jf-violet/30 ${className}`}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      onFocus={() => setIsHovered(true)}
      onBlur={() => setIsHovered(false)}
    >
      <div className="flex-1 pr-2">
        {/* Large institutional quote mark */}
        <div className="text-3xl font-serif text-jf-violet/40 leading-none select-none mb-1" aria-hidden="true">
          “
        </div>

        {/* Dynamic principle text with upward slide transition */}
        <div
          key={activeIndex}
          className="min-h-[64px] transition-all duration-300 animate-[fadeInUp_350ms_ease-out]"
        >
          <p className="text-sm font-bold text-jf-ink leading-snug tracking-tight">
            {active.quote}
          </p>
          <p className="text-xs text-jf-ink-muted leading-relaxed mt-1">
            {active.detail}
          </p>
        </div>

        {/* Footer info without play/pause button */}
        <div className="flex items-center justify-between mt-3 pt-2 border-t border-jf-border-subtle/60">
          <span className="text-[10px] uppercase tracking-wider font-semibold text-jf-ink-muted">
            Institutional Principles
          </span>
          <span className="text-[10px] font-semibold text-jf-ink-muted/80 tabular-nums">
            {activeIndex + 1} / {PRINCIPLES.length}
          </span>
        </div>
      </div>

      {/* Vertical progress dots on the right edge */}
      <div className="flex flex-col gap-1.5 self-center select-none" aria-label="Carousel navigation">
        {PRINCIPLES.map((_, idx) => (
          <button
            key={idx}
            type="button"
            onClick={() => setActiveIndex(idx)}
            aria-label={`Go to principle ${idx + 1}`}
            className={`w-1.5 rounded-full transition-all duration-300 jf-focus-ring ${
              idx === activeIndex
                ? 'h-6 bg-jf-violet shadow-[0_0_8px_rgba(80,78,118,0.35)]'
                : 'h-1.5 bg-jf-violet/30 hover:bg-jf-violet/70 hover:shadow-[0_0_6px_rgba(80,78,118,0.2)]'
            }`}
          />
        ))}
      </div>
    </div>
  );
};
