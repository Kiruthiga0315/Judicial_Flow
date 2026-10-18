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

  const activeBenches = new Set(hearings.map((h) => h.judgeName).filter(Boolean)).size;
  const activeRooms = new Set(hearings.map((h) => h.courtroomName).filter(Boolean)).size;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-bold text-jf-ink tracking-tight flex items-center gap-2">
            <span>{role === 'JUDGE' ? 'My Judicial Bench & Cause List' : 'Master Cause List & Bench Schedule'}</span>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-jf-sage/20 text-jf-ink border border-jf-sage/40 font-semibold">
              Court Calendar
            </span>
          </h2>
          <p className="text-xs text-jf-ink-muted mt-0.5">
            {role === 'JUDGE'
              ? 'Official court calendar restricted to your assigned bench.'
              : 'Court-wide calendar grouped by Presiding Judge and Allocated Courtroom.'}
          </p>
        </div>

        <div className="flex items-center gap-2 self-start sm:self-auto">
          <button
            onClick={() => setIsLeaveModalOpen(true)}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-jf-apricot text-jf-ink border border-jf-apricot/80 rounded-lg text-xs font-bold hover:bg-jf-apricot/80 shadow-2xs transition-colors jf-focus-ring"
          >
            <CalendarOff className="w-3.5 h-3.5 text-jf-text-orange" />
            Register Judge Leave
          </button>
          <button
            onClick={fetchHearings}
            disabled={isLoading}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-jf-cream border border-jf-border-subtle rounded-lg text-xs font-semibold text-jf-ink hover:bg-white shadow-2xs jf-focus-ring transition-colors"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-jf-violet' : 'text-jf-violet'}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* KPI Overview Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-jf-cream rounded-2xl p-4.5 border-2 border-jf-border-subtle shadow-card jf-card-interactive hover:shadow-card-hover space-y-1.5 relative overflow-hidden animate-stagger-1">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-violet" />
          <div className="flex items-center justify-between pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink-muted uppercase tracking-wider">Scheduled Hearings</span>
            <span className="p-1.5 rounded-lg bg-jf-violet/10 text-jf-violet shadow-2xs">
              <Calendar className="w-4 h-4" />
            </span>
          </div>
          <div className="text-3xl font-extrabold text-jf-ink font-mono tabular-nums tracking-tight">{hearings.length}</div>
          <p className="text-[11px] text-jf-ink-muted font-medium">Cause list matters in horizon</p>
        </div>

        <div className="bg-gradient-to-br from-jf-cream to-jf-sage/15 rounded-2xl p-4.5 border-2 border-jf-sage/50 shadow-card jf-card-interactive hover:shadow-card-hover space-y-1.5 relative overflow-hidden animate-stagger-2">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-sage" />
          <div className="flex items-center justify-between pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink uppercase tracking-wider">Presiding Benches</span>
            <span className="p-1.5 rounded-lg bg-jf-sage/30 text-jf-ink shadow-2xs">
              <User className="w-4 h-4 text-jf-ink" />
            </span>
          </div>
          <div className="text-3xl font-extrabold text-jf-ink font-mono tabular-nums tracking-tight">{activeBenches}</div>
          <p className="text-[11px] text-jf-ink-muted font-medium">Judges currently on docket</p>
        </div>

        <div className="bg-gradient-to-br from-jf-cream to-jf-apricot/15 rounded-2xl p-4.5 border-2 border-jf-apricot/60 shadow-card jf-card-interactive hover:shadow-card-hover space-y-1.5 relative overflow-hidden animate-stagger-3">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-apricot" />
          <div className="flex items-center justify-between pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink uppercase tracking-wider">Active Courtrooms</span>
            <span className="p-1.5 rounded-lg bg-jf-apricot/40 text-jf-ink shadow-2xs">
              <Building2 className="w-4 h-4 text-jf-ink" />
            </span>
          </div>
          <div className="text-3xl font-extrabold text-jf-ink font-mono tabular-nums tracking-tight">{activeRooms}</div>
          <p className="text-[11px] text-jf-ink-muted font-medium">Halls booked for proceedings</p>
        </div>
      </div>

      {leaveSuccessNotice && (
        <div className="p-3.5 bg-jf-sage/15 border border-jf-sage/50 rounded-xl text-xs text-jf-ink flex items-start justify-between gap-2 animate-in fade-in">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-jf-sage shrink-0" />
            <span className="font-semibold">{leaveSuccessNotice}</span>
          </div>
          <button
            onClick={() => setLeaveSuccessNotice(null)}
            className="text-jf-ink-muted hover:text-jf-ink font-bold px-1"
          >
            ✕
          </button>
        </div>
      )}

      {/* Date & Filter Controls */}
      <div className="bg-jf-cream p-4.5 rounded-2xl border-2 border-jf-border-subtle shadow-card flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-1.5 text-xs text-jf-ink font-semibold">
            <Calendar className="w-3.5 h-3.5 text-jf-violet" />
            <span>From:</span>
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className="text-xs rounded-md border-2 border-jf-border-subtle px-2.5 py-1.5 bg-white text-jf-ink focus:border-jf-violet jf-focus-ring transition-colors shadow-2xs"
            />
          </div>

          <div className="flex items-center gap-1.5 text-xs text-jf-ink font-semibold">
            <span>To:</span>
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className="text-xs rounded-md border-2 border-jf-border-subtle px-2.5 py-1.5 bg-white text-jf-ink focus:border-jf-violet jf-focus-ring transition-colors shadow-2xs"
            />
          </div>

          {role !== 'JUDGE' && (
            <>
              <div className="flex items-center gap-1.5 text-xs text-jf-ink font-semibold">
                <User className="w-3.5 h-3.5 text-jf-violet" />
                <span>Judge:</span>
                <select
                  value={selectedJudge}
                  onChange={(e) => setSelectedJudge(e.target.value)}
                  className="text-xs rounded-md border-2 border-jf-border-subtle px-2.5 py-1.5 bg-white text-jf-ink focus:border-jf-violet jf-focus-ring transition-colors shadow-2xs"
                >
                  <option value="">All Judges</option>
                  {judges.map((j) => (
                    <option key={j.id} value={j.id}>
                      {j.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex items-center gap-1.5 text-xs text-jf-ink font-semibold">
                <Building2 className="w-3.5 h-3.5 text-jf-violet" />
                <span>Courtroom:</span>
                <select
                  value={selectedCourtroom}
                  onChange={(e) => setSelectedCourtroom(e.target.value)}
                  className="text-xs rounded-md border-2 border-jf-border-subtle px-2.5 py-1.5 bg-white text-jf-ink focus:border-jf-violet jf-focus-ring transition-colors shadow-2xs"
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
        <div className="flex items-center gap-1 bg-white p-1 rounded-lg text-xs font-medium border-2 border-jf-border-subtle shadow-2xs">
          <button
            onClick={() => setGroupBy('JUDGE')}
            className={`px-3 py-1.5 rounded-md transition-all duration-150 jf-focus-ring ${
              groupBy === 'JUDGE' ? 'bg-jf-violet text-jf-cream shadow-xs font-bold -translate-y-0.5' : 'text-jf-ink-muted hover:text-jf-ink hover:bg-jf-canvas'
            }`}
          >
            By Judge
          </button>
          <button
            onClick={() => setGroupBy('COURTROOM')}
            className={`px-3 py-1.5 rounded-md transition-all duration-150 jf-focus-ring ${
              groupBy === 'COURTROOM' ? 'bg-jf-violet text-jf-cream shadow-xs font-bold -translate-y-0.5' : 'text-jf-ink-muted hover:text-jf-ink hover:bg-jf-canvas'
            }`}
          >
            By Courtroom
          </button>
          <button
            onClick={() => setGroupBy('NONE')}
            className={`px-3 py-1.5 rounded-md transition-all duration-150 jf-focus-ring ${
              groupBy === 'NONE' ? 'bg-jf-violet text-jf-cream shadow-xs font-bold -translate-y-0.5' : 'text-jf-ink-muted hover:text-jf-ink hover:bg-jf-canvas'
            }`}
          >
            Chronological
          </button>
        </div>
      </div>

      {/* Schedule Table */}
      <div className="bg-jf-cream rounded-2xl border-2 border-jf-border-subtle shadow-card overflow-hidden">
        {error && (
          <div className="p-4 bg-jf-orange/10 border-b border-jf-orange/30 text-xs text-jf-text-orange font-semibold">
            {error}
          </div>
        )}

        {isLoading ? (
          <div className="py-20 text-center text-jf-ink-muted text-xs">
            <div className="w-6 h-6 border-2 border-jf-violet border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
            Loading cause list schedule...
          </div>
        ) : hearings.length === 0 ? (
          <div className="py-16 text-center text-jf-ink-muted text-xs italic">
            No hearings scheduled for the selected date range and criteria.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y-2 divide-jf-border-subtle text-left text-xs">
              <thead className="bg-gradient-to-r from-jf-violet/15 via-jf-lavender/25 to-jf-violet/10 border-b-2 border-jf-violet/30 text-jf-violet font-bold uppercase tracking-wider text-[11px]">
                <tr>
                  <th scope="col" className="px-4 py-3.5">Scheduled Slot</th>
                  <th scope="col" className="px-4 py-3.5">Case Number</th>
                  <th scope="col" className="px-4 py-3.5">Presiding Judge</th>
                  <th scope="col" className="px-4 py-3.5">Courtroom</th>
                  <th scope="col" className="px-4 py-3.5">Duration</th>
                  <th scope="col" className="px-4 py-3.5">Status</th>
                  {role !== 'JUDGE' && (
                    <th scope="col" className="px-4 py-3.5 text-right">Actions</th>
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-jf-border-subtle/70 bg-jf-cream">
                {hearings.map((h) => {
                  const hearingDate = new Date(h.scheduledTime);
                  return (
                    <tr
                      key={h.id}
                      onClick={() => onSelectCase(h.caseId)}
                      className="hover:bg-jf-lavender/25 cursor-pointer transition-colors group"
                    >
                      <td className="px-4 py-3.5 font-semibold text-jf-ink tabular-nums">
                        <div className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-jf-lavender/35 border border-jf-lavender text-jf-violet font-semibold">
                          <Clock className="w-3.5 h-3.5 text-jf-violet" />
                          <span>
                            {hearingDate.toLocaleDateString('en-IN', {
                              weekday: 'short',
                              day: 'numeric',
                              month: 'short',
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
                      <td className="px-4 py-3.5">
                        <span className="font-mono tabular-nums font-bold text-jf-violet bg-jf-lavender/40 px-2.5 py-1 rounded-md border border-jf-lavender/80 shadow-2xs group-hover:bg-jf-violet group-hover:text-jf-cream transition-colors">
                          {h.caseNumber}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-jf-ink font-semibold">
                        {h.judgeName || 'Unassigned'}
                      </td>
                      <td className="px-4 py-3.5">
                        <span className="px-2 py-0.5 rounded bg-white text-[11px] font-semibold border border-jf-border-subtle text-jf-ink">
                          {h.courtroomName || 'Unassigned'}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-jf-ink-muted tabular-nums font-medium">
                        {h.durationMinutes} mins
                      </td>
                      <td className="px-4 py-3.5">
                        <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wide bg-jf-sage text-jf-ink border border-jf-sage/80 shadow-2xs">
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
                            className="inline-flex items-center gap-1.5 px-3 py-1 text-xs font-bold rounded-lg bg-jf-apricot text-jf-ink border border-jf-apricot/80 hover:bg-jf-apricot/80 transition-all shadow-2xs jf-focus-ring"
                            title="Manually reassign hearing"
                          >
                            <ArrowRightLeft className="w-3 h-3 text-jf-text-orange" />
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
