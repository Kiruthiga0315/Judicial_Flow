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
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-xl shadow-2xl max-w-lg w-full border border-slate-200 overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        {/* Header */}
        <div className="bg-slate-900 px-6 py-4 flex items-center justify-between text-white border-b border-slate-800">
          <div>
            <div className="flex items-center gap-2">
              <Scale className="w-4 h-4 text-amber-400" />
              <h3 className="text-base font-semibold">Direct Courtroom & Schedule Allocation</h3>
            </div>
            <p className="text-xs text-slate-300 mt-1 flex items-center gap-2">
              <span>Case: <strong className="font-mono text-amber-300">{caseNumber}</strong></span>
              {caseType && (
                <span className="px-1.5 py-0.5 rounded bg-slate-800 text-[10px] text-slate-300 border border-slate-700">
                  {caseType}
                </span>
              )}
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white transition-colors p-1 rounded-md"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {conflictError && (
            <div className="p-3.5 bg-red-50 border border-red-200 rounded-lg flex items-start gap-2.5 text-red-800 text-xs">
              <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">Allocation Blocked</p>
                <p className="mt-0.5 leading-relaxed">{conflictError}</p>
              </div>
            </div>
          )}

          <div className="bg-blue-50/50 p-3 rounded-lg border border-blue-100 text-xs text-blue-900 flex items-start gap-2">
            <Sparkles className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
            <p className="leading-relaxed">
              Direct allocation bypasses engine proposal queues and immediately commits the hearing to the courtroom calendar with high-priority audit tracking.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1 flex items-center gap-1">
                <User className="w-3.5 h-3.5 text-slate-500" /> Presiding Judge *
              </label>
              <select
                required
                value={judgeId}
                onChange={(e) => setJudgeId(e.target.value)}
                className="w-full text-xs rounded-md border border-slate-300 px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
              <label className="block text-xs font-semibold text-slate-700 mb-1 flex items-center gap-1">
                <MapPin className="w-3.5 h-3.5 text-slate-500" /> Courtroom *
              </label>
              <select
                required
                value={courtroomId}
                onChange={(e) => setCourtroomId(e.target.value)}
                className="w-full text-xs rounded-md border border-slate-300 px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
              <label className="block text-xs font-semibold text-slate-700 mb-1 flex items-center gap-1">
                <Calendar className="w-3.5 h-3.5 text-slate-500" /> Date & Time Slot *
              </label>
              <input
                type="datetime-local"
                required
                value={scheduledTime}
                onChange={(e) => setScheduledTime(e.target.value)}
                className="w-full text-xs rounded-md border border-slate-300 px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
              </input>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1 flex items-center gap-1">
                <Clock className="w-3.5 h-3.5 text-slate-500" /> Duration (minutes) *
              </label>
              <input
                type="number"
                min="15"
                max="240"
                step="15"
                required
                value={durationMinutes}
                onChange={(e) => setDurationMinutes(Number(e.target.value))}
                className="w-full text-xs rounded-md border border-slate-300 px-3 py-2 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <div className="flex gap-1.5 mt-1.5">
                {[30, 45, 60, 90, 120].map((mins) => (
                  <button
                    key={mins}
                    type="button"
                    onClick={() => setDurationMinutes(mins)}
                    className={`px-1.5 py-0.5 text-[10px] rounded border transition-colors ${
                      durationMinutes === mins
                        ? 'bg-blue-900 text-white border-blue-900 font-semibold'
                        : 'bg-slate-50 text-slate-600 border-slate-200 hover:bg-slate-100'
                    }`}
                  >
                    {mins}m
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 mb-1">
              Allocation Reason / Administrative Justification *
            </label>
            <div className="flex flex-wrap gap-1.5 mb-2">
              {quickReasons.map((qr, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => setReason(qr)}
                  className="px-2 py-1 text-[11px] bg-slate-100 text-slate-700 hover:bg-slate-200 rounded border border-slate-200 text-left transition-colors"
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
              className="w-full text-xs rounded-md border border-slate-300 p-2.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p className="text-[11px] text-slate-500 mt-0.5">
              Logged to the immutable judicial audit trail under actor: <strong>{user?.username || 'Registrar'}</strong>.
            </p>
          </div>

          <div className="pt-3 border-t border-slate-200 flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              disabled={isLoading}
              className="px-4 py-2 border border-slate-300 rounded-md text-xs font-medium text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="px-4 py-2 bg-blue-900 text-white rounded-md text-xs font-medium hover:bg-blue-800 disabled:opacity-50 flex items-center gap-1.5 shadow-sm"
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
