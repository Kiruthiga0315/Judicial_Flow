import React, { useState } from 'react';
import { Judge, JudgeLeave } from '../types/api';
import { api } from '../api/client';
import { Calendar, AlertTriangle, X, User } from 'lucide-react';

interface JudgeLeaveModalProps {
  judges: Judge[];
  initialJudgeId?: string;
  onClose: () => void;
  onSuccess: (result: JudgeLeave) => void;
}

export const JudgeLeaveModal: React.FC<JudgeLeaveModalProps> = ({
  judges,
  initialJudgeId,
  onClose,
  onSuccess,
}) => {
  const [judgeId, setJudgeId] = useState<string>(initialJudgeId || (judges[0]?.id || ''));
  const [startDate, setStartDate] = useState<string>(() => new Date().toISOString().slice(0, 10));
  const [endDate, setEndDate] = useState<string>(() => new Date().toISOString().slice(0, 10));
  const [reason, setReason] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!judgeId) {
      setError('Please select a judge.');
      return;
    }
    if (startDate > endDate) {
      setError('Start date cannot be after end date.');
      return;
    }
    if (!reason.trim()) {
      setError('Please provide a reason for the leave.');
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const res = await api.recordJudgeLeave(judgeId, {
        startDate,
        endDate,
        reason: reason.trim(),
      });
      onSuccess(res);
    } catch (err: any) {
      setError(err.message || 'Failed to record judge leave');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-jf-ink/65 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl max-w-lg w-full shadow-2xl border-2 border-jf-border-subtle overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="px-6 py-5 bg-[#201F35] text-white flex items-center justify-between border-b-2 border-[#434164] border-t-4 border-t-[#E65100]">
          <div className="flex items-center gap-2.5">
            <Calendar className="w-5 h-5 text-jf-apricot" />
            <h3 className="font-bold text-lg text-white">Register Judge Absence / Leave</h3>
          </div>
          <button
            onClick={onClose}
            className="text-[#C4C3E3] hover:text-white hover:bg-white/10 p-1.5 rounded-lg transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div className="bg-jf-apricot/25 border border-jf-apricot/60 rounded-lg p-3 text-xs text-jf-ink flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 text-jf-text-orange shrink-0 mt-0.5" />
            <span className="leading-relaxed">
              Registering leave will automatically mark any conflicting hearings on these dates as <strong>ADJOURNED</strong> and return the cases to the scheduling pool for automated re-allocation.
            </span>
          </div>

          {error && (
            <div className="p-3 bg-jf-orange/10 border border-jf-orange/30 rounded-lg text-xs text-jf-text-orange flex items-start gap-2">
              <AlertTriangle className="w-4 h-4 text-jf-text-orange shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-jf-ink uppercase tracking-wider mb-1">
              Select Judge
            </label>
            <div className="relative">
              <User className="w-4 h-4 text-jf-violet/70 absolute left-3 top-3" />
              <select
                value={judgeId}
                onChange={(e) => setJudgeId(e.target.value)}
                required
                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-jf-border-subtle bg-white text-jf-ink focus:outline-none jf-focus-ring"
              >
                {judges.map((j) => (
                  <option key={j.id} value={j.id}>
                    {j.name} {j.specialization ? `(${j.specialization})` : ''}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-jf-ink uppercase tracking-wider mb-1">
                Leave Start Date
              </label>
              <input
                type="date"
                required
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-jf-border-subtle bg-white text-jf-ink tabular-nums focus:outline-none jf-focus-ring"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-jf-ink uppercase tracking-wider mb-1">
                Leave End Date
              </label>
              <input
                type="date"
                required
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-jf-border-subtle bg-white text-jf-ink tabular-nums focus:outline-none jf-focus-ring"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-jf-ink uppercase tracking-wider mb-1">
              Reason / Remarks
            </label>
            <textarea
              required
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Judicial training conference, medical leave..."
              className="w-full px-3 py-2 text-sm rounded-lg border border-jf-border-subtle bg-white text-jf-ink focus:outline-none jf-focus-ring"
            />
          </div>

          <div className="pt-3 border-t border-jf-border-subtle flex justify-end gap-2.5">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-jf-ink hover:bg-jf-canvas border border-jf-border-subtle rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="px-4 py-2 text-sm font-semibold jf-btn-primary disabled:opacity-50 rounded-lg shadow-xs transition-colors flex items-center gap-1.5"
            >
              {isLoading ? 'Recording Leave...' : 'Confirm Leave & Adjourn Hearings'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
