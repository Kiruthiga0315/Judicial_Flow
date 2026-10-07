import React, { useState, useEffect } from 'react';
import { Hearing, Judge, Courtroom } from '../types/api';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import {
  Calendar,
  Clock,
  User,
  ArrowRightLeft,
  RefreshCw,
  Building2,
  CalendarOff,
  CheckCircle2,
} from 'lucide-react';
import { JudgeLeaveModal } from '../components/JudgeLeaveModal';

interface SchedulePageProps {
  onSelectCase: (caseId: string) => void;
  onOpenReassign: (hearing: Hearing) => void;
}

export const SchedulePage: React.FC<SchedulePageProps> = ({ onSelectCase, onOpenReassign }) => {
  const { role, judgeId: userJudgeId } = useAuth();
  const [hearings, setHearings] = useState<Hearing[]>([]);
  const [judges, setJudges] = useState<Judge[]>([]);
  const [courtrooms, setCourtrooms] = useState<Courtroom[]>([]);
  const [isLeaveModalOpen, setIsLeaveModalOpen] = useState<boolean>(false);
  const [leaveSuccessNotice, setLeaveSuccessNotice] = useState<string | null>(null);

  // Date range defaults: Today to Today + 14 days
  const [fromDate, setFromDate] = useState<string>(() => {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    return d.toISOString().slice(0, 10);
  });
  const [toDate, setToDate] = useState<string>(() => {
    const d = new Date();
    d.setDate(d.getDate() + 14);
    d.setHours(23, 59, 59, 999);
    return d.toISOString().slice(0, 10);
  });

  const [selectedJudge, setSelectedJudge] = useState<string>(userJudgeId || '');
  const [selectedCourtroom, setSelectedCourtroom] = useState<string>('');
  const [groupBy, setGroupBy] = useState<'NONE' | 'JUDGE' | 'COURTROOM'>('JUDGE');

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadMaster = async () => {
      try {
        const [jRes, cRes] = await Promise.all([api.listJudges(), api.listCourtrooms()]);
        setJudges(jRes.content || []);
        setCourtrooms(cRes.content || []);
      } catch (err) {
        console.error('Failed to load master data', err);
      }
    };
    loadMaster();
  }, []);

  const fetchHearings = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const fromIso = fromDate ? `${fromDate}T00:00:00` : undefined;
      const toIso = toDate ? `${toDate}T23:59:59` : undefined;
      const jId = role === 'JUDGE' && userJudgeId ? userJudgeId : selectedJudge || undefined;

      const data = await api.listHearings(fromIso, toIso, jId, selectedCourtroom || undefined);
      setHearings(data || []);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve hearing calendar');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchHearings();
  }, [fromDate, toDate, selectedJudge, selectedCourtroom]);

  return (
    <div className="space-y-5">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">
            {role === 'JUDGE' ? 'My Judicial Bench & Cause List' : 'Master Cause List & Bench Schedule'}
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            {role === 'JUDGE'
              ? 'Official court calendar restricted to your assigned bench.'
              : 'Court-wide calendar grouped by Presiding Judge and Allocated Courtroom.'}
          </p>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          <button
            onClick={() => setIsLeaveModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-amber-50 border border-amber-300 rounded-lg text-xs font-semibold text-amber-900 hover:bg-amber-100 shadow-2xs transition-colors"
          >
            <CalendarOff className="w-3.5 h-3.5 text-amber-700" />
            Register Judge Leave
          </button>
          <button
            onClick={fetchHearings}
            disabled={isLoading}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-white border border-slate-300 rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-50 shadow-2xs"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {leaveSuccessNotice && (
        <div className="p-3.5 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-900 flex items-start justify-between gap-2 animate-in fade-in">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>{leaveSuccessNotice}</span>
          </div>
          <button
            onClick={() => setLeaveSuccessNotice(null)}
            className="text-emerald-700 hover:text-emerald-900 font-bold px-1"
          >
            ✕
          </button>
        </div>
      )}

      {/* Date & Filter Controls */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1.5 text-xs text-slate-600">
            <Calendar className="w-3.5 h-3.5 text-slate-400" />
            <span>From:</span>
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="text-xs rounded-md border border-slate-300 px-2.5 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          <div className="flex items-center gap-1.5 text-xs text-slate-600">
            <span>To:</span>
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className="text-xs rounded-md border border-slate-300 px-2.5 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>

          {role !== 'JUDGE' && (
            <>
              <div className="flex items-center gap-1.5 text-xs text-slate-600">
                <User className="w-3.5 h-3.5 text-slate-400" />
                <span>Judge:</span>
                <select
                  value={selectedJudge}
                  onChange={(e) => setSelectedJudge(e.target.value)}
                  className="text-xs rounded-md border border-slate-300 px-2.5 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">All Judges</option>
                  {judges.map((j) => (
                    <option key={j.id} value={j.id}>
                      {j.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex items-center gap-1.5 text-xs text-slate-600">
                <Building2 className="w-3.5 h-3.5 text-slate-400" />
                <span>Courtroom:</span>
                <select
                  value={selectedCourtroom}
                  onChange={(e) => setSelectedCourtroom(e.target.value)}
                  className="text-xs rounded-md border border-slate-300 px-2.5 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">All Courtrooms</option>
                  {courtrooms.map((cr) => (
                    <option key={cr.id} value={cr.id}>
                      {cr.name}
                    </option>
                  ))}
                </select>
              </div>
            </>
          )}
        </div>

        {/* Grouping switcher */}
        <div className="flex items-center gap-1 bg-slate-100 p-1 rounded-lg text-xs font-medium text-slate-600">
          <button
            onClick={() => setGroupBy('JUDGE')}
            className={`px-2.5 py-1 rounded-md transition-colors ${
              groupBy === 'JUDGE' ? 'bg-white text-blue-900 shadow-2xs font-semibold' : 'hover:text-slate-900'
            }`}
          >
            By Judge
          </button>
          <button
            onClick={() => setGroupBy('COURTROOM')}
            className={`px-2.5 py-1 rounded-md transition-colors ${
              groupBy === 'COURTROOM' ? 'bg-white text-blue-900 shadow-2xs font-semibold' : 'hover:text-slate-900'
            }`}
          >
            By Courtroom
          </button>
          <button
            onClick={() => setGroupBy('NONE')}
            className={`px-2.5 py-1 rounded-md transition-colors ${
              groupBy === 'NONE' ? 'bg-white text-blue-900 shadow-2xs font-semibold' : 'hover:text-slate-900'
            }`}
          >
            Chronological
          </button>
        </div>
      </div>

      {/* Schedule Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        {error && (
          <div className="p-4 bg-red-50 border-b border-red-200 text-xs text-red-800">
            {error}
          </div>
        )}

        {isLoading ? (
          <div className="py-20 text-center text-slate-500 text-xs">
            <div className="w-6 h-6 border-2 border-blue-900 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
            Loading cause list schedule...
          </div>
        ) : hearings.length === 0 ? (
          <div className="py-16 text-center text-slate-400 text-xs italic">
            No hearings scheduled for the selected date range and criteria.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 font-semibold uppercase tracking-wider text-[11px]">
                <tr>
                  <th scope="col" className="px-4 py-3">Scheduled Slot</th>
                  <th scope="col" className="px-4 py-3">Case Number</th>
                  <th scope="col" className="px-4 py-3">Presiding Judge</th>
                  <th scope="col" className="px-4 py-3">Courtroom</th>
                  <th scope="col" className="px-4 py-3">Duration</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  {role !== 'JUDGE' && (
                    <th scope="col" className="px-4 py-3 text-right">Actions</th>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {hearings.map((h) => {
                  const hearingDate = new Date(h.scheduledTime);
                  return (
                    <tr
                      key={h.id}
                      onClick={() => onSelectCase(h.caseId)}
                      className="hover:bg-blue-50/40 cursor-pointer transition-colors"
                    >
                      <td className="px-4 py-3.5 font-semibold text-slate-900">
                        <div className="flex items-center gap-1.5">
                          <Clock className="w-3.5 h-3.5 text-blue-700" />
                          <span>
                            {hearingDate.toLocaleDateString('en-IN', {
                              weekday: 'short',
                              day: 'numeric',
                              month: 'short',
                              year: 'numeric',
                            })}
                            ,{' '}
                            {hearingDate.toLocaleTimeString('en-IN', {
                              hour: '2-digit',
                              minute: '2-digit',
                              timeZone: 'UTC',
                            })}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5 font-mono font-bold text-blue-950">
                        {h.caseNumber}
                      </td>
                      <td className="px-4 py-3.5 text-slate-800 font-medium">
                        {h.judgeName || 'Unassigned'}
                      </td>
                      <td className="px-4 py-3.5 text-slate-700">
                        {h.courtroomName || 'Unassigned'}
                      </td>
                      <td className="px-4 py-3.5 text-slate-600">
                        {h.durationMinutes} minutes
                      </td>
                      <td className="px-4 py-3.5">
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wide bg-emerald-50 text-emerald-800 border border-emerald-200">
                          {h.status}
                        </span>
                      </td>
                      {role !== 'JUDGE' && (
                        <td className="px-4 py-3.5 text-right">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              onOpenReassign(h);
                            }}
                            className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-semibold rounded-md bg-amber-50 text-amber-900 border border-amber-300 hover:bg-amber-100 transition-colors shadow-2xs"
                            title="Manually reassign hearing"
                          >
                            <ArrowRightLeft className="w-3 h-3 text-amber-700" />
                            Reassign
                          </button>
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {isLeaveModalOpen && (
        <JudgeLeaveModal
          judges={judges}
          initialJudgeId={role === 'JUDGE' && userJudgeId ? userJudgeId : selectedJudge || undefined}
          onClose={() => setIsLeaveModalOpen(false)}
          onSuccess={(res) => {
            setIsLeaveModalOpen(false);
            setLeaveSuccessNotice(
              res.message ||
                `Leave registered for ${res.judgeName}. ${res.affectedHearingsCount} hearing(s) adjourned for rescheduling.`
            );
            fetchHearings();
          }}
        />
      )}
    </div>
  );
};
