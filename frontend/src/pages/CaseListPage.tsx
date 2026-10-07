import React, { useState, useEffect } from 'react';
import { Case, CaseStatus } from '../types/api';
import { api } from '../api/client';
import {
  Search,
  Filter,
  ArrowUpDown,
  ChevronLeft,
  ChevronRight,
  Eye,
  Calendar,
  AlertCircle,
  RotateCcw,
} from 'lucide-react';

interface CaseListPageProps {
  onSelectCase: (caseId: string) => void;
}

export const CaseListPage: React.FC<CaseListPageProps> = ({ onSelectCase }) => {
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
        selectedStatus || undefined
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
  }, [page, sortBy, direction, selectedType, selectedStatus]);

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
    if (score == null) return 'bg-slate-100 text-slate-600 border-slate-200';
    if (score >= 70) return 'bg-red-50 text-red-800 border-red-300';
    if (score >= 45) return 'bg-amber-50 text-amber-800 border-amber-300';
    return 'bg-emerald-50 text-emerald-800 border-emerald-300';
  };

  const statusBadgeColor = (status: CaseStatus) => {
    switch (status) {
      case 'FILED':
      case 'PENDING':
        return 'bg-blue-50 text-blue-800 border-blue-200';
      case 'SCHEDULED':
        return 'bg-purple-50 text-purple-800 border-purple-200';
      case 'HEARD':
        return 'bg-cyan-50 text-cyan-800 border-cyan-200';
      case 'DISPOSED':
        return 'bg-emerald-50 text-emerald-800 border-emerald-200';
      case 'ADJOURNED':
        return 'bg-amber-50 text-amber-800 border-amber-200';
      default:
        return 'bg-slate-50 text-slate-700 border-slate-200';
    }
  };

  const filteredDisplayCases = searchTerm
    ? cases.filter((c) =>
        c.caseNumber.toLowerCase().includes(searchTerm.toLowerCase())
      )
    : cases;

  return (
    <div className="space-y-5">
      {/* Page Title & Filter Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 tracking-tight">Case Registry & Urgency Docket</h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Total of {totalElements} registered matters. Click any row for explainable factor breakdown.
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
            className="p-2 border border-slate-300 rounded-lg text-slate-600 hover:bg-slate-50 text-xs flex items-center gap-1"
          >
            <RotateCcw className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Reset</span>
          </button>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex flex-col md:flex-row gap-3 items-stretch md:items-center justify-between">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search by Case Number (e.g. SYN-2026-)..."
            className="w-full text-xs rounded-lg border border-slate-300 pl-9 pr-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <div className="flex items-center gap-1.5 text-xs text-slate-600">
            <Filter className="w-3.5 h-3.5 text-slate-400" />
            <span>Type:</span>
            <select
              value={selectedType}
              onChange={(e) => {
                setSelectedType(e.target.value);
                setPage(0);
              }}
              className="text-xs rounded-md border border-slate-300 px-2.5 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">All Types</option>
              <option value="BAIL">Bail Matters</option>
              <option value="POCSO">POCSO / Special Act</option>
              <option value="MATRIMONIAL">Matrimonial</option>
              <option value="CIVIL">Civil Suits</option>
              <option value="CRIMINAL_OTHER">General Criminal</option>
            </select>
          </div>

          <div className="flex items-center gap-1.5 text-xs text-slate-600">
            <span>Status:</span>
            <select
              value={selectedStatus}
              onChange={(e) => {
                setSelectedStatus(e.target.value);
                setPage(0);
              }}
              className="text-xs rounded-md border border-slate-300 px-2.5 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        {error && (
          <div className="p-4 bg-red-50 border-b border-red-200 flex items-center gap-2 text-red-800 text-xs">
            <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
            <thead className="bg-slate-50 text-slate-600 font-semibold uppercase tracking-wider text-[11px]">
              <tr>
                <th scope="col" className="px-4 py-3 cursor-pointer" onClick={() => handleSortToggle('caseNumber')}>
                  <div className="flex items-center gap-1">
                    <span>Case Number</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th scope="col" className="px-4 py-3">Case Type</th>
                <th scope="col" className="px-4 py-3 cursor-pointer" onClick={() => handleSortToggle('currentStatus')}>
                  <div className="flex items-center gap-1">
                    <span>Status</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th scope="col" className="px-4 py-3 cursor-pointer" onClick={() => handleSortToggle('filingDate')}>
                  <div className="flex items-center gap-1">
                    <span>Filing Date</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th scope="col" className="px-4 py-3 cursor-pointer" onClick={() => handleSortToggle('priorAdjournments')}>
                  <div className="flex items-center gap-1">
                    <span>Adjournments</span>
                    <ArrowUpDown className="w-3 h-3 text-slate-400" />
                  </div>
                </th>
                <th scope="col" className="px-4 py-3">Urgency Score</th>
                <th scope="col" className="px-4 py-3">Next Scheduled Hearing</th>
                <th scope="col" className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {isLoading ? (
                <tr>
                  <td colSpan={8} className="py-16 text-center text-slate-500">
                    <div className="w-6 h-6 border-2 border-blue-900 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    Loading registry records...
                  </td>
                </tr>
              ) : filteredDisplayCases.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-slate-400 italic">
                    No cases match the selected filters.
                  </td>
                </tr>
              ) : (
                filteredDisplayCases.map((c) => (
                  <tr
                    key={c.id}
                    onClick={() => onSelectCase(c.id)}
                    className="hover:bg-blue-50/40 cursor-pointer transition-colors group"
                  >
                    <td className="px-4 py-3.5 font-mono font-bold text-slate-900">
                      {c.caseNumber}
                    </td>
                    <td className="px-4 py-3.5 font-medium text-slate-700">
                      <span className="px-2 py-0.5 rounded bg-slate-100 text-[11px]">
                        {c.caseType}
                      </span>
                    </td>
                    <td className="px-4 py-3.5">
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border ${statusBadgeColor(
                          c.currentStatus
                        )}`}
                      >
                        {c.currentStatus}
                      </span>
                    </td>
                    <td className="px-4 py-3.5 text-slate-600">
                      {c.filingDate ? new Date(c.filingDate).toLocaleDateString('en-IN') : 'N/A'}
                    </td>
                    <td className="px-4 py-3.5 text-slate-600 font-medium">
                      {c.priorAdjournments > 0 ? (
                        <span className="text-amber-700 font-semibold">{c.priorAdjournments}</span>
                      ) : (
                        <span className="text-slate-400">0</span>
                      )}
                    </td>
                    <td className="px-4 py-3.5">
                      {c.priorityScore != null ? (
                        <span
                          className={`px-2.5 py-1 rounded-md text-xs font-mono font-bold border shadow-2xs ${scoreBadgeColor(
                            c.priorityScore
                          )}`}
                        >
                          {c.priorityScore.toFixed(1)}
                        </span>
                      ) : (
                        <span className="text-slate-400 text-xs italic">Unscored</span>
                      )}
                    </td>
                    <td className="px-4 py-3.5 text-slate-700">
                      {c.nextHearingDate ? (
                        <span className="inline-flex items-center gap-1 font-medium text-indigo-950">
                          <Calendar className="w-3.5 h-3.5 text-indigo-600" />
                          {new Date(c.nextHearingDate).toLocaleDateString('en-IN')}
                        </span>
                      ) : (
                        <span className="text-slate-400 italic">Unscheduled</span>
                      )}
                    </td>
                    <td className="px-4 py-3.5 text-right space-x-2">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          onSelectCase(c.id);
                        }}
                        className="p-1.5 rounded hover:bg-slate-100 text-slate-600 hover:text-blue-900"
                        title="View breakdown & score history"
                      >
                        <Eye className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination controls */}
        <div className="bg-slate-50 px-4 py-3 border-t border-slate-200 flex items-center justify-between text-xs text-slate-600">
          <div>
            Showing Page <strong>{page + 1}</strong> of <strong>{Math.max(1, totalPages)}</strong> ({totalElements} cases)
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0 || isLoading}
              className="px-3 py-1.5 border border-slate-300 rounded-md bg-white hover:bg-slate-50 disabled:opacity-50 flex items-center gap-1"
            >
              <ChevronLeft className="w-3.5 h-3.5" /> Previous
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || isLoading}
              className="px-3 py-1.5 border border-slate-300 rounded-md bg-white hover:bg-slate-50 disabled:opacity-50 flex items-center gap-1"
            >
              Next <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
