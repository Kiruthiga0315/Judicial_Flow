import React, { useState, useEffect } from 'react';
import { Proposal, SchedulingRun } from '../types/api';
import { api } from '../api/client';
import {
  CheckCircle2,
  XCircle,
  AlertTriangle,
  User,
  MapPin,
  RefreshCw,
  Info,
  Calendar,
  Sparkles,
} from 'lucide-react';

interface ProposalsPageProps {
  onSelectCase: (caseId: string) => void;
}

export const ProposalsPage: React.FC<ProposalsPageProps> = ({ onSelectCase }) => {
  const [run, setRun] = useState<SchedulingRun | null>(null);
  const [proposals, setProposals] = useState<Proposal[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isGenerating, setIsGenerating] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'ALL' | 'PROPOSED' | 'APPROVED' | 'REJECTED'>('ALL');

  // Rejection modal state
  const [rejectingProposalId, setRejectingProposalId] = useState<string | null>(null);
  const [rejectionReason, setRejectionReason] = useState<string>('');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  // Per-proposal inline error tracking (e.g., 409 conflict messages)
  const [proposalErrors, setProposalErrors] = useState<Record<string, string>>({});
  const [successNotices, setSuccessNotices] = useState<Record<string, string>>({});

  const fetchProposals = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const latestRun = await api.getLatestSchedulingRun();
      setRun(latestRun);
      setProposals(latestRun.proposals || []);
    } catch (err: any) {
      if (err.status === 404) {
        setError('No completed scheduling run found. Click "Run Optimization Engine" to generate proposals.');
      } else {
        setError(err.message || 'Failed to load scheduling proposals');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleRunOptimization = async () => {
    setIsGenerating(true);
    setError(null);
    try {
      const newRun = await api.triggerSchedulingRun();
      setRun(newRun);
      setProposals(newRun.proposals || []);
    } catch (err: any) {
      setError(err.message || 'Failed to run optimization engine');
    } finally {
      setIsGenerating(false);
    }
  };

  useEffect(() => {
    fetchProposals();
  }, []);

  const handleApprove = async (proposalId: string) => {
    setProposalErrors((prev) => ({ ...prev, [proposalId]: '' }));
    setSuccessNotices((prev) => ({ ...prev, [proposalId]: '' }));
    setIsSubmitting(true);
    try {
      await api.approveProposal(proposalId);
      setSuccessNotices((prev) => ({
        ...prev,
        [proposalId]: 'Proposal approved and committed to official hearing calendar.',
      }));
      // Update local status to APPROVED
      setProposals((prev) =>
        prev.map((p) => (p.proposalId === proposalId ? { ...p, status: 'APPROVED' } : p))
      );
    } catch (err: any) {
      setProposalErrors((prev) => ({
        ...prev,
        [proposalId]: err.status === 409
          ? `Conflict (409): ${err.message}`
          : err.message || 'Approval failed',
      }));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleOpenReject = (proposalId: string) => {
    setRejectingProposalId(proposalId);
    setRejectionReason('');
    setProposalErrors((prev) => ({ ...prev, [proposalId]: '' }));
  };

  const handleConfirmReject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rejectingProposalId) return;

    if (!rejectionReason.trim()) {
      alert('A rejection reason is required for judicial registry logs.');
      return;
    }

    setIsSubmitting(true);
    try {
      await api.rejectProposal(rejectingProposalId, rejectionReason.trim());
      setSuccessNotices((prev) => ({
        ...prev,
        [rejectingProposalId]: `Proposal rejected: "${rejectionReason.trim()}"`,
      }));
      setProposals((prev) =>
        prev.map((p) =>
          p.proposalId === rejectingProposalId
            ? { ...p, status: 'REJECTED', rejectionReason: rejectionReason.trim() }
            : p
        )
      );
      setRejectingProposalId(null);
    } catch (err: any) {
      setProposalErrors((prev) => ({
        ...prev,
        [rejectingProposalId]: err.message || 'Rejection failed',
      }));
      setRejectingProposalId(null);
    } finally {
      setIsSubmitting(false);
    }
  };

  const scoreBadgeColor = (score?: number | null) => {
    if (score == null) return 'bg-[#EEEBF5] text-[#5C5A78] border-2 border-jf-border-subtle';
    if (score >= 70) return 'bg-[#E65100] text-white border-2 border-[#BF360C] font-extrabold shadow-xs';
    if (score >= 45) return 'bg-[#F9D68A] text-[#201F35] border-2 border-[#D4A746] font-extrabold shadow-2xs';
    return 'bg-[#D2DE9E] text-[#1E2E08] border-2 border-[#8A9E48] font-bold shadow-2xs';
  };

  const statusBadge = (status: string) => {
    switch (status) {
      case 'PROPOSED':
        return 'bg-jf-lavender/40 text-jf-violet border-2 border-jf-violet/50 font-bold';
      case 'APPROVED':
        return 'bg-jf-sage/35 text-jf-ink border-2 border-jf-sage font-bold';
      case 'REJECTED':
        return 'bg-jf-orange/20 text-jf-text-orange border-2 border-jf-orange/60 font-bold';
      case 'SUPERSEDED':
        return 'bg-jf-cream-darker text-jf-ink-muted border-2 border-jf-border-subtle font-medium';
      default:
        return 'bg-jf-cream-darker text-jf-ink-muted border-2 border-jf-border-subtle font-medium';
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-bold text-jf-ink tracking-tight flex items-center gap-2">
            <span>Proposed Hearing Allocations</span>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-jf-violet/10 text-jf-violet border-2 border-jf-violet/30 font-bold">
              AI Optimization
            </span>
          </h2>
          <p className="text-xs text-jf-ink-muted mt-0.5">
            Latest optimization run generated by the weighted constraint-satisfaction engine. Review explainable rationale before approving.
          </p>
        </div>

        <div className="flex items-center gap-2.5 self-start sm:self-auto">
          <button
            onClick={fetchProposals}
            disabled={isLoading || isGenerating}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-white border-2 border-jf-border-subtle hover:border-jf-violet rounded-lg text-xs font-bold text-jf-ink hover:text-jf-violet shadow-2xs jf-focus-ring transition-all cursor-pointer"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-jf-violet' : 'text-jf-violet'}`} />
            Refresh
          </button>
          <button
            onClick={handleRunOptimization}
            disabled={isLoading || isGenerating}
            className="inline-flex items-center gap-1.5 px-4 py-2 border-2 border-jf-violet jf-btn-primary rounded-lg text-xs font-bold hover:shadow-glow-btn hover:-translate-y-0.5 active:translate-y-0 transition-all shadow-xs disabled:opacity-50 jf-focus-ring cursor-pointer"
          >
            <Sparkles className={`w-3.5 h-3.5 ${isGenerating ? 'animate-spin text-jf-apricot' : 'text-jf-apricot'}`} />
            {isGenerating ? 'Optimizing Schedule...' : 'Run Optimization Engine'}
          </button>
        </div>
      </div>

      {/* Run Summary Banner */}
      {run && (
        <div className="bg-jf-cream rounded-2xl border-2 border-[#201F35] p-5.5 shadow-card jf-card-interactive hover:shadow-card-hover relative overflow-hidden flex flex-wrap items-center justify-between gap-4 text-xs animate-stagger-1">
          <div className="absolute top-0 inset-x-0 h-1.5 bg-gradient-to-r from-jf-violet via-jf-orange to-jf-apricot" />
          <div className="flex items-center gap-5 pt-0.5">
            <div>
              <span className="text-jf-ink-muted text-[11px] font-bold uppercase tracking-wider block">Run Status</span>
              <span className="font-extrabold text-jf-ink uppercase tracking-wider px-3 py-1 rounded-full bg-jf-sage/35 border-2 border-[#201F35] inline-block mt-1 text-[11px] shadow-2xs">
                {run.status}
              </span>
            </div>
            <div className="border-l-2 border-[#201F35]/30 pl-5">
              <span className="text-jf-ink-muted text-[11px] font-bold uppercase tracking-wider block">Execution Time</span>
              <span className="font-bold text-jf-ink block mt-1 tabular-nums text-sm">
                {new Date(run.triggeredAt).toLocaleString('en-IN', { timeZone: 'UTC' })}
              </span>
            </div>
          </div>

          <div className="flex items-center divide-x-2 divide-[#201F35]/30 bg-white/90 p-2 rounded-xl border-2 border-[#201F35]/70 shadow-2xs">
            <div className="text-center px-4">
              <span className="text-jf-ink-muted text-[10px] font-bold uppercase tracking-wider block">Cases Input</span>
              <span className="text-lg font-extrabold text-jf-ink tabular-nums">{run.totalCasesInput}</span>
            </div>
            <div className="text-center px-4">
              <span className="text-jf-ink-muted text-[10px] font-bold uppercase tracking-wider block">Allocated</span>
              <span className="text-lg font-extrabold text-jf-violet tabular-nums">{run.totalAssigned}</span>
            </div>
            <div className="text-center px-4">
              <span className="text-jf-ink-muted text-[10px] font-bold uppercase tracking-wider block">Unschedulable</span>
              <span className="text-lg font-extrabold text-jf-text-orange tabular-nums">{run.totalUnschedulable}</span>
            </div>
            <div className="text-center px-4">
              <span className="text-jf-ink-muted text-[10px] font-bold uppercase tracking-wider block">Horizon</span>
              <span className="text-lg font-extrabold text-jf-ink tabular-nums">{run.horizonDays} days</span>
            </div>
          </div>
        </div>
      )}

      {/* Tabs / Filter bar */}
      {proposals.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 bg-white/90 p-1.5 rounded-xl border-2 border-[#201F35] text-xs shadow-2xs">
          <button
            onClick={() => setActiveTab('ALL')}
            className={`px-3.5 py-1.5 rounded-lg transition-all duration-150 jf-focus-ring cursor-pointer ${
              activeTab === 'ALL'
                ? 'bg-jf-violet text-jf-cream font-bold shadow-xs border-2 border-jf-violet -translate-y-0.5'
                : 'text-jf-ink font-semibold hover:text-jf-violet hover:bg-jf-lavender/30 border-2 border-transparent'
            }`}
          >
            All Proposals ({proposals.length})
          </button>
          <button
            onClick={() => setActiveTab('PROPOSED')}
            className={`px-3.5 py-1.5 rounded-lg transition-all duration-150 jf-focus-ring cursor-pointer ${
              activeTab === 'PROPOSED'
                ? 'bg-jf-violet text-jf-cream font-bold shadow-xs border-2 border-jf-violet -translate-y-0.5'
                : 'text-jf-ink font-semibold hover:text-jf-violet hover:bg-jf-lavender/30 border-2 border-transparent'
            }`}
          >
            Pending Review ({proposals.filter((p) => p.status === 'PROPOSED').length})
          </button>
          <button
            onClick={() => setActiveTab('APPROVED')}
            className={`px-3.5 py-1.5 rounded-lg transition-all duration-150 jf-focus-ring cursor-pointer ${
              activeTab === 'APPROVED'
                ? 'bg-jf-violet text-jf-cream font-bold shadow-xs border-2 border-jf-violet -translate-y-0.5'
                : 'text-jf-ink font-semibold hover:text-jf-violet hover:bg-jf-lavender/30 border-2 border-transparent'
            }`}
          >
            Approved ({proposals.filter((p) => p.status === 'APPROVED').length})
          </button>
          <button
            onClick={() => setActiveTab('REJECTED')}
            className={`px-3.5 py-1.5 rounded-lg transition-all duration-150 jf-focus-ring cursor-pointer ${
              activeTab === 'REJECTED'
                ? 'bg-jf-violet text-jf-cream font-bold shadow-xs border-2 border-jf-violet -translate-y-0.5'
                : 'text-jf-ink font-semibold hover:text-jf-violet hover:bg-jf-lavender/30 border-2 border-transparent'
            }`}
          >
            Rejected ({proposals.filter((p) => p.status === 'REJECTED').length})
          </button>
        </div>
      )}

      {/* Proposals List */}
      <div className="space-y-3">
        {isLoading || isGenerating ? (
          <div className="py-20 text-center text-jf-ink-muted bg-jf-cream rounded-xl border border-jf-border-subtle text-xs">
            <div className="w-6 h-6 border-2 border-jf-violet border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
            {isGenerating ? 'Running weighted constraint optimization across filed cases...' : 'Loading latest scheduling proposals...'}
          </div>
        ) : error ? (
          <div className="p-8 text-center bg-jf-cream rounded-xl border border-jf-border-subtle text-xs space-y-3">
            <Info className="w-6 h-6 text-jf-ink-muted mx-auto" />
            <p className="text-jf-ink font-medium">{error}</p>
            <button
              onClick={handleRunOptimization}
              className="inline-flex items-center gap-1.5 px-4 py-2 jf-btn-primary rounded-lg text-xs font-semibold shadow-xs"
            >
              <Sparkles className="w-3.5 h-3.5" />
              Run Optimization Engine Now
            </button>
          </div>
        ) : proposals.length === 0 ? (
          <div className="p-12 text-center bg-jf-cream rounded-xl border border-jf-border-subtle text-xs space-y-3">
            <p className="text-jf-ink-muted">No proposals available in the latest run.</p>
            <button
              onClick={handleRunOptimization}
              className="inline-flex items-center gap-1.5 px-4 py-2 jf-btn-primary rounded-lg text-xs font-semibold shadow-xs"
            >
              <Sparkles className="w-3.5 h-3.5" />
              Run Optimization Engine Now
            </button>
          </div>
        ) : (
          proposals
            .filter((p) => activeTab === 'ALL' || p.status === activeTab)
            .map((p) => {
            const hasError = !!proposalErrors[p.proposalId];
            const hasSuccess = !!successNotices[p.proposalId];
            const timeString = p.proposedTime || p.slotStartTime;
            const parsedDate = timeString ? new Date(timeString) : null;
            const isValidDate = parsedDate && !isNaN(parsedDate.getTime());
            const priorityScore = p.casePriorityScore ?? p.scoreAtScheduling;
            const explanationText = p.decisionLog?.explanation || p.explanation;

            return (
              <div
                key={p.proposalId}
                className="bg-jf-cream rounded-2xl border-2 border-[#201F35] shadow-card jf-card-interactive hover:shadow-card-hover transition-all p-6 space-y-4"
              >
                {/* Proposal Inline Alerts */}
                {hasError && (
                  <div className="p-3.5 bg-jf-orange/10 border-2 border-jf-orange/40 rounded-xl flex items-start gap-2.5 text-jf-text-orange text-xs animate-in fade-in">
                    <AlertTriangle className="w-4 h-4 text-jf-text-orange shrink-0 mt-0.5" />
                    <div>
                      <p className="font-bold">Assignment Conflict</p>
                      <p className="mt-0.5 leading-relaxed">{proposalErrors[p.proposalId]}</p>
                    </div>
                  </div>
                )}

                {hasSuccess && (
                  <div className="p-3.5 bg-jf-sage/20 border-2 border-jf-sage/60 rounded-xl flex items-center gap-2.5 text-jf-ink text-xs animate-in fade-in">
                    <CheckCircle2 className="w-4 h-4 text-jf-sage shrink-0" />
                    <span className="font-semibold">{successNotices[p.proposalId]}</span>
                  </div>
                )}

                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3.5 border-b-2 border-[#201F35]/30">
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => onSelectCase(p.caseId)}
                      className="font-mono tabular-nums text-base font-bold text-jf-violet bg-jf-lavender/40 px-3 py-1 rounded-md border-2 border-[#201F35] shadow-2xs hover:bg-jf-violet hover:text-white transition-all flex items-center gap-1 cursor-pointer"
                      title="View case breakdown in drawer"
                    >
                      {p.caseNumber}
                    </button>
                    <span
                      className={`text-[10px] uppercase font-bold tracking-wider px-2.5 py-0.5 rounded-full border-2 shadow-2xs ${statusBadge(
                        p.status
                      )}`}
                    >
                      {p.status}
                    </span>
                  </div>

                  <div className="flex items-center gap-2.5">
                    <span className="text-xs text-jf-ink font-bold">Urgency Score:</span>
                    <span
                      className={`inline-block px-2.5 py-1 rounded-md text-xs font-mono font-bold border-2 tabular-nums tracking-tight shadow-xs ${scoreBadgeColor(
                        priorityScore
                      )}`}
                    >
                      {priorityScore !== undefined && priorityScore !== null
                        ? Number(priorityScore).toFixed(1)
                        : 'N/A'}
                    </span>
                  </div>
                </div>

                {/* Assignment specifics */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5 text-xs bg-white p-4 rounded-xl border-2 border-[#201F35]/70 shadow-2xs">
                  <div className="flex items-center gap-3 text-jf-ink">
                    <div className="w-9 h-9 rounded-lg bg-jf-lavender/40 border-2 border-[#201F35]/60 flex items-center justify-center text-jf-violet shrink-0 shadow-2xs">
                      <User className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="text-[11px] text-jf-ink-muted block font-bold uppercase tracking-wider">Judge</span>
                      <strong className="text-jf-ink font-bold text-xs">{p.judgeName || 'Unassigned'}</strong>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 text-jf-ink">
                    <div className="w-9 h-9 rounded-lg bg-jf-apricot/40 border-2 border-jf-apricot/80 flex items-center justify-center text-jf-ink shrink-0 shadow-2xs">
                      <MapPin className="w-4 h-4 text-jf-ink" />
                    </div>
                    <div>
                      <span className="text-[11px] text-jf-ink-muted block font-bold uppercase tracking-wider">Courtroom</span>
                      <strong className="text-jf-ink font-bold text-xs">{p.courtroomName || 'Unassigned'}</strong>
                    </div>
                  </div>

                  <div className="flex items-center gap-3 text-jf-ink">
                    <div className="w-9 h-9 rounded-lg bg-jf-sage/35 border-2 border-jf-sage/80 flex items-center justify-center text-jf-ink shrink-0 shadow-2xs">
                      <Calendar className="w-4 h-4 text-jf-ink" />
                    </div>
                    <div>
                      <span className="text-[11px] text-jf-ink-muted block font-bold uppercase tracking-wider">Proposed Slot</span>
                      <strong className="text-jf-ink font-bold text-xs tabular-nums">
                        {isValidDate
                          ? `${parsedDate.toLocaleDateString('en-IN', {
                              weekday: 'short',
                              day: 'numeric',
                              month: 'short',
                              year: 'numeric',
                            })} at ${parsedDate.toLocaleTimeString('en-IN', {
                              hour: '2-digit',
                              minute: '2-digit',
                            })} (${p.durationMinutes}m)`
                          : `Unscheduled (${p.durationMinutes}m)`}
                      </strong>
                    </div>
                  </div>
                </div>

                {/* Explainable Decision Log */}
                {explanationText && (
                  <div className="text-xs text-jf-ink bg-[#FAF9FD] p-4 rounded-xl border-2 border-[#201F35]/70 leading-relaxed space-y-2.5 shadow-2xs">
                    <div>
                      <span className="font-extrabold text-jf-ink">Optimization Rationale: </span>
                      <span className="font-medium text-jf-ink">{explanationText}</span>
                    </div>

                    {p.decisionLog?.constraintsSatisfied && p.decisionLog.constraintsSatisfied.length > 0 && (
                      <div className="flex flex-wrap items-center gap-2 pt-1">
                        <span className="text-[11px] text-jf-ink font-bold uppercase tracking-wider">Constraints verified:</span>
                        {p.decisionLog.constraintsSatisfied.map((c, i) => (
                          <span
                            key={i}
                            className="inline-flex items-center text-[10px] bg-white border-2 border-[#201F35]/60 text-jf-ink px-2.5 py-0.5 rounded-md font-bold shadow-2xs"
                          >
                            ✓ {c}
                          </span>
                        ))}
                      </div>
                    )}

                    {p.decisionLog?.runnerUpJudgeName && (
                      <div className="text-[11px] text-jf-ink pt-2 border-t-2 border-[#201F35]/25">
                        <span className="font-bold text-jf-ink">Runner-up slot: </span>
                        {p.decisionLog.runnerUpJudgeName} in {p.decisionLog.runnerUpCourtroomName || 'Courtroom'}
                        {p.decisionLog.runnerUpRejectionReason && (
                          <span className="text-jf-ink-muted block mt-0.5 font-medium">
                            Reason chosen over runner-up: {p.decisionLog.runnerUpRejectionReason}
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                )}

                {/* Rejection reason if rejected */}
                {p.status === 'REJECTED' && p.rejectionReason && (
                  <div className="text-xs text-jf-text-orange bg-jf-orange/10 p-3 rounded-xl border-2 border-jf-orange/40">
                    <strong>Rejection Justification: </strong>
                    {p.rejectionReason}
                  </div>
                )}

                {/* Action buttons (only enabled if PROPOSED) */}
                {p.status === 'PROPOSED' && (
                  <div className="pt-2 flex justify-end gap-2.5">
                    <button
                      type="button"
                      onClick={() => handleOpenReject(p.proposalId)}
                      disabled={isSubmitting}
                      className="px-4 py-2 border-2 border-jf-text-orange bg-white hover:bg-jf-orange hover:text-white rounded-lg text-xs font-bold text-jf-text-orange hover:-translate-y-0.5 active:translate-y-0 transition-all flex items-center gap-1.5 jf-focus-ring shadow-xs cursor-pointer"
                    >
                      <XCircle className="w-3.5 h-3.5" />
                      Reject Proposal
                    </button>
                    <button
                      type="button"
                      onClick={() => handleApprove(p.proposalId)}
                      disabled={isSubmitting}
                      className="px-4 py-2 border-2 border-jf-violet jf-btn-primary rounded-lg text-xs font-bold hover:shadow-glow-btn hover:-translate-y-0.5 active:translate-y-0 transition-all shadow-xs flex items-center gap-1.5 jf-focus-ring cursor-pointer"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Approve & Commit
                    </button>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* Reject Modal */}
      {rejectingProposalId && (
        <div className="fixed inset-0 z-50 bg-jf-ink/65 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-jf-cream rounded-2xl shadow-2xl max-w-md w-full border-2 border-jf-border-subtle overflow-hidden animate-in fade-in">
            <div className="bg-[#201F35] px-6 py-5 text-white border-b-2 border-[#434164] border-t-4 border-t-[#E65100]">
              <h3 className="text-base font-bold">Reject Proposed Allocation</h3>
              <p className="text-xs text-[#C4C3E3] mt-0.5">
                Record mandatory justification for the judicial audit log.
              </p>
            </div>
            <form onSubmit={handleConfirmReject} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-bold text-jf-ink mb-1.5 uppercase tracking-wider text-[11px]">
                  Reason for Rejection *
                </label>
                <textarea
                  required
                  rows={3}
                  value={rejectionReason}
                  onChange={(e) => setRejectionReason(e.target.value)}
                  placeholder="e.g. Counsel not available on date; Bench specialization mismatch; Witness attendance issue"
                  className="w-full text-xs rounded-xl border-2 border-jf-border-subtle bg-white p-3 focus:outline-none jf-focus-ring text-jf-ink shadow-2xs"
                />
              </div>

              <div className="flex justify-end gap-2.5 pt-2">
                <button
                  type="button"
                  onClick={() => setRejectingProposalId(null)}
                  disabled={isSubmitting}
                  className="px-4 py-2 border-2 border-jf-border-subtle rounded-lg text-xs font-bold text-jf-ink hover:bg-jf-cream-darker transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-4.5 py-2 bg-[#E65100] text-white rounded-lg text-xs font-bold hover:bg-[#BF360C] border-2 border-[#BF360C] shadow-xs transition-colors disabled:opacity-50 cursor-pointer"
                >
                  Confirm Rejection
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
