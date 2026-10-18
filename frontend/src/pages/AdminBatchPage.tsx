import React, { useState } from 'react';
import { api } from '../api/client';
import { ScheduleDiffSummary, BatchJobStatusResponse } from '../types/api';
import {
  Play,
  RotateCw,
  CheckCircle2,
  AlertCircle,
  Clock,
  Sliders,
  Layers,
} from 'lucide-react';

export const AdminBatchPage: React.FC = () => {
  const [horizonDays, setHorizonDays] = useState<number>(30);
  const [isLaunching, setIsLaunching] = useState<boolean>(false);
  const [jobStatus, setJobStatus] = useState<BatchJobStatusResponse | null>(null);
  const [diffSummary, setDiffSummary] = useState<ScheduleDiffSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  const pollJobStatus = async (jobExecutionId: number) => {
    const interval = setInterval(async () => {
      try {
        const statusRes = await api.getBatchJobStatus(jobExecutionId);
        setJobStatus(statusRes);

        if (statusRes.status === 'COMPLETED') {
          clearInterval(interval);
          setIsLaunching(false);
          if (statusRes.schedulingRunId) {
            fetchDiff(statusRes.schedulingRunId);
          }
        } else if (statusRes.status === 'FAILED') {
          clearInterval(interval);
          setIsLaunching(false);
          setError(`Batch job execution failed with exit code: ${statusRes.exitStatus}`);
        }
      } catch (err: any) {
        console.error('Error polling job status', err);
      }
    }, 1500);
  };

  const fetchDiff = async (runId: string) => {
    try {
      const diff = await api.getBatchScheduleDiff(runId);
      setDiffSummary(diff);
    } catch (err: any) {
      console.warn('Could not fetch diff for run', runId, err);
    }
  };

  const handleLaunchJob = async () => {
    setIsLaunching(true);
    setError(null);
    setDiffSummary(null);
    setJobStatus(null);
    try {
      const res = await api.triggerNightlyBatch('DASHBOARD_UI', horizonDays);
      pollJobStatus(res.jobExecutionId);
    } catch (err: any) {
      setIsLaunching(false);
      setError(err.message || 'Failed to trigger batch job');
    }
  };

  const changeBadgeColor = (type: string) => {
    switch (type) {
      case 'NEW':
        return 'bg-jf-sage/20 text-jf-ink border-jf-sage/50 font-bold';
      case 'MODIFIED':
        return 'bg-jf-apricot/40 text-jf-ink border-jf-apricot/70 font-bold';
      case 'COMMITTED':
        return 'bg-jf-lavender/30 text-jf-ink border-jf-lavender/60 font-bold';
      case 'REMOVED':
        return 'bg-jf-orange/15 text-jf-text-orange border-jf-orange/40 font-bold';
      default:
        return 'bg-jf-canvas text-jf-ink-muted border-jf-border-subtle font-medium';
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2">
          <h2 className="text-xl font-bold text-jf-ink tracking-tight">
            Nightly Rescheduling Batch Control
          </h2>
          <span className="px-2.5 py-0.5 rounded-full bg-jf-apricot/40 text-jf-ink text-[10px] font-bold uppercase tracking-wider border border-jf-apricot/70">
            Admin Only
          </span>
        </div>
        <p className="text-xs text-jf-ink-muted mt-0.5">
          Orchestrates Spring Batch 3-step pipeline: recomputing priority scores, executing constraint-satisfaction solver, and logging schedule diff.
        </p>
      </div>

      {/* Control Card */}
      <div className="bg-jf-cream rounded-2xl border-2 border-jf-border-subtle p-6 shadow-card jf-card-interactive hover:shadow-card-hover relative overflow-hidden space-y-4 animate-stagger-1">
        <div className="absolute top-0 inset-x-0 h-1 bg-jf-violet" />
        <h3 className="text-sm font-bold text-jf-ink flex items-center gap-2 pt-0.5">
          <Sliders className="w-4 h-4 text-jf-violet" />
          <span>Execution Parameters</span>
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-lg text-xs">
          <div>
            <label className="block text-jf-ink-muted font-medium mb-1">
              Scheduling Horizon (Business Days)
            </label>
            <input
              type="number"
              min="5"
              max="90"
              value={horizonDays}
              onChange={(e) => setHorizonDays(Number(e.target.value))}
              disabled={isLaunching}
              className="w-full rounded-md border-2 border-jf-border-subtle px-3 py-2 bg-white text-jf-ink tabular-nums focus:outline-none jf-focus-ring shadow-2xs"
            />
            <p className="text-[11px] text-jf-ink-muted mt-1 font-medium">
              Engine allocates non-disposed cases across Monday–Friday court sessions.
            </p>
          </div>
        </div>

        <div className="pt-2 flex items-center gap-3">
          <button
            onClick={handleLaunchJob}
            disabled={isLaunching}
            className="inline-flex items-center gap-2 px-4 py-2.5 jf-btn-primary rounded-lg text-xs font-semibold disabled:opacity-50 hover:shadow-glow-btn hover:-translate-y-0.5 active:translate-y-0 transition-all shadow-xs"
          >
            {isLaunching ? (
              <>
                <RotateCw className="w-4 h-4 animate-spin text-jf-lavender" />
                Pipeline Executing & Polling Status...
              </>
            ) : (
              <>
                <Play className="w-4 h-4 text-jf-apricot fill-jf-apricot" />
                Trigger Nightly Batch Pipeline
              </>
            )}
          </button>
        </div>

        {error && (
          <div className="p-3 bg-jf-orange/10 border-2 border-jf-orange/30 rounded-lg text-xs text-jf-text-orange flex items-center gap-2 font-medium">
            <AlertCircle className="w-4 h-4 text-jf-text-orange shrink-0" />
            <span>{error}</span>
          </div>
        )}
      </div>

      {/* Live Pipeline Status */}
      {jobStatus && (
        <div className="bg-jf-ink text-white rounded-xl p-5 shadow-lg space-y-3 animate-in fade-in text-xs border-2 border-jf-lavender/30">
          <div className="flex items-center justify-between pb-3 border-b border-jf-lavender/20">
            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-jf-apricot" />
              <span className="font-semibold text-sm">Batch Job Execution #{jobStatus.jobExecutionId}</span>
            </div>
            <span
              className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                jobStatus.status === 'COMPLETED'
                  ? 'bg-jf-sage/30 text-emerald-300 border border-jf-sage/50'
                  : jobStatus.status === 'FAILED'
                  ? 'bg-jf-orange/30 text-orange-200 border border-jf-orange/50'
                  : 'bg-jf-lavender/25 text-jf-lavender border border-jf-lavender/40 animate-pulse'
              }`}
            >
              {jobStatus.status}
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-jf-lavender">
            <div>
              <span className="text-jf-lavender/70 block text-[11px]">Start Time</span>
              <span className="tabular-nums font-mono text-white">{jobStatus.startTime ? new Date(jobStatus.startTime).toLocaleTimeString() : '—'}</span>
            </div>
            <div>
              <span className="text-jf-lavender/70 block text-[11px]">End Time</span>
              <span className="tabular-nums font-mono text-white">{jobStatus.endTime ? new Date(jobStatus.endTime).toLocaleTimeString() : 'In Progress...'}</span>
            </div>
            <div>
              <span className="text-jf-lavender/70 block text-[11px]">Exit Status</span>
              <span className="font-mono text-emerald-300 font-semibold">{jobStatus.exitStatus || 'RUNNING'}</span>
            </div>
            <div>
              <span className="text-jf-lavender/70 block text-[11px]">Scheduling Run ID</span>
              <span className="font-mono tabular-nums text-jf-lavender/90 truncate block" title={jobStatus.schedulingRunId || ''}>
                {jobStatus.schedulingRunId ? jobStatus.schedulingRunId.slice(0, 8) + '...' : 'Pending'}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Schedule Diff Summary */}
      {diffSummary && (
        <div className="bg-jf-cream rounded-2xl border-2 border-jf-border-subtle p-6 shadow-card relative overflow-hidden space-y-5 animate-stagger-2">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-apricot" />
          <div className="flex items-center justify-between pb-3 border-b border-jf-border-subtle pt-0.5">
            <div className="flex items-center gap-2">
              <Layers className="w-5 h-5 text-jf-violet" />
              <div>
                <h3 className="text-sm font-bold text-jf-ink">Schedule Diff & Delta Analysis</h3>
                <p className="text-[11px] text-jf-ink-muted">
                  Comparison between Run #{diffSummary.runId.slice(0, 8)} and Prior Run #{diffSummary.previousRunId?.slice(0, 8) || 'Initial'}
                </p>
              </div>
            </div>
            <span className="px-2.5 py-0.5 rounded-full bg-jf-sage/20 text-jf-ink border border-jf-sage/50 font-bold text-xs flex items-center gap-1 shadow-2xs">
              <CheckCircle2 className="w-3.5 h-3.5 text-jf-sage" /> Pipeline Verified
            </span>
          </div>

          {/* Metric cards */}
          <div className="grid grid-cols-2 sm:grid-cols-6 gap-3 text-center">
            <div className="p-3 bg-white rounded-xl border border-jf-border-subtle shadow-2xs jf-card-interactive hover:shadow-card-hover hover:-translate-y-0.5">
              <span className="text-jf-ink-muted text-[11px] font-medium block">Proposals</span>
              <span className="text-lg font-bold text-jf-ink tabular-nums">{diffSummary.totalProposals}</span>
            </div>
            <div className="p-3 bg-jf-sage/20 rounded-xl border border-jf-sage/60 shadow-2xs jf-card-interactive hover:shadow-card-hover hover:-translate-y-0.5">
              <span className="text-jf-ink font-medium text-[11px] block">New Slots</span>
              <span className="text-lg font-bold text-jf-ink tabular-nums">+{diffSummary.newProposals}</span>
            </div>
            <div className="p-3 bg-jf-apricot/30 rounded-xl border border-jf-apricot/70 shadow-2xs jf-card-interactive hover:shadow-card-hover hover:-translate-y-0.5">
              <span className="text-jf-ink font-medium text-[11px] block">Modified</span>
              <span className="text-lg font-bold text-jf-ink tabular-nums">{diffSummary.modifiedProposals}</span>
            </div>
            <div className="p-3 bg-white rounded-xl border border-jf-border-subtle shadow-2xs jf-card-interactive hover:shadow-card-hover hover:-translate-y-0.5">
              <span className="text-jf-ink-muted text-[11px] font-medium block">Unchanged</span>
              <span className="text-lg font-bold text-jf-ink-muted tabular-nums">{diffSummary.unchangedProposals}</span>
            </div>
            <div className="p-3 bg-jf-lavender/30 rounded-xl border border-jf-lavender/70 shadow-2xs jf-card-interactive hover:shadow-card-hover hover:-translate-y-0.5">
              <span className="text-jf-violet font-semibold text-[11px] block">Reprioritized</span>
              <span className="text-lg font-bold text-jf-violet tabular-nums">{diffSummary.reprioritizedCasesCount}</span>
            </div>
            <div className="p-3 bg-white rounded-xl border border-jf-border-subtle shadow-2xs jf-card-interactive hover:shadow-card-hover hover:-translate-y-0.5">
              <span className="text-jf-ink-muted text-[11px] font-medium block">Prior Proposals</span>
              <span className="text-lg font-bold text-jf-ink-muted tabular-nums">{diffSummary.previousProposals}</span>
            </div>
          </div>

          {/* Assignment changes table */}
          {diffSummary.assignmentChanges && diffSummary.assignmentChanges.length > 0 && (
            <div className="space-y-2 pt-2">
              <h4 className="text-xs font-semibold text-jf-ink uppercase tracking-wider">
                Detailed Assignment Variations
              </h4>
              <div className="max-h-64 overflow-y-auto divide-y divide-jf-border-subtle border border-jf-border-subtle rounded-xl bg-white shadow-2xs">
                {diffSummary.assignmentChanges.map((change, i) => (
                  <div key={i} className="p-3 flex items-start justify-between text-xs hover:bg-jf-canvas/50 transition-colors">
                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2">
                        <span className="font-mono tabular-nums font-bold text-jf-violet">{change.caseNumber}</span>
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider border ${changeBadgeColor(
                            change.changeType
                          )}`}
                        >
                          {change.changeType}
                        </span>
                      </div>
                      <p className="text-[11px] text-jf-ink-muted leading-relaxed">{change.details}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
