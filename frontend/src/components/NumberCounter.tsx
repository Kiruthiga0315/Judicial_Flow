import React, { useEffect, useState } from 'react';

interface NumberCounterProps {
  value: number;
  duration?: number;
  className?: string;
}

export const NumberCounter: React.FC<NumberCounterProps> = ({ value, duration = 1500, className }) => {
  const [displayValue, setDisplayValue] = useState(0);

  useEffect(() => {
    if (import.meta.env?.MODE === 'test' || duration === 0) {
      setDisplayValue(value);
      return;
    }
    let startTimestamp: number | null = null;
    let animationFrameId: number;

    const step = (timestamp: number) => {
      if (!startTimestamp) startTimestamp = timestamp;
      const progress = Math.min((timestamp - startTimestamp) / duration, 1);
      
      // easeOutExpo curve for smooth deceleration
      const easeProgress = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress);
      
      setDisplayValue(Math.floor(easeProgress * value));

      if (progress < 1) {
        animationFrameId = window.requestAnimationFrame(step);
      } else {
        setDisplayValue(value);
      }
    };

    animationFrameId = window.requestAnimationFrame(step);

    return () => {
      if (animationFrameId) {
        window.cancelAnimationFrame(animationFrameId);
      }
    };
  }, [value, duration]);

  return <span className={className}>{displayValue}</span>;
};
