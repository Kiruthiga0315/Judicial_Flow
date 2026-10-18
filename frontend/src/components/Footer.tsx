import React from 'react';

export const Footer: React.FC = () => {
  return (
    <footer className="w-full bg-jf-cream/90 backdrop-blur-md border-t border-jf-border-subtle text-jf-ink-muted py-3 px-6 text-center text-xs tracking-wide">
      <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2.5">
        <span className="font-semibold text-jf-ink text-xs">
          JudicialFlow Scheduling & Registry System
        </span>
        <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-jf-cream-darker text-jf-text-orange font-medium border border-jf-text-orange/20 text-[11px]">
          <span className="w-1.5 h-1.5 rounded-full bg-jf-orange shrink-0" aria-hidden="true"></span>
          <span>Synthetic data: demo only; no real court records are used. Demo outputs under stated assumptions; no real-world impact.</span>
        </span>
        <span className="text-jf-ink-muted/80 text-[11px]">
          Indian Judicial Bench & Registry Automation
        </span>
      </div>
    </footer>
  );
};
