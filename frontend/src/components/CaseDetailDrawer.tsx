import React, { useState, useEffect, useCallback } from 'react';
import { Case, PriorityScoreResult, Hearing } from '../types/api';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { ManualScheduleModal } from './ManualScheduleModal';
import {
  X,
  Calendar,
  Scale,
  History,
  PlusCircle,
} from 'lucide-react';

interface CaseDetailDrawerProps {
  caseId: string;
  onClose: () => void;
  onReassignHearing?: (hearing: Hearing) => void;
  onHearingUpdated?: () => void;
}

export const CaseDetailDrawer: React.FC<CaseDetailDrawerProps> = ({
  caseId,
  onClose,
  onReassignHearing,
  onHearingUpdated,
}) => {
  const { role } = useAuth();
  const [caseData, setCaseData] = useState<Case | null>(null);
  const [scoreResult, setScoreResult] = useState<PriorityScoreResult | null>(null);
  const [history, setHistory] = useState<PriorityScoreResult[]>([]);
  const [hearing, setHearing] = useState<Hearing | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isScheduleModalOpen, setIsScheduleModalOpen] = useState<boolean>(false);

  const loadDetails = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [c, s, hList, currentHearing] = await Promise.all([
        api.getCase(caseId),
        api.getCaseScore(caseId).catch(() => null),
        api.getCaseScoreHistory(caseId).catch(() => []),
        api.getHearingForCase(caseId).catch(() => null),
      ]);
      setCaseData(c);
      setScoreResult(s);
      setHistory(hList || []);
      setHearing(currentHearing);
    } catch (err: any) {
      setError(err.message || 'Failed to load case details');
    } finally {
      setIsLoading(false);
    }
  }, [caseId]);

  useEffect(() => {
    loadDetails();
  }, [loadDetails]);

  const scoreBadgeColor = (score: number) => {
    if (score >= 70) return 'bg-red-100 text-red-800 border-red-300';
    if (score >= 45) return 'bg-amber-100 text-amber-800 border-amber-300';
    return 'bg-emerald-100 text-emerald-800 border-emerald-300';
  };

  return (
    <div className="fixed inset-0 z-40 overflow-hidden bg-slate-900/40 backdrop-blur-xs flex justify-end">
      <div className="w-full max-w-2xl bg-white h-full shadow-2xl flex flex-col border-l border-slate-200 animate-in slide-in-from-right duration-200">
        {/* Header */}
        <div className="bg-slate-900 px-6 py-4 text-white flex items-center justify-between border-b border-slate-800">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs uppercase font-bold px-2 py-0.5 rounded bg-blue-800 text-blue-200 border border-blue-700">
                {caseData?.caseType || 'CASE'}
              </span>
              <span className="text-xs text-slate-400">CNR / CIS Reference</span>
            </div>
            <h2 className="text-lg font-bold font-mono text-amber-300 mt-1">
              {caseData?.caseNumber || 'Loading case...'}
            </h2>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1 rounded-md transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {isLoading ? (
            <div className="py-20 text-center space-y-3">
              <div className="w-8 h-8 border-3 border-blue-900/30 border-t-blue-900 rounded-full animate-spin mx-auto"></div>
              <p className="text-xs text-slate-500 font-medium">Fetching case history and score breakdown...</p>
            </div>
          ) : error ? (
            <div className="p-4 bg-red-50 border border-red-200 rounded-lg text-red-800 text-xs">
              {error}
            </div>
          ) : (
            <>
              {/* Case Summary Card */}
              <div className="bg-slate-50 rounded-xl p-4 border border-slate-200 grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
                <div>
                  <div className="text-slate-400 text-[11px]">Current Status</div>
                  <div className="font-semibold text-slate-900 mt-0.5">{caseData?.currentStatus}</div>
                </div>
                <div>
                  <div className="text-slate-400 text-[11px]">Filing Date</div>
                  <div className="font-semibold text-slate-900 mt-0.5">
                    {caseData?.filingDate ? new Date(caseData.filingDate).toLocaleDateString('en-IN') : 'N/A'}
                  </div>
                </div>
                <div>
                  <div className="text-slate-400 text-[11px]">Prior Adjournments</div>
                  <div className="font-semibold text-slate-900 mt-0.5">{caseData?.priorAdjournments} times</div>
                </div>
                <div>
                  <div className="text-slate-400 text-[11px]">Litigant Contact</div>
                  <div className="font-semibold text-slate-900 mt-0.5 truncate" title={caseData?.litigantContactEmail || 'None'}>
                    {caseData?.litigantContactEmail || 'None'}
                  </div>
                </div>
              </div>

              {/* Priority Score Summary */}
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs">
                <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                  <div className="flex items-center gap-2">
                    <Scale className="w-5 h-5 text-blue-900" />
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">Explainable Priority Score</h3>
                      <p className="text-[11px] text-slate-500">Multifactor urgency weighted metric</p>
                    </div>
                  </div>
                  {scoreResult && (
                    <div
                      className={`text-base font-extrabold px-3 py-1 rounded-full border shadow-xs ${scoreBadgeColor(
                        scoreResult.totalScore
                      )}`}
                    >
                      {scoreResult.totalScore.toFixed(1)} pts
                    </div>
                  )}
                </div>

                {scoreResult?.summary && (
                  <p className="text-xs text-slate-600 italic bg-blue-50/60 p-2.5 rounded-lg border border-blue-100/60 mt-3">
                    "{scoreResult.summary}"
                  </p>
                )}

                {/* Factor Breakdown */}
                <div className="mt-4 space-y-2.5">
                  <h4 className="text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Factor-by-Factor Breakdown
                  </h4>
                  {scoreResult?.factors?.length ? (
                    <div className="space-y-2">
                      {scoreResult.factors.map((f, idx) => {
                        const rawDisplay = f.rawValue != null ? Number(f.rawValue).toString() : f.rawMetricValue || '1';
                        const weightDisplay = f.weight != null ? (f.weight < 1 ? `${f.weight} pts/unit` : `${f.weight} pts`) : '';

                        return (
                          <div
                            key={idx}
                            className="p-3 rounded-lg border border-slate-200/80 bg-slate-50/50 hover:bg-slate-50 transition-colors"
                          >
                            <div className="flex items-center justify-between text-xs">
                              <span className="font-semibold text-slate-800">{f.factorName}</span>
                              <span className="font-mono font-bold text-blue-900">
                                +{Number(f.contribution).toFixed(2)} pts
                              </span>
                            </div>
                            <div className="flex flex-wrap items-center gap-3 text-[11px] text-slate-500 mt-1">
                              <span>Raw Value: <strong className="text-slate-700">{rawDisplay}</strong></span>
                              {weightDisplay && <span>Factor Rate: <strong className="text-slate-700">{weightDisplay}</strong></span>}
                            </div>
                            {f.explanation && (
                              <p className="text-[11px] text-slate-600 mt-1.5 leading-relaxed bg-white/80 p-1.5 rounded border border-slate-100">
                                {f.explanation}
                              </p>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p className="text-xs text-slate-400 italic">No score factor breakdown available.</p>
                  )}
                </div>
              </div>

              {/* Scheduled Hearing Status */}
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs">
                <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                  <div className="flex items-center gap-2">
                    <Calendar className="w-5 h-5 text-indigo-900" />
                    <div>
                      <h3 className="text-sm font-bold text-slate-900">Scheduled Hearing</h3>
                      <p className="text-[11px] text-slate-500">Current calendar assignment</p>
                    </div>
                  </div>
                  {hearing && role !== 'JUDGE' && onReassignHearing && (
                    <button
                      onClick={() => onReassignHearing(hearing)}
                      className="px-2.5 py-1 text-xs font-medium bg-amber-50 text-amber-900 border border-amber-300 rounded hover:bg-amber-100 transition-colors"
                    >
                      Override / Reassign
                    </button>
                  )}
                </div>

                {hearing ? (
                  <div className="mt-3 p-3.5 bg-indigo-50/40 rounded-lg border border-indigo-100 text-xs space-y-2">
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <span className="text-slate-500">Presiding Judge:</span>
                        <div className="font-semibold text-slate-900">{hearing.judgeName || 'N/A'}</div>
                      </div>
                      <div>
                        <span className="text-slate-500">Courtroom:</span>
                        <div className="font-semibold text-slate-900">{hearing.courtroomName || 'N/A'}</div>
                      </div>
                    </div>
                    <div className="pt-2 border-t border-indigo-100/60 flex items-center justify-between">
                      <div>
                        <span className="text-slate-500">Slot Time:</span>
                        <div className="font-semibold text-indigo-950">
                          {new Date(hearing.scheduledTime).toLocaleString('en-IN', { timeZone: 'UTC' })}
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="text-slate-500">Estimated Duration:</span>
                        <div className="font-semibold text-slate-900">{hearing.durationMinutes} minutes</div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="mt-3 p-4 bg-slate-50 rounded-lg text-center text-xs text-slate-600 border border-slate-200 space-y-3">
                    <p className="text-slate-500">No active hearing currently committed. Case awaiting proposal acceptance or direct allocation.</p>
                    {role !== 'JUDGE' && (
                      <button
                        onClick={() => setIsScheduleModalOpen(true)}
                        className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-blue-900 text-white rounded-md text-xs font-medium hover:bg-blue-800 shadow-xs transition-colors"
                      >
                        <PlusCircle className="w-4 h-4 text-amber-300" />
                        Directly Allocate Courtroom & Schedule
                      </button>
                    )}
                  </div>
                )}
              </div>

              {/* Priority Score History */}
              <div className="bg-white rounded-xl border border-slate-200 p-5 shadow-xs">
                <div className="flex items-center gap-2 pb-3 border-b border-slate-100">
                  <History className="w-5 h-5 text-slate-600" />
                  <div>
                    <h3 className="text-sm font-bold text-slate-900">Score History</h3>
                    <p className="text-[11px] text-slate-500">Cumulative record of priority recalculations</p>
                  </div>
                </div>

                {history.length > 0 ? (
                  <div className="mt-3 divide-y divide-slate-100 text-xs">
                    {history.map((h, i) => (
                      <div key={h.persistedScoreId || i} className="py-2.5 flex items-center justify-between">
                        <div>
                          <div className="font-semibold text-slate-800">
                            Score: <span className="text-blue-900 font-mono">{h.totalScore.toFixed(2)}</span>
                          </div>
                          <div className="text-[11px] text-slate-500">
                            {new Date(h.computedAt).toLocaleString('en-IN', { timeZone: 'UTC' })}
                          </div>
                        </div>
                        <span className="text-[10px] uppercase font-mono px-2 py-0.5 rounded bg-slate-100 text-slate-600">
                          ID: {h.persistedScoreId ? h.persistedScoreId.slice(0, 8) : 'N/A'}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400 italic mt-3">No historical score evaluations recorded.</p>
                )}
              </div>
            </>
          )}
        </div>

        {/* Direct Schedule Modal */}
        {isScheduleModalOpen && caseData && (
          <ManualScheduleModal
            caseId={caseData.id}
            caseNumber={caseData.caseNumber}
            caseType={caseData.caseType}
            initialJudgeId={caseData.assignedJudge?.id}
            initialCourtroomId={caseData.assignedCourtroom?.id}
            onClose={() => setIsScheduleModalOpen(false)}
            onSuccess={() => {
              loadDetails();
              if (onHearingUpdated) {
                onHearingUpdated();
              }
            }}
          />
        )}
      </div>
    </div>
  );
};
