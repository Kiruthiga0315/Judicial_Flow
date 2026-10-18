import React from 'react';

interface JudicialLogomarkProps {
  size?: 'sm' | 'md' | 'lg';
  showWordmark?: boolean;
  className?: string;
}

export const JudicialLogomark: React.FC<JudicialLogomarkProps> = ({
  size = 'md',
  showWordmark = true,
  className = '',
}) => {
  const iconSizes = {
    sm: 'w-7 h-7',
    md: 'w-10 h-10',
    lg: 'w-12 h-12',
  };

  const textSizes = {
    sm: 'text-lg',
    md: 'text-2xl',
    lg: 'text-3xl',
  };

  return (
    <div className={`inline-flex items-center gap-3 ${className}`}>
      {/* Original abstract judicial scale & flow glyph */}
      <div
        className={`${iconSizes[size]} relative flex items-center justify-center rounded-xl bg-jf-violet text-jf-cream shadow-sm p-1.5`}
        aria-hidden="true"
      >
        <svg
          viewBox="0 0 36 36"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          className="w-full h-full"
        >
          {/* Central fulcrum & vertical pillar */}
          <path
            d="M18 6V29"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
          />
          {/* Base balance bar */}
          <path
            d="M11 29H25"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
          />
          {/* Dynamic curved balance beam / flow horizon */}
          <path
            d="M6 13.5C11 11.5 25 15.5 30 13.5"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
          />
          {/* Left balance pan suspension */}
          <path
            d="M6 14L8.5 21H3.5L6 14Z"
            fill="currentColor"
            fillOpacity="0.85"
          />
          {/* Right balance pan suspension */}
          <path
            d="M30 14L32.5 21H27.5L30 14Z"
            fill="currentColor"
            fillOpacity="0.85"
          />
          {/* Orange flow accent node on the fulcrum apex (<=10% hot accent) */}
          <circle
            cx="18"
            cy="6.5"
            r="3"
            className="fill-jf-orange"
          />
          {/* Flow pulse arc */}
          <path
            d="M14 6.5C14 4.29086 15.7909 2.5 18 2.5C20.2091 2.5 22 4.29086 22 6.5"
            stroke="var(--jf-orange)"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeDasharray="2 2"
          />
        </svg>
      </div>

      {showWordmark && (
        <div className="flex flex-col leading-none">
          <span className={`font-bold tracking-tight text-jf-ink font-sans ${textSizes[size]}`}>
            Judicial<span className="text-jf-violet">Flow</span>
          </span>
          <span className="text-[10px] uppercase tracking-widest text-jf-ink-muted font-semibold mt-0.5">
            Registry & Scheduling
          </span>
        </div>
      )}
    </div>
  );
};
