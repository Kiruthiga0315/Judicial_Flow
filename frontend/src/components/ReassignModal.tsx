import React, { useState, useEffect } from 'react';
import { Hearing, Judge, Courtroom } from '../types/api';
import { api } from '../api/client';
import { AlertCircle, Calendar, Clock, MapPin, User, X, CheckCircle2 } from 'lucide-react';

interface ReassignModalProps {
  hearing: Hearing;
  onClose: () => void;
  onSuccess: () => void;
}

export const ReassignModal: React.FC<ReassignModalProps> = ({ hearing, onClose, onSuccess }) => {
  const [judges, setJudges] = useState<Judge[]>([]);
  const [courtrooms, setCourtrooms] = useState<Courtroom[]>([]);
  const [judgeId, setJudgeId] = useState<string>(hearing.judgeId || '');
  const [courtroomId, setCourtroomId] = useState<string>(hearing.courtroomId || '');
  const [scheduledTime, setScheduledTime] = useState<string>(
    hearing.scheduledTime ? hearing.scheduledTime.slice(0, 16) : ''
  );
  const [durationMinutes, setDurationMinutes] = useState<number>(hearing.durationMinutes || 60);
  const [reason, setReason] = useState<string>('');
  const [litigantMessage, setLitigantMessage] = useState<string>('');

  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [conflictError, setConflictError] = useState<string | null>(null);

  useEffect(() => {
    const fetchMasterData = async () => {
      try {
        const [judgeRes, crRes] = await Promise.all([
          api.listJudges(),
          api.listCourtrooms(),
        ]);
        setJudges(judgeRes.content || []);
        setCourtrooms(crRes.content || []);
      } catch (err: any) {
        console.error('Failed to load master data', err);
      }
    };
    fetchMasterData();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setConflictError(null);

    if (!reason.trim()) {
      setConflictError('A detailed administrative justification is required for judicial audit logs.');
      return;
    }

    setIsLoading(true);
    try {
      await api.reassignHearing(hearing.id, {
        judgeId,
        courtroomId,
        scheduledTime: new Date(scheduledTime).toISOString().slice(0, 19),
        durationMinutes: Number(durationMinutes),
        reason: reason.trim(),
        litigantMessage: litigantMessage.trim() || undefined,
      });
      onSuccess();
      onClose();
    } catch (err: any) {
      if (err.status === 409) {
        setConflictError(`Schedule Conflict (409): ${err.message}`);
      } else {
        setConflictError(err.message || 'Failed to reassign hearing');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-jf-ink/65 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full border-2 border-jf-border-subtle overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="bg-[#201F35] px-6 py-5 flex items-center justify-between text-white border-b-2 border-[#434164] border-t-4 border-t-[#E65100]">
          <div>
            <h3 className="text-base font-bold">Manual Hearing Reassignment</h3>
            <p className="text-xs text-[#C4C3E3] mt-0.5">
              Case No: <span className="font-mono tabular-nums text-jf-apricot font-bold">{hearing.caseNumber}</span>
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-[#C4C3E3] hover:text-white hover:bg-white/10 transition-colors p-1.5 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {conflictError && (
            <div className="p-3 bg-jf-orange/10 border border-jf-orange/30 rounded-lg flex items-start gap-2.5 text-jf-text-orange text-xs">
              <AlertCircle className="w-4 h-4 text-jf-text-orange shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">Reassignment Blocked</p>
                <p className="mt-0.5 leading-relaxed">{conflictError}</p>
              </div>
            </div>
          )}

          <div className="bg-white/80 p-3 rounded-lg border border-jf-border-subtle text-xs space-y-1">
            <div className="font-medium text-jf-ink">Current Assignment:</div>
            <div className="text-jf-ink-muted flex items-center gap-3">
              <span>Judge: <strong className="text-jf-ink font-semibold">{hearing.judgeName || 'Unassigned'}</strong></span>
              <span>Court: <strong className="text-jf-ink font-semibold">{hearing.courtroomName || 'Unassigned'}</strong></span>
            </div>
            <div className="text-jf-ink-muted tabular-nums">
              Slot: {new Date(hearing.scheduledTime).toLocaleString('en-IN', { timeZone: 'UTC' })} ({hearing.durationMinutes} mins)
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-jf-ink mb-1 flex items-center gap-1">
                <User className="w-3.5 h-3.5 text-jf-violet/70" /> Presiding Judge *
              </label>
              <select
                required
                value={judgeId}
                onChange={(e) => setJudgeId(e.target.value)}
                className="w-full text-xs rounded-md border border-jf-border-subtle px-3 py-2 bg-white text-jf-ink focus:outline-none jf-focus-ring"
              >
                <option value="">Select Judge</option>
                {judges.map((j) => (
                  <option key={j.id} value={j.id}>
                    {j.name} {j.specialization ? `(${j.specialization})` : ''}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-jf-ink mb-1 flex items-center gap-1">
                <MapPin className="w-3.5 h-3.5 text-jf-violet/70" /> Courtroom *
              </label>
              <select
                required
                value={courtroomId}
                onChange={(e) => setCourtroomId(e.target.value)}
                className="w-full text-xs rounded-md border border-jf-border-subtle px-3 py-2 bg-white text-jf-ink focus:outline-none jf-focus-ring"
              >
                <option value="">Select Courtroom</option>
                {courtrooms.map((cr) => (
                  <option key={cr.id} value={cr.id}>
                    {cr.name} {cr.building ? `(${cr.building})` : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-jf-ink mb-1 flex items-center gap-1">
                <Calendar className="w-3.5 h-3.5 text-jf-violet/70" /> Date & Time Slot *
              </label>
              <input
                type="datetime-local"
                required
                value={scheduledTime}
                onChange={(e) => setScheduledTime(e.target.value)}
                className="w-full text-xs rounded-md border border-jf-border-subtle px-3 py-2 bg-white text-jf-ink tabular-nums focus:outline-none jf-focus-ring"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-jf-ink mb-1 flex items-center gap-1">
                <Clock className="w-3.5 h-3.5 text-jf-violet/70" /> Duration (minutes) *
              </label>
              <input
                type="number"
                min="15"
                max="240"
                step="15"
                required
                value={durationMinutes}
                onChange={(e) => setDurationMinutes(Number(e.target.value))}
                className="w-full text-xs rounded-md border border-jf-border-subtle px-3 py-2 bg-white text-jf-ink tabular-nums focus:outline-none jf-focus-ring"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-jf-ink mb-1">
              Internal Reassignment Reason (Required for Audit Log) *
            </label>
            <textarea
              required
              rows={2}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Urgent advocate mention; Presiding judge on administrative leave; Bench balance"
              className="w-full text-xs rounded-md border border-jf-border-subtle bg-white text-jf-ink p-2.5 focus:outline-none jf-focus-ring"
            />
            <p className="text-[11px] text-jf-ink-muted mt-0.5">
              Strictly preserved in append-only audit trail; not shared with litigants.
            </p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-jf-ink mb-1">
              Litigant Notification Notice (Optional)
            </label>
            <input
              type="text"
              value={litigantMessage}
              onChange={(e) => setLitigantMessage(e.target.value)}
              placeholder="e.g. Administrative court roster adjustment"
              className="w-full text-xs rounded-md border border-jf-border-subtle bg-white text-jf-ink px-3 py-2 focus:outline-none jf-focus-ring"
            />
            <p className="text-[11px] text-jf-ink-muted mt-0.5">
              Public message included in litigant email notification.
            </p>
          </div>

          <div className="pt-2 border-t border-jf-border-subtle flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isLoading}
              className="px-4 py-2 border border-jf-border-subtle rounded-md text-xs font-medium text-jf-ink hover:bg-jf-canvas transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="px-4 py-2 jf-btn-primary rounded-md text-xs font-semibold disabled:opacity-50 flex items-center gap-1.5 shadow-xs"
            >
              {isLoading ? (
                <>
                  <span className="w-3.5 h-3.5 border-2 border-white/20 border-t-white rounded-full animate-spin"></span>
                  Validating Conflicts...
                </>
              ) : (
                <>
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  Apply Override & Reassign
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
