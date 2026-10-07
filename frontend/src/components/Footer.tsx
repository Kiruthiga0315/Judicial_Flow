import React from 'react';

export const Footer: React.FC = () => {
  return (
    <footer className="w-full bg-slate-900 border-t border-slate-800 text-slate-400 py-3 px-6 text-center text-xs tracking-wide">
      <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-2">
        <span className="font-medium text-slate-300">
          JudicialFlow Scheduling & Registry System
        </span>
        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-400 font-medium border border-amber-500/20">
          <span className="w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"></span>
          Synthetic data: demo only; no real court records are used. Demo outputs under stated assumptions; no real-world impact.
        </span>
        <span className="text-slate-500">
          Indian Judicial Bench & Registry Automation
        </span>
      </div>
    </footer>
  );
};
