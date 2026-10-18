import React, { useState, useEffect } from 'react';
import { Case, CaseStatus, CaseRegistryStats } from '../types/api';
import { api } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { ManualScheduleModal } from '../components/ManualScheduleModal';
import {
  Search,
  Filter,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  Eye,
  Calendar,
  CalendarPlus,
  AlertCircle,
  RotateCcw,
  Briefcase,
  Scale,
  Zap,
} from 'lucide-react';
import { NumberCounter } from '../components/NumberCounter';

interface CaseListPageProps {
  onSelectCase: (caseId: string) => void;
}

export const CaseListPage: React.FC<CaseListPageProps> = ({ onSelectCase }) => {
  const { role } = useAuth();
  const [cases, setCases] = useState<Case[]>([]);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [pageSize] = useState<number>(15);

  const [selectedType, setSelectedType] = useState<string>('');
  const [selectedStatus, setSelectedStatus] = useState<string>('');
  const [sortBy, setSortBy] = useState<string>('filingDate');
  const [direction, setDirection] = useState<'ASC' | 'DESC'>('DESC');
  const [searchTerm, setSearchTerm] = useState<string>('');

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [scheduleModalCase, setScheduleModalCase] = useState<Case | null>(null);
  const [stats, setStats] = useState<CaseRegistryStats | null>(null);

  const fetchStats = async () => {
    try {
      const res = await api.getRegistryStats(
        selectedType || undefined,
        selectedStatus || undefined,
        searchTerm || undefined
      );
      if (res) {
        setStats(res);
      }
    } catch {
      // Graceful fallback: keep existing or derived counts
    }
  };

  useEffect(() => {
    fetchStats();
  }, [selectedType, selectedStatus, searchTerm]);

  const fetchCases = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await api.listCases(
        page,
        pageSize,
        sortBy,
        direction,
        selectedType || undefined,
        selectedStatus || undefined,
        searchTerm || undefined
      );
      setCases(res.content || []);
      setTotalPages(res.totalPages || 0);
      setTotalElements(res.totalElements || 0);
    } catch (err: any) {
      setError(err.message || 'Failed to retrieve court cases');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchCases();
  }, [page, sortBy, direction, selectedType, selectedStatus, searchTerm]);

  const handleSortToggle = (field: string) => {
    if (sortBy === field) {
      setDirection(direction === 'ASC' ? 'DESC' : 'ASC');
    } else {
      setSortBy(field);
      setDirection('DESC');
    }
    setPage(0);
  };

  const scoreBadgeColor = (score?: number | null) => {
    if (score == null) return 'bg-[#EEEBF5] text-[#5C5A78] border-[#D8D6E8]';
    if (score >= 70) return 'bg-[#E65100] text-white border-[#BF360C] font-extrabold shadow-xs';
    if (score >= 45) return 'bg-[#F9D68A] text-[#201F35] border-[#D4A746] font-extrabold shadow-2xs';
    return 'bg-[#D2DE9E] text-[#1E2E08] border-[#8A9E48] font-bold shadow-2xs';
  };

  const statusBadgeColor = (status: CaseStatus) => {
    switch (status) {
      case 'FILED':
      case 'PENDING':
        return 'bg-jf-lavender/40 text-jf-violet border-jf-violet/30 font-semibold';
      case 'SCHEDULED':
        return 'bg-jf-violet text-jf-cream font-bold border-jf-violet shadow-2xs';
      case 'HEARD':
        return 'bg-jf-apricot text-jf-ink font-bold border-jf-apricot/80 shadow-2xs';
      case 'DISPOSED':
        return 'bg-jf-sage text-jf-ink font-bold border-jf-sage/80 shadow-2xs';
      case 'ADJOURNED':
        return 'bg-jf-orange/15 text-jf-text-orange border-jf-orange/40 font-bold';
      default:
        return 'bg-jf-cream-darker text-jf-ink-muted border-jf-border-subtle';
    }
  };

  const criticalCount = stats?.criticalUrgencyCount ?? cases.filter((c) => (c.priorityScore ?? 0) >= 70).length;
  const highCount = stats?.highStatutoryCount ?? cases.filter((c) => (c.priorityScore ?? 0) >= 45 && (c.priorityScore ?? 0) < 70).length;
  const scheduledCount = stats?.allocatedSlotsCount ?? cases.filter((c) => c.currentStatus === 'SCHEDULED' || c.currentStatus === 'HEARD').length;
  const displayTotal = stats?.totalCases ?? totalElements;

  const filteredDisplayCases = cases;

  return (
    <div className="space-y-6">
      {/* Page Title & Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-bold text-jf-ink tracking-tight flex items-center gap-2">
            <span>Case Registry & Urgency Docket</span>
            <span className="text-xs px-2.5 py-0.5 rounded-full bg-jf-violet/10 text-jf-violet border border-jf-violet/20 font-semibold">
              Live Roster
            </span>
          </h2>
          <p className="text-xs text-jf-ink-muted mt-0.5">
            Total of <span className="font-bold text-jf-ink tabular-nums">{displayTotal}</span> registered matters. Click any row for explainable factor breakdown.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => {
              setSelectedType('');
              setSelectedStatus('');
              setSearchTerm('');
              setPage(0);
            }}
            title="Reset filters"
            className="px-3 py-1.5 border border-jf-border-subtle bg-jf-cream rounded-lg text-jf-ink hover:text-jf-text-orange hover:border-jf-orange/30 text-xs font-semibold flex items-center gap-1.5 jf-focus-ring transition-colors shadow-2xs"
          >
            <RotateCcw className="w-3.5 h-3.5 text-jf-violet" />
            <span>Reset Filters</span>
          </button>
        </div>
      </div>

      {/* Executive KPI Stat Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-jf-cream rounded-2xl p-4.5 border-2 border-jf-border-subtle shadow-card jf-card-interactive hover:shadow-card-hover space-y-1.5 relative overflow-hidden animate-stagger-1">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-violet" />
          <div className="flex items-center justify-between pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink-muted uppercase tracking-wider">Total Registry</span>
            <span className="p-1.5 rounded-lg bg-jf-violet/10 text-jf-violet shadow-2xs">
              <Briefcase className="w-4 h-4" />
            </span>
          </div>
          <div className="text-3xl font-extrabold text-jf-ink font-mono tabular-nums tracking-tight">
            <NumberCounter value={displayTotal} duration={1200} />
          </div>
          <p className="text-[11px] text-jf-ink-muted font-medium">Active court proceedings</p>
        </div>

        <div className="bg-gradient-to-br from-jf-cream to-jf-orange/10 rounded-2xl p-4.5 border-2 border-jf-orange/40 shadow-card jf-card-interactive hover:shadow-card-hover space-y-1.5 relative overflow-hidden animate-stagger-2">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-orange" />
          <div className="flex items-center justify-between pt-0.5">
            <span className="text-[11px] font-bold text-jf-text-orange uppercase tracking-wider flex items-center gap-1.5">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-jf-orange opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-jf-orange"></span>
              </span>
              Critical Urgency
            </span>
            <span className="p-1.5 rounded-lg bg-jf-orange/20 text-jf-text-orange shadow-2xs">
              <Zap className="w-4 h-4 text-jf-orange" />
            </span>
          </div>
          <div className="text-3xl font-extrabold text-jf-text-orange font-mono tabular-nums tracking-tight">
            <NumberCounter value={criticalCount} duration={1200} />
          </div>
          <p className="text-[11px] text-jf-ink-muted font-medium">Score ≥ 70 (Immediate listing)</p>
        </div>

        <div className="bg-gradient-to-br from-jf-cream to-jf-apricot/15 rounded-2xl p-4.5 border-2 border-jf-apricot/60 shadow-card jf-card-interactive hover:shadow-card-hover space-y-1.5 relative overflow-hidden animate-stagger-3">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-apricot" />
          <div className="flex items-center justify-between pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink uppercase tracking-wider">High Statutory</span>
            <span className="p-1.5 rounded-lg bg-jf-apricot/40 text-jf-ink shadow-2xs">
              <Scale className="w-4 h-4 text-jf-ink" />
            </span>
          </div>
          <div className="text-3xl font-extrabold text-jf-ink font-mono tabular-nums tracking-tight">
            <NumberCounter value={highCount} duration={1200} />
          </div>
          <p className="text-[11px] text-jf-ink-muted font-medium">Score 45–69 (Accelerated)</p>
        </div>

        <div className="bg-gradient-to-br from-jf-cream to-jf-sage/15 rounded-2xl p-4.5 border-2 border-jf-sage/50 shadow-card jf-card-interactive hover:shadow-card-hover space-y-1.5 relative overflow-hidden animate-stagger-4">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-sage" />
          <div className="flex items-center justify-between pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink uppercase tracking-wider">Allocated Slots</span>
            <span className="p-1.5 rounded-lg bg-jf-sage/30 text-jf-ink shadow-2xs">
              <Calendar className="w-4 h-4 text-jf-ink" />
            </span>
          </div>
          <div className="text-3xl font-extrabold text-jf-ink font-mono tabular-nums tracking-tight">
            <NumberCounter value={scheduledCount} duration={1200} />
          </div>
          <p className="text-[11px] text-jf-ink-muted font-medium">Hearings currently scheduled</p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-jf-cream p-4.5 rounded-2xl border-2 border-jf-border-subtle shadow-card flex flex-col md:flex-row gap-3 items-stretch md:items-center justify-between">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-jf-violet absolute left-3 top-2.5" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => {
              setSearchTerm(e.target.value);
              setPage(0);
            }}
            placeholder="Search by Case Number (e.g. SYN-2026-)..."
            className="w-full text-xs rounded-lg border-2 border-jf-border-subtle bg-white pl-9 pr-3 py-2 text-jf-ink focus:border-jf-violet jf-focus-ring transition-colors shadow-2xs"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <div className="flex items-center gap-1.5 text-xs text-jf-ink font-semibold">
            <Filter className="w-3.5 h-3.5 text-jf-violet" />
            <span>Type:</span>
            <select
              value={selectedType}
              onChange={(e) => {
                setSelectedType(e.target.value);
                setPage(0);
              }}
              className="text-xs rounded-md border-2 border-jf-border-subtle px-2.5 py-1.5 bg-white text-jf-ink focus:border-jf-violet jf-focus-ring transition-colors shadow-2xs"
            >
              <option value="">All Types</option>
              <option value="BAIL">Bail Matters</option>
              <option value="POCSO">POCSO / Special Act</option>
              <option value="MATRIMONIAL">Matrimonial</option>
              <option value="CIVIL">Civil Suits</option>
              <option value="CRIMINAL_OTHER">General Criminal</option>
            </select>
          </div>

          <div className="flex items-center gap-1.5 text-xs text-jf-ink font-semibold">
            <span>Status:</span>
            <select
              value={selectedStatus}
              onChange={(e) => {
                setSelectedStatus(e.target.value);
                setPage(0);
              }}
              className="text-xs rounded-md border-2 border-jf-border-subtle px-2.5 py-1.5 bg-white text-jf-ink focus:border-jf-violet jf-focus-ring transition-colors shadow-2xs"
            >
              <option value="">All Statuses</option>
              <option value="FILED">FILED (Pending)</option>
              <option value="SCHEDULED">SCHEDULED</option>
              <option value="HEARD">HEARD</option>
              <option value="ADJOURNED">ADJOURNED</option>
              <option value="DISPOSED">DISPOSED</option>
            </select>
          </div>
        </div>
      </div>

      {/* Case Table */}
      <div className="bg-jf-cream rounded-2xl border-2 border-jf-border-subtle shadow-card overflow-hidden">
        {error && (
          <div className="p-4 bg-jf-orange/10 border-b border-jf-orange/30 flex items-center gap-2 text-jf-text-orange text-xs">
            <AlertCircle className="w-4 h-4 text-jf-orange shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y-2 divide-jf-border-subtle text-left text-xs">
            <thead className="bg-gradient-to-r from-jf-violet/15 via-jf-lavender/25 to-jf-violet/10 border-b-2 border-jf-violet/30 text-jf-violet font-bold uppercase tracking-wider text-[11px]">
              <tr>
                <th scope="col" className="px-4 py-3.5 cursor-pointer" onClick={() => handleSortToggle('caseNumber')}>
                  <div className="flex items-center gap-1">
                    <span>Case Number</span>
                    <ArrowUpDown className="w-3 h-3 text-jf-violet/70" />
                  </div>
                </th>
                <th scope="col" className="px-4 py-3.5">Case Type</th>
                <th scope="col" className="px-4 py-3.5 cursor-pointer" onClick={() => handleSortToggle('currentStatus')}>
                  <div className="flex items-center gap-1">
                    <span>Status</span>
                    <ArrowUpDown className="w-3 h-3 text-jf-violet/70" />
                  </div>
                </th>
                <th scope="col" className="px-4 py-3.5 cursor-pointer" onClick={() => handleSortToggle('filingDate')}>
                  <div className="flex items-center gap-1">
                    <span>Filing Date</span>
                    <ArrowUpDown className="w-3 h-3 text-jf-violet/70" />
                  </div>
                </th>
                <th scope="col" className="px-4 py-3.5 cursor-pointer" onClick={() => handleSortToggle('priorAdjournments')}>
                  <div className="flex items-center gap-1">
                    <span>Adjournments</span>
                    <ArrowUpDown className="w-3 h-3 text-jf-violet/70" />
                  </div>
                </th>
                <th scope="col" className="px-4 py-3.5">Urgency Score</th>
                <th scope="col" className="px-4 py-3.5">Next Scheduled Hearing</th>
                <th scope="col" className="px-4 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-jf-border-subtle/70 bg-jf-cream">
              {isLoading ? (
                <tr>
                  <td colSpan={8} className="py-16 text-center text-jf-ink-muted">
                    <div className="w-6 h-6 border-2 border-jf-violet border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    Loading registry records...
                  </td>
                </tr>
              ) : filteredDisplayCases.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-jf-ink-muted italic">
                    No cases match the selected filters.
                  </td>
                </tr>
              ) : (
                filteredDisplayCases.map((c) => {
                  const isCritical = (c.priorityScore ?? 0) >= 70;
                  return (
                    <tr
                      key={c.id}
                      onClick={() => onSelectCase(c.id)}
                      className={`cursor-pointer transition-all duration-200 group ${
                        isCritical
                          ? 'bg-jf-orange/[0.035] hover:bg-jf-orange/[0.08]'
                          : 'hover:bg-jf-lavender/35'
                      }`}
                    >
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-1.5">
                          {isCritical && (
                            <span className="relative flex h-2 w-2 shrink-0" title="Critical urgency listing">
                              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-jf-orange opacity-75"></span>
                              <span className="relative inline-flex rounded-full h-2 w-2 bg-jf-orange"></span>
                            </span>
                          )}
                          <span className="font-mono tabular-nums font-bold text-jf-violet bg-jf-lavender/40 px-2.5 py-1 rounded-md border border-jf-lavender/80 shadow-2xs group-hover:bg-jf-violet group-hover:text-jf-cream group-hover:shadow-glow-violet transition-all duration-150">
                            {c.caseNumber}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5 font-medium text-jf-ink">
                        <span className="px-2 py-0.5 rounded bg-white text-[11px] font-semibold border border-jf-border-subtle text-jf-ink shadow-2xs">
                          {c.caseType}
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <span
                          className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border shadow-2xs ${statusBadgeColor(
                            c.currentStatus
                          )}`}
                        >
                          {c.currentStatus}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-jf-ink-muted tabular-nums">
                        {c.filingDate ? new Date(c.filingDate).toLocaleDateString('en-IN') : 'N/A'}
                      </td>
                      <td className="px-4 py-3.5 text-jf-ink font-medium tabular-nums">
                        {c.priorAdjournments > 0 ? (
                          <span className="text-jf-text-orange font-bold">{c.priorAdjournments}</span>
                        ) : (
                          <span className="text-jf-ink-muted/60">0</span>
                        )}
                      </td>
                      <td className="px-4 py-3.5">
                        {c.priorityScore != null ? (
                          <span
                            className={`inline-block px-2.5 py-1 rounded-md text-xs font-mono font-bold border-2 tabular-nums tracking-tight shadow-xs ${scoreBadgeColor(
                              c.priorityScore
                            )}`}
                          >
                            {c.priorityScore.toFixed(1)}
                          </span>
                        ) : (
                          <span className="text-[#5C5A78] text-xs italic font-medium">Unscored</span>
                        )}
                      </td>
                      <td className="px-4 py-3.5 text-jf-ink">
                        {c.nextHearingDate ? (
                          <span className="inline-flex items-center gap-1 font-medium text-jf-ink tabular-nums">
                            <Calendar className="w-3.5 h-3.5 text-jf-violet" />
                            {new Date(c.nextHearingDate).toLocaleDateString('en-IN')}
                          </span>
                        ) : (
                          <span className="text-jf-ink-muted italic">Unscheduled</span>
                        )}
                      </td>
                      <td className="px-4 py-3.5 text-right space-x-1.5 whitespace-nowrap">
                        {role !== 'JUDGE' && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setScheduleModalCase(c);
                            }}
                            className="inline-flex items-center gap-1 px-2.5 py-1 rounded bg-jf-violet/10 text-jf-violet border border-jf-violet/25 hover:bg-jf-violet hover:text-jf-cream font-semibold text-[11px] jf-focus-ring hover:-translate-y-0.5 hover:shadow-xs active:translate-y-0 transition-all shadow-2xs"
                            title="Directly allocate courtroom & schedule"
                          >
                            <CalendarPlus className="w-3.5 h-3.5" />
                            <span>{c.nextHearingDate ? 'Re-allocate' : 'Schedule'}</span>
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            onSelectCase(c.id);
                          }}
                          className="inline-flex items-center gap-1.5 px-3 py-1 rounded-md bg-[#FAF9FE] border-2 border-[#434164] text-[#434164] hover:bg-[#434164] hover:text-white font-bold text-[11px] jf-focus-ring hover:-translate-y-0.5 hover:shadow-xs active:translate-y-0 transition-all shadow-2xs group/btn cursor-pointer"
                          title="View breakdown & score history"
                        >
                          <Eye className="w-3.5 h-3.5 text-[#434164] group-hover/btn:text-white transition-colors shrink-0" />
                          <span className="text-[#434164] group-hover/btn:text-white transition-colors font-bold">View</span>
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination controls */}
        <div className="bg-jf-cream-darker/40 px-4 py-3 border-t border-jf-border-subtle flex items-center justify-between text-xs text-jf-ink-muted">
          <div>
            Showing Page <strong className="text-jf-ink tabular-nums">{page + 1}</strong> of <strong className="text-jf-ink tabular-nums">{Math.max(1, totalPages)}</strong> (<span className="tabular-nums">{totalElements}</span> cases)
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0 || isLoading}
              className="px-3 py-1.5 border border-jf-border-subtle rounded-md bg-jf-cream hover:bg-jf-cream-darker disabled:opacity-50 text-jf-ink jf-focus-ring transition-colors flex items-center gap-1 shadow-2xs"
            >
              <ChevronLeft className="w-3.5 h-3.5" /> Previous
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || isLoading}
              className="px-3 py-1.5 border border-jf-border-subtle rounded-md bg-jf-cream hover:bg-jf-cream-darker disabled:opacity-50 text-jf-ink jf-focus-ring transition-colors flex items-center gap-1 shadow-2xs"
            >
              Next <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* Direct Schedule Modal */}
      {scheduleModalCase && (
        <ManualScheduleModal
          caseId={scheduleModalCase.id}
          caseNumber={scheduleModalCase.caseNumber}
          caseType={scheduleModalCase.caseType}
          initialJudgeId={scheduleModalCase.assignedJudge?.id}
          initialCourtroomId={scheduleModalCase.assignedCourtroom?.id}
          initialScheduledTime={scheduleModalCase.nextHearingDate || undefined}
          onClose={() => setScheduleModalCase(null)}
          onSuccess={() => {
            setScheduleModalCase(null);
            fetchCases();
          }}
        />
      )}
    </div>
  );
};
