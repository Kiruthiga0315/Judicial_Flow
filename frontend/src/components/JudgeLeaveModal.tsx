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
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl max-w-lg w-full shadow-2xl border border-slate-200 overflow-hidden animate-in fade-in zoom-in-95 duration-150">
        <div className="px-6 py-4 bg-slate-900 text-white flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <Calendar className="w-5 h-5 text-amber-400" />
            <h3 className="font-bold text-lg font-serif">Register Judge Absence / Leave</h3>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white p-1 rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-xs text-amber-900 flex items-start gap-2">
            <AlertTriangle className="w-4 h-4 text-amber-700 shrink-0 mt-0.5" />
            <span>
              Registering leave will automatically mark any conflicting hearings on these dates as <strong>ADJOURNED</strong> and return the cases to the scheduling pool for automated re-allocation.
            </span>
          </div>

          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-xs text-red-800 flex items-start gap-2">
              <AlertTriangle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
              Select Judge
            </label>
            <div className="relative">
              <User className="w-4 h-4 text-slate-400 absolute left-3 top-3" />
              <select
                value={judgeId}
                onChange={(e) => setJudgeId(e.target.value)}
                required
                className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-300 focus:ring-2 focus:ring-blue-600 focus:outline-none"
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
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                Leave Start Date
              </label>
              <input
                type="date"
                required
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:ring-2 focus:ring-blue-600 focus:outline-none"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                Leave End Date
              </label>
              <input
                type="date"
                required
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:ring-2 focus:ring-blue-600 focus:outline-none"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
              Reason / Remarks
            </label>
            <textarea
              required
              rows={3}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Judicial training conference, medical leave..."
              className="w-full px-3 py-2 text-sm rounded-lg border border-slate-300 focus:ring-2 focus:ring-blue-600 focus:outline-none"
            />
          </div>

          <div className="pt-3 border-t border-slate-200 flex justify-end gap-2.5">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isLoading}
              className="px-4 py-2 text-sm font-semibold text-white bg-blue-900 hover:bg-blue-800 disabled:opacity-50 rounded-lg shadow-sm transition-colors flex items-center gap-1.5"
            >
              {isLoading ? 'Recording Leave...' : 'Confirm Leave & Adjourn Hearings'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
