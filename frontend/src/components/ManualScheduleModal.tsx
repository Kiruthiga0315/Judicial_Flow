import React, { useState, useEffect } from 'react';
import { Judge, Courtroom } from '../types/api';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import {
  AlertCircle,
  Calendar,
  Clock,
  MapPin,
  User,
  X,
  CheckCircle2,
  Scale,
  Sparkles,
} from 'lucide-react';

interface ManualScheduleModalProps {
  caseId: string;
  caseNumber: string;
  caseType?: string;
  initialJudgeId?: string;
  initialCourtroomId?: string;
  initialScheduledTime?: string;
  initialDurationMinutes?: number;
  onClose: () => void;
  onSuccess: () => void;
}

export const ManualScheduleModal: React.FC<ManualScheduleModalProps> = ({
  caseId,
  caseNumber,
  caseType,
  initialJudgeId,
  initialCourtroomId,
  initialScheduledTime,
  initialDurationMinutes = 60,
  onClose,
  onSuccess,
}) => {
  const { user } = useAuth();
  const [judges, setJudges] = useState<Judge[]>([]);
  const [courtrooms, setCourtrooms] = useState<Courtroom[]>([]);
  const [judgeId, setJudgeId] = useState<string>(initialJudgeId || '');
  const [courtroomId, setCourtroomId] = useState<string>(initialCourtroomId || '');
  
  // Default to tomorrow 10:00 AM if not provided
  const getDefaultDateTime = () => {
    if (initialScheduledTime) {
      return initialScheduledTime.slice(0, 16);
    }
    const d = new Date();
    d.setDate(d.getDate() + 1);
    d.setHours(10, 0, 0, 0);
    const tzOffset = d.getTimezoneOffset() * 60000;
    const localISOTime = new Date(d.getTime() - tzOffset).toISOString().slice(0, 16);
    return localISOTime;
  };

  const [scheduledTime, setScheduledTime] = useState<string>(getDefaultDateTime());
  const [durationMinutes, setDurationMinutes] = useState<number>(initialDurationMinutes);
  const [reason, setReason] = useState<string>('');
  
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [conflictError, setConflictError] = useState<string | null>(null);

  const quickReasons = [
    'Urgent Mention / Interim Relief allocation',
    'Special Bench direct assignment',
    'Administrative roster re-balancing',
    'Expedited hearing per Chief Justice directive',
  ];

  useEffect(() => {
    const fetchMasterData = async () => {
      try {
        const [judgeRes, crRes] = await Promise.all([
          api.listJudges(),
          api.listCourtrooms(),
        ]);
        const fetchedJudges = judgeRes.content || [];
        const fetchedCourtrooms = crRes.content || [];
        setJudges(fetchedJudges);
        setCourtrooms(fetchedCourtrooms);

        if (!judgeId && fetchedJudges.length > 0) {
          setJudgeId(fetchedJudges[0].id);
        }
        if (!courtroomId && fetchedCourtrooms.length > 0) {
          setCourtroomId(fetchedCourtrooms[0].id);
        }
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
      setConflictError('An administrative justification is mandatory for judicial audit compliance.');
      return;
    }

    if (!judgeId || !courtroomId || !scheduledTime) {
      setConflictError('Please select a Presiding Judge, Courtroom, and Time slot.');
      return;
    }

    setIsLoading(true);
    try {
      await api.applyManualOverride({
        caseId,
        judgeId,
        courtroomId,
        scheduledTime: new Date(scheduledTime).toISOString().slice(0, 19),
        durationMinutes: Number(durationMinutes),
        reason: reason.trim(),
        overriddenBy: user?.username || 'Registrar',
      });
      onSuccess();
      onClose();
    } catch (err: any) {
      if (err.status === 409) {
        setConflictError(`Schedule Conflict: ${err.message}`);
      } else {
        setConflictError(err.message || 'Failed to apply direct allocation');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-jf-ink/65 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full border-2 border-jf-border-subtle overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="bg-[#201F35] px-6 py-5 flex items-center justify-between text-white border-b-2 border-[#434164] border-t-4 border-t-[#E65100]">
          <div>
            <div className="flex items-center gap-2">
              <Scale className="w-4 h-4 text-jf-apricot" />
              <h3 className="text-base font-bold">Direct Courtroom & Schedule Allocation</h3>
            </div>
            <p className="text-xs text-[#C4C3E3] mt-1 flex items-center gap-2">
              <span>Case: <strong className="font-mono tabular-nums text-jf-apricot font-bold">{caseNumber}</strong></span>
              {caseType && (
                <span className="px-2 py-0.5 rounded bg-white/15 text-[10px] text-white border border-white/25 font-bold uppercase tracking-wider">
                  {caseType}
                </span>
              )}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-[#C4C3E3] hover:text-white hover:bg-white/10 transition-colors p-1.5 rounded-lg cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {conflictError && (
            <div className="p-3.5 bg-jf-orange/10 border border-jf-orange/30 rounded-lg flex items-start gap-2.5 text-jf-text-orange text-xs">
              <AlertCircle className="w-4 h-4 text-jf-text-orange shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">Allocation Blocked</p>
                <p className="mt-0.5 leading-relaxed">{conflictError}</p>
              </div>
            </div>
          )}

          <div className="bg-jf-lavender/15 p-3 rounded-lg border border-jf-lavender/30 text-xs text-jf-ink flex items-start gap-2">
            <Sparkles className="w-4 h-4 text-jf-violet shrink-0 mt-0.5" />
            <p className="leading-relaxed">
              Direct allocation bypasses engine proposal queues and immediately commits the hearing to the courtroom calendar with high-priority audit tracking.
            </p>
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
              <div className="flex gap-1.5 mt-1.5">
                {[30, 45, 60, 90, 120].map((mins) => (
                  <button
                    key={mins}
                    type="button"
                    onClick={() => setDurationMinutes(mins)}
                    className={`px-1.5 py-0.5 text-[10px] rounded border transition-colors ${
                      durationMinutes === mins
                        ? 'bg-jf-violet text-white border-jf-violet font-semibold'
                        : 'bg-white text-jf-ink border-jf-border-subtle hover:bg-jf-canvas'
                    }`}
                  >
                    {mins}m
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-jf-ink mb-1">
              Allocation Reason / Administrative Justification *
            </label>
            <div className="flex flex-wrap gap-1.5 mb-2">
              {quickReasons.map((qr, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => setReason(qr)}
                  className="px-2 py-1 text-[11px] bg-white text-jf-ink hover:bg-jf-canvas rounded border border-jf-border-subtle text-left transition-colors jf-focus-ring"
                >
                  {qr}
                </button>
              ))}
            </div>
            <textarea
              required
              rows={2}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Provide a clear judicial / administrative justification for this direct allocation..."
              className="w-full text-xs rounded-md border border-jf-border-subtle bg-white text-jf-ink p-2.5 focus:outline-none jf-focus-ring"
            />
            <p className="text-[11px] text-jf-ink-muted mt-0.5">
              Logged to the immutable judicial audit trail under actor: <strong>{user?.username || 'Registrar'}</strong>.
            </p>
          </div>

          <div className="pt-3 border-t border-jf-border-subtle flex justify-end gap-2">
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
                  Allocating Courtroom...
                </>
              ) : (
                <>
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  Allocate & Commit Hearing
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
