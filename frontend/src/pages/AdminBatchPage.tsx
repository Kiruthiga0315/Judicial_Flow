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
        return 'bg-emerald-50 text-emerald-800 border-emerald-200';
      case 'MODIFIED':
        return 'bg-amber-50 text-amber-800 border-amber-200';
      case 'COMMITTED':
        return 'bg-purple-50 text-purple-800 border-purple-200';
      case 'REMOVED':
        return 'bg-red-50 text-red-800 border-red-200';
      default:
        return 'bg-slate-50 text-slate-700 border-slate-200';
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2">
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">
            Nightly Rescheduling Batch Control
          </h2>
          <span className="px-2.5 py-0.5 rounded-full bg-purple-100 text-purple-800 text-[10px] font-bold uppercase tracking-wider border border-purple-200">
            Admin Only
          </span>
        </div>
        <p className="text-xs text-slate-500 mt-0.5">
          Orchestrates Spring Batch 3-step pipeline: recomputing priority scores, executing constraint-satisfaction solver, and logging schedule diff.
        </p>
      </div>

      {/* Control Card */}
      <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-xs space-y-4">
        <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
          <Sliders className="w-4 h-4 text-blue-900" />
          <span>Execution Parameters</span>
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-lg text-xs">
          <div>
            <label className="block text-slate-600 font-medium mb-1">
              Scheduling Horizon (Business Days)
            </label>
            <input
              type="number"
              min="5"
              max="90"
              value={horizonDays}
              onChange={(e) => setHorizonDays(Number(e.target.value))}
              disabled={isLaunching}
              className="w-full rounded-md border border-slate-300 px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p className="text-[11px] text-slate-400 mt-1">
              Engine allocates non-disposed cases across Monday–Friday court sessions.
            </p>
          </div>
        </div>

        <div className="pt-2 flex items-center gap-3">
          <button
            onClick={handleLaunchJob}
            disabled={isLaunching}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-blue-900 text-white rounded-lg text-xs font-semibold hover:bg-blue-800 disabled:opacity-50 transition-colors shadow-sm"
          >
            {isLaunching ? (
              <>
                <RotateCw className="w-4 h-4 animate-spin text-blue-200" />
                Pipeline Executing & Polling Status...
              </>
            ) : (
              <>
                <Play className="w-4 h-4 text-emerald-400 fill-emerald-400" />
                Trigger Nightly Batch Pipeline
              </>
            )}
          </button>
        </div>

        {error && (
          <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-xs text-red-800 flex items-center gap-2">
            <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
            <span>{error}</span>
          </div>
        )}
      </div>

      {/* Live Pipeline Status */}
      {jobStatus && (
        <div className="bg-slate-900 text-white rounded-xl p-5 shadow-lg space-y-3 animate-in fade-in text-xs">
          <div className="flex items-center justify-between pb-3 border-b border-slate-800">
            <div className="flex items-center gap-2">
              <Clock className="w-4 h-4 text-blue-400" />
              <span className="font-semibold text-sm">Batch Job Execution #{jobStatus.jobExecutionId}</span>
            </div>
            <span
              className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider ${
                jobStatus.status === 'COMPLETED'
                  ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                  : jobStatus.status === 'FAILED'
                  ? 'bg-red-500/20 text-red-400 border border-red-500/40'
                  : 'bg-blue-500/20 text-blue-400 border border-blue-500/40 animate-pulse'
              }`}
            >
              {jobStatus.status}
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-slate-300">
            <div>
              <span className="text-slate-500 block text-[11px]">Start Time</span>
              <span>{jobStatus.startTime ? new Date(jobStatus.startTime).toLocaleTimeString() : '—'}</span>
            </div>
            <div>
              <span className="text-slate-500 block text-[11px]">End Time</span>
              <span>{jobStatus.endTime ? new Date(jobStatus.endTime).toLocaleTimeString() : 'In Progress...'}</span>
            </div>
            <div>
              <span className="text-slate-500 block text-[11px]">Exit Status</span>
              <span className="font-mono text-emerald-400">{jobStatus.exitStatus || 'RUNNING'}</span>
            </div>
            <div>
              <span className="text-slate-500 block text-[11px]">Scheduling Run ID</span>
              <span className="font-mono text-slate-400 truncate block" title={jobStatus.schedulingRunId || ''}>
                {jobStatus.schedulingRunId ? jobStatus.schedulingRunId.slice(0, 8) + '...' : 'Pending'}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Schedule Diff Summary */}
      {diffSummary && (
        <div className="bg-white rounded-xl border border-slate-200 p-6 shadow-xs space-y-5 animate-in fade-in">
          <div className="flex items-center justify-between pb-3 border-b border-slate-100">
            <div className="flex items-center gap-2">
              <Layers className="w-5 h-5 text-blue-900" />
              <div>
                <h3 className="text-sm font-bold text-slate-900">Schedule Diff & Delta Analysis</h3>
                <p className="text-[11px] text-slate-500">
                  Comparison between Run #{diffSummary.runId.slice(0, 8)} and Prior Run #{diffSummary.previousRunId?.slice(0, 8) || 'Initial'}
                </p>
              </div>
            </div>
            <span className="px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-800 border border-emerald-200 font-bold text-xs flex items-center gap-1">
              <CheckCircle2 className="w-3.5 h-3.5" /> Pipeline Verified
            </span>
          </div>

          {/* Metric cards */}
          <div className="grid grid-cols-2 sm:grid-cols-6 gap-3 text-center">
            <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
              <span className="text-slate-400 text-[11px] block">Proposals</span>
              <span className="text-base font-bold text-slate-900">{diffSummary.totalProposals}</span>
            </div>
            <div className="p-3 bg-emerald-50 rounded-lg border border-emerald-200">
              <span className="text-emerald-700 text-[11px] block">New Slots</span>
              <span className="text-base font-bold text-emerald-900">+{diffSummary.newProposals}</span>
            </div>
            <div className="p-3 bg-amber-50 rounded-lg border border-amber-200">
              <span className="text-amber-700 text-[11px] block">Modified</span>
              <span className="text-base font-bold text-amber-900">{diffSummary.modifiedProposals}</span>
            </div>
            <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
              <span className="text-slate-500 text-[11px] block">Unchanged</span>
              <span className="text-base font-bold text-slate-800">{diffSummary.unchangedProposals}</span>
            </div>
            <div className="p-3 bg-purple-50 rounded-lg border border-purple-200">
              <span className="text-purple-700 text-[11px] block">Reprioritized</span>
              <span className="text-base font-bold text-purple-900">{diffSummary.reprioritizedCasesCount}</span>
            </div>
            <div className="p-3 bg-blue-50 rounded-lg border border-blue-200">
              <span className="text-blue-700 text-[11px] block">Prior Proposals</span>
              <span className="text-base font-bold text-blue-900">{diffSummary.previousProposals}</span>
            </div>
          </div>

          {/* Assignment changes table */}
          {diffSummary.assignmentChanges && diffSummary.assignmentChanges.length > 0 && (
            <div className="space-y-2 pt-2">
              <h4 className="text-xs font-semibold text-slate-700 uppercase tracking-wider">
                Detailed Assignment Variations
              </h4>
              <div className="max-h-64 overflow-y-auto divide-y divide-slate-100 border border-slate-200 rounded-lg">
                {diffSummary.assignmentChanges.map((change, i) => (
                  <div key={i} className="p-3 flex items-start justify-between text-xs hover:bg-slate-50">
                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2">
                        <span className="font-mono font-bold text-slate-900">{change.caseNumber}</span>
                        <span
                          className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider border ${changeBadgeColor(
                            change.changeType
                          )}`}
                        >
                          {change.changeType}
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-600 leading-relaxed">{change.details}</p>
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
