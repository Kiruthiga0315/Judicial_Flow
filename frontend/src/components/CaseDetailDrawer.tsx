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
  Edit2
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

  const [isEditing, setIsEditing] = useState<boolean>(false);
  const [editForm, setEditForm] = useState<any>({});


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
    if (score >= 70) return 'bg-[#E65100] text-white border-[#BF360C] font-extrabold shadow-xs';
    if (score >= 45) return 'bg-[#F9D68A] text-[#201F35] border-[#D4A746] font-extrabold shadow-2xs';
    return 'bg-[#D2DE9E] text-[#1E2E08] border-[#8A9E48] font-bold shadow-2xs';
  };

  return (
    <div className="fixed inset-0 z-50 overflow-hidden bg-jf-ink/65 backdrop-blur-sm flex justify-end">
      <div className="w-full max-w-2xl bg-white h-full shadow-2xl flex flex-col border-l-2 border-jf-violet/30 animate-in slide-in-from-right duration-200">
        {/* Header */}
        <div className="bg-[#201F35] px-6 py-5 text-white flex items-center justify-between border-b-2 border-[#434164] border-t-4 border-t-[#E65100] shrink-0 shadow-md">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-[11px] uppercase font-bold px-2.5 py-0.5 rounded-md bg-[#434164] text-[#FCF9EE] border border-[#C4C3E3]/50 tracking-wider shadow-2xs">
                {caseData?.caseType || 'CASE'}
              </span>
              <span className="text-xs text-[#C4C3E3] font-medium tracking-wide">CNR / CIS Reference</span>
            </div>
            <h2 className="text-xl font-bold font-mono tabular-nums text-[#FCDD9D] mt-1.5 tracking-tight">
              {caseData?.caseNumber || 'Loading case...'}
            </h2>
          </div>
          <div className="flex gap-2">
            {role !== 'JUDGE' && (
              <button
                onClick={() => {
                  if (isEditing) {
                    setIsEditing(false);
                  } else {
                    setEditForm({
                      caseNumber: caseData?.caseNumber,
                      caseType: caseData?.caseType,
                      filingDate: caseData?.filingDate,
                      currentStatus: caseData?.currentStatus,
                      priorAdjournments: caseData?.priorAdjournments,
                      linkedCaseId: (caseData as any)?.linkedCaseId || null,
                      assignedJudgeId: (caseData as any)?.assignedJudgeId || null,
                      statutoryDeadline: caseData?.statutoryDeadline || null,
                      litigantContactEmail: caseData?.litigantContactEmail || ''
                    });
                    setIsEditing(true);
                  }
                }}
                className="text-[#C4C3E3] hover:text-white hover:bg-white/10 p-2 rounded-lg transition-colors cursor-pointer"
                title="Edit case"
              >
                <Edit2 className="w-5 h-5" />
              </button>
            )}
            <button

            onClick={onClose}
            className="text-[#C4C3E3] hover:text-white hover:bg-white/10 p-2 rounded-lg transition-colors cursor-pointer"
            title="Close details drawer"
          >
            <X className="w-5 h-5" />
          </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6 bg-[#FAF9FD]">
          {isLoading ? (
            <div className="py-20 text-center space-y-3">
              <div className="w-8 h-8 border-3 border-jf-violet/30 border-t-jf-violet rounded-full animate-spin mx-auto"></div>
              <p className="text-xs text-jf-ink-muted font-medium">Fetching case history and score breakdown...</p>
            </div>
          ) : error ? (
            <div className="p-4 bg-jf-orange/10 border-2 border-jf-orange/30 rounded-xl text-jf-text-orange text-xs font-semibold">
              {error}
            </div>
          ) : (
            <>
              {isEditing && (
                <div className="bg-white rounded-xl p-5 border-2 border-jf-violet shadow-xs mb-6">
                  <h3 className="font-bold text-lg mb-4 text-jf-violet">Edit Case details</h3>
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <label className="block text-xs font-bold text-jf-ink mb-1">Case Type</label>
                      <select value={editForm.caseType} onChange={(e) => setEditForm({...editForm, caseType: e.target.value})} className="w-full px-3 py-2 border border-jf-border-subtle rounded-lg focus:ring-2 focus:ring-jf-violet">
                        <option value="BAIL">BAIL</option>
                        <option value="POCSO">POCSO</option>
                        <option value="MATRIMONIAL">MATRIMONIAL</option>
                        <option value="CIVIL">CIVIL</option>
                        <option value="CRIMINAL_OTHER">CRIMINAL_OTHER</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-jf-ink mb-1">Filing Date</label>
                      <input type="date" value={editForm.filingDate} onChange={(e) => setEditForm({...editForm, filingDate: e.target.value})} className="w-full px-3 py-2 border border-jf-border-subtle rounded-lg focus:ring-2 focus:ring-jf-violet" />
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-jf-ink mb-1">Statutory Deadline</label>
                      <input type="date" value={editForm.statutoryDeadline || ''} onChange={(e) => setEditForm({...editForm, statutoryDeadline: e.target.value || null})} className="w-full px-3 py-2 border border-jf-border-subtle rounded-lg focus:ring-2 focus:ring-jf-violet" />
                    </div>
                    <div>
                      <label className="block text-xs font-bold text-jf-ink mb-1">Litigant Email</label>
                      <input type="email" value={editForm.litigantContactEmail || ''} onChange={(e) => setEditForm({...editForm, litigantContactEmail: e.target.value})} className="w-full px-3 py-2 border border-jf-border-subtle rounded-lg focus:ring-2 focus:ring-jf-violet" />
                    </div>
                  </div>
                  <div className="mt-5 flex gap-3">
                    <button onClick={async () => {
                       try {
                         await api.request(`/api/v1/cases/${caseId}`, { method: 'PUT', body: JSON.stringify(editForm) });
                         setIsEditing(false);
                         loadDetails();
                         if (onHearingUpdated) onHearingUpdated();
                       } catch(e) {
                         alert("Failed to update case");
                       }
                    }} className="px-5 py-2 bg-jf-violet text-white text-sm font-bold rounded-lg shadow-sm hover:bg-jf-violet/90">Save Changes</button>
                    <button onClick={() => setIsEditing(false)} className="px-5 py-2 border-2 border-jf-border-subtle text-jf-ink text-sm font-bold rounded-lg hover:bg-jf-canvas">Cancel</button>
                  </div>
                </div>
              )}
              {/* Case Summary Card */}
              <div className="bg-white rounded-xl p-4.5 border-2 border-jf-border-subtle shadow-xs grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
                <div>
                  <div className="text-jf-ink-muted text-[11px] font-bold uppercase tracking-wider">Current Status</div>
                  <div className="font-bold text-jf-ink text-sm mt-1">{caseData?.currentStatus}</div>
                </div>
                <div>
                  <div className="text-jf-ink-muted text-[11px] font-bold uppercase tracking-wider">Filing Date</div>
                  <div className="font-bold text-jf-ink text-sm mt-1 tabular-nums">
                    {caseData?.filingDate ? new Date(caseData.filingDate).toLocaleDateString('en-IN') : 'N/A'}
                  </div>
                </div>
                <div>
                  <div className="text-jf-ink-muted text-[11px] font-bold uppercase tracking-wider">Prior Adjournments</div>
                  <div className="font-bold text-jf-ink text-sm mt-1 tabular-nums">{caseData?.priorAdjournments} times</div>
                </div>
                <div>
                  <div className="text-jf-ink-muted text-[11px] font-bold uppercase tracking-wider">Litigant Contact</div>
                  <div className="font-bold text-jf-ink text-sm mt-1 truncate" title={caseData?.litigantContactEmail || 'None'}>
                    {caseData?.litigantContactEmail || 'None'}
                  </div>
                </div>
              </div>

              {/* Priority Score Summary */}
              <div className="bg-white rounded-xl border-2 border-jf-border-subtle p-5 shadow-xs">
                <div className="flex items-center justify-between pb-3 border-b-2 border-jf-border-subtle">
                  <div className="flex items-center gap-2.5">
                    <div className="p-2 rounded-lg bg-[#FAF9FE] border-2 border-jf-border-subtle shadow-2xs">
                      <Scale className="w-5 h-5 text-jf-violet" />
                    </div>
                    <div>
                      <h3 className="text-sm font-bold text-jf-ink">Explainable Priority Score</h3>
                      <p className="text-[11px] text-[#5C5A78] font-medium">Multifactor urgency weighted metric</p>
                    </div>
                  </div>
                  {scoreResult && (
                    <div
                      className={`text-base font-extrabold tabular-nums px-3.5 py-1 rounded-full border-2 shadow-xs ${scoreBadgeColor(
                        scoreResult.totalScore
                      )}`}
                    >
                      {scoreResult.totalScore.toFixed(1)} pts
                    </div>
                  )}
                </div>

                {scoreResult?.summary && (
                  <p className="text-xs text-jf-ink font-medium italic bg-[#F4F2FA] p-3 rounded-lg border-2 border-jf-border-subtle mt-3.5 leading-relaxed">
                    "{scoreResult.summary}"
                  </p>
                )}

                {/* Factor Breakdown */}
                <div className="mt-5 space-y-3">
                  <h4 className="text-xs font-bold text-jf-ink uppercase tracking-wider flex items-center justify-between">
                    <span>Factor-by-Factor Breakdown</span>
                    <span className="text-[11px] text-[#5C5A78] font-semibold lowercase">additive point weights</span>
                  </h4>
                  {scoreResult?.factors?.length ? (
                    <div className="space-y-2.5">
                      {scoreResult.factors.map((f, idx) => {
                        const rawDisplay = f.rawValue != null ? Number(f.rawValue).toString() : f.rawMetricValue || '1';
                        const weightDisplay = f.weight != null ? (f.weight < 1 ? `${f.weight} pts/unit` : `${f.weight} pts`) : '';

                        return (
                          <div
                            key={idx}
                            className="p-3.5 rounded-lg border-2 border-jf-border-subtle bg-white hover:bg-[#FAF9FE] transition-colors shadow-2xs"
                          >
                            <div className="flex items-center justify-between text-xs">
                              <span className="font-bold text-jf-ink">{f.factorName}</span>
                              <span className="font-mono tabular-nums font-bold text-jf-violet bg-[#EDEBF7] px-2 py-0.5 rounded border border-[#7B789C]">
                                +{Number(f.contribution).toFixed(2)} pts
                              </span>
                            </div>
                            <div className="flex flex-wrap items-center gap-3 text-[11px] text-[#5C5A78] font-medium mt-1.5">
                              <span>Raw Value: <strong className="text-jf-ink tabular-nums">{rawDisplay}</strong></span>
                              {weightDisplay && <span>Factor Rate: <strong className="text-jf-ink tabular-nums">{weightDisplay}</strong></span>}
                            </div>
                            {f.explanation && (
                              <p className="text-[11px] text-jf-ink mt-2 leading-relaxed bg-[#FAF9FE] p-2.5 rounded border-2 border-jf-border-subtle font-medium">
                                {f.explanation}
                              </p>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p className="text-xs text-[#5C5A78] italic">No score factor breakdown available.</p>
                  )}
                </div>
              </div>

              {/* Scheduled Hearing Status */}
              <div className="bg-white rounded-xl border-2 border-jf-border-subtle p-5 shadow-xs">
                <div className="flex items-center justify-between pb-3 border-b-2 border-jf-border-subtle">
                  <div className="flex items-center gap-2">
                    <Calendar className="w-5 h-5 text-jf-violet" />
                    <div>
                      <h3 className="text-sm font-bold text-jf-ink">Scheduled Hearing</h3>
                      <p className="text-[11px] text-jf-ink-muted font-medium">Current calendar assignment</p>
                    </div>
                  </div>
                  {hearing && role !== 'JUDGE' && onReassignHearing && (
                    <button
                      onClick={() => onReassignHearing(hearing)}
                      className="px-2.5 py-1 text-xs font-semibold bg-jf-apricot/40 text-jf-ink border-2 border-jf-apricot/70 rounded-md hover:bg-jf-apricot/60 transition-colors jf-focus-ring"
                    >
                      Override / Reassign
                    </button>
                  )}
                </div>

                {hearing ? (
                  <div className="mt-3 p-3.5 bg-jf-lavender/20 rounded-lg border-2 border-jf-lavender/40 text-xs space-y-2">
                    <div className="grid grid-cols-2 gap-2">
                      <div>
                        <span className="text-jf-ink-muted font-medium">Presiding Judge:</span>
                        <div className="font-bold text-jf-ink">{hearing.judgeName || 'N/A'}</div>
                      </div>
                      <div>
                        <span className="text-jf-ink-muted font-medium">Courtroom:</span>
                        <div className="font-bold text-jf-ink">{hearing.courtroomName || 'N/A'}</div>
                      </div>
                    </div>
                    <div className="pt-2 border-t border-jf-lavender/40 flex items-center justify-between">
                      <div>
                        <span className="text-jf-ink-muted font-medium">Slot Time:</span>
                        <div className="font-bold text-jf-ink tabular-nums">
                          {new Date(hearing.scheduledTime).toLocaleString('en-IN', { timeZone: 'UTC' })}
                        </div>
                      </div>
                      <div className="text-right">
                        <span className="text-jf-ink-muted font-medium">Estimated Duration:</span>
                        <div className="font-bold text-jf-ink tabular-nums">{hearing.durationMinutes} minutes</div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="mt-3 p-4 bg-jf-canvas rounded-lg text-center text-xs text-jf-ink-muted border-2 border-jf-border-subtle space-y-3">
                    <p className="text-jf-ink-muted font-medium">No active hearing currently committed. Case awaiting proposal acceptance or direct allocation.</p>
                    {role !== 'JUDGE' && (
                      <button
                        onClick={() => setIsScheduleModalOpen(true)}
                        className="inline-flex items-center gap-1.5 px-3.5 py-2 jf-btn-primary rounded-md text-xs font-semibold shadow-xs"
                      >
                        <PlusCircle className="w-4 h-4 text-jf-apricot" />
                        Directly Allocate Courtroom & Schedule
                      </button>
                    )}
                  </div>
                )}
              </div>

              {/* Priority Score History */}
              <div className="bg-white rounded-xl border-2 border-jf-border-subtle p-5 shadow-xs">
                <div className="flex items-center gap-2 pb-3 border-b-2 border-jf-border-subtle">
                  <History className="w-5 h-5 text-jf-violet" />
                  <div>
                    <h3 className="text-sm font-bold text-jf-ink">Score History</h3>
                    <p className="text-[11px] text-jf-ink-muted font-medium">Cumulative record of priority recalculations</p>
                  </div>
                </div>

                {history.length > 0 ? (
                  <div className="mt-3 divide-y-2 divide-jf-border-subtle text-xs">
                    {history.map((h, i) => (
                      <div key={h.persistedScoreId || i} className="py-2.5 flex items-center justify-between">
                        <div>
                          <div className="font-bold text-jf-ink">
                            Score: <span className="text-jf-violet font-mono tabular-nums">{h.totalScore.toFixed(2)}</span>
                          </div>
                          <div className="text-[11px] text-jf-ink-muted tabular-nums font-medium">
                            {new Date(h.computedAt).toLocaleString('en-IN', { timeZone: 'UTC' })}
                          </div>
                        </div>
                        <span className="text-[10px] uppercase font-mono px-2 py-0.5 rounded bg-jf-canvas text-jf-ink font-semibold border border-jf-border-subtle">
                          ID: {h.persistedScoreId ? h.persistedScoreId.slice(0, 8) : 'N/A'}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-jf-ink-muted italic mt-3">No historical score evaluations recorded.</p>
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
