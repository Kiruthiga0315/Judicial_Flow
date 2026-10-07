import React, { useState, useEffect } from 'react';
import { AgingReportItem } from '../types/api';
import { api } from '../api/client';
import { AlertTriangle, Filter, Eye, RefreshCw } from 'lucide-react';

interface AgingReportPageProps {
  onSelectCase: (caseId: string) => void;
}

export const AgingReportPage: React.FC<AgingReportPageProps> = ({ onSelectCase }) => {
  const [items, setItems] = useState<AgingReportItem[]>([]);
  const [caseTypeFilter, setCaseTypeFilter] = useState<string>('');
  const [limit, setLimit] = useState<number>(50);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAgingData = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await api.getAgingReport(caseTypeFilter || undefined, limit);
      setItems(data || []);
    } catch (err: any) {
      setError(err.message || 'Failed to generate aging report');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchAgingData();
  }, [caseTypeFilter, limit]);

  const scoreBadgeColor = (score: number) => {
    if (score >= 70) return 'bg-red-50 text-red-800 border-red-300 font-bold';
    if (score >= 45) return 'bg-amber-50 text-amber-800 border-amber-300 font-semibold';
    return 'bg-emerald-50 text-emerald-800 border-emerald-300';
  };

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl font-bold text-slate-900 tracking-tight">
              Pendency & Aging Report (At-Risk Docket)
            </h2>
            <span className="px-2 py-0.5 rounded-full bg-red-100 text-red-800 text-[10px] font-bold uppercase tracking-wide border border-red-200">
              High Pendency
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            Prioritized ranking of cases exhibiting statutory deadline proximity, extensive pendency, or high adjournments.
          </p>
        </div>

        <button
          onClick={fetchAgingData}
          disabled={isLoading}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-white border border-slate-300 rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-50 shadow-2xs self-start sm:self-auto"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          Recalculate
        </button>
      </div>

      {/* Filter controls */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-xs text-slate-700">
          <Filter className="w-3.5 h-3.5 text-slate-400" />
          <span className="font-medium">Filter by Case Type:</span>
          <select
            value={caseTypeFilter}
            onChange={(e) => setCaseTypeFilter(e.target.value)}
            className="text-xs rounded-md border border-slate-300 px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">All Case Types (Court-Wide)</option>
            <option value="BAIL">Bail Matters (Statutory Liberty)</option>
            <option value="POCSO">POCSO (Expedited 1-Year Target)</option>
            <option value="MATRIMONIAL">Matrimonial (Family Court)</option>
            <option value="CIVIL">Civil Suits (Commercial / Title)</option>
            <option value="CRIMINAL_OTHER">General Criminal</option>
          </select>
        </div>

        <div className="flex items-center gap-2 text-xs text-slate-500">
          <span>Top Display Limit:</span>
          <select
            value={limit}
            onChange={(e) => setLimit(Number(e.target.value))}
            className="text-xs rounded-md border border-slate-300 px-2 py-1 bg-white focus:outline-none"
          >
            <option value={25}>Top 25 At-Risk</option>
            <option value={50}>Top 50 At-Risk</option>
            <option value={100}>Top 100 At-Risk</option>
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
        {error && (
          <div className="p-4 bg-red-50 border-b border-red-200 text-xs text-red-800">
            {error}
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left text-xs">
            <thead className="bg-slate-50 text-slate-600 font-semibold uppercase tracking-wider text-[11px]">
              <tr>
                <th scope="col" className="px-4 py-3">Rank & Urgency</th>
                <th scope="col" className="px-4 py-3">Case Number</th>
                <th scope="col" className="px-4 py-3">Type</th>
                <th scope="col" className="px-4 py-3">Days Pending</th>
                <th scope="col" className="px-4 py-3">Prior Adjournments</th>
                <th scope="col" className="px-4 py-3">Statutory Deadline</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {isLoading ? (
                <tr>
                  <td colSpan={8} className="py-16 text-center text-slate-500">
                    <div className="w-6 h-6 border-2 border-blue-900 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    Generating pendency metrics...
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-slate-400 italic">
                    No open cases found matching criteria.
                  </td>
                </tr>
              ) : (
                items.map((item, index) => {
                  const isCriticalDeadline =
                    item.daysToDeadline != null && item.daysToDeadline <= 15;
                  const isHighAdjournments = item.adjournments >= 10;

                  return (
                    <tr
                      key={item.caseId}
                      onClick={() => onSelectCase(item.caseId)}
                      className="hover:bg-amber-50/40 cursor-pointer transition-colors"
                    >
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-slate-400 font-bold w-5">
                            #{index + 1}
                          </span>
                          <span
                            className={`px-2.5 py-1 rounded-md text-xs font-mono border ${scoreBadgeColor(
                              item.priorityScore
                            )}`}
                          >
                            {item.priorityScore.toFixed(1)}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5 font-mono font-bold text-slate-900">
                        {item.caseNumber}
                      </td>
                      <td className="px-4 py-3.5">
                        <span className="px-2 py-0.5 rounded bg-slate-100 text-[11px] font-medium text-slate-700">
                          {item.caseType}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-slate-700 font-medium">
                        <span className={item.daysPending > 365 ? 'text-red-700 font-bold' : ''}>
                          {item.daysPending} days ({Math.round(item.daysPending / 30)} mos)
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <span
                          className={`font-semibold ${
                            isHighAdjournments ? 'text-amber-800' : 'text-slate-600'
                          }`}
                        >
                          {item.adjournments} times
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        {item.statutoryDeadline ? (
                          <div
                            className={`inline-flex items-center gap-1 font-medium ${
                              isCriticalDeadline ? 'text-red-700 font-bold' : 'text-slate-700'
                            }`}
                          >
                            {isCriticalDeadline && <AlertTriangle className="w-3.5 h-3.5 text-red-600" />}
                            <span>{new Date(item.statutoryDeadline).toLocaleDateString('en-IN')}</span>
                            {item.daysToDeadline != null && (
                              <span className="text-[10px] text-slate-400">
                                ({item.daysToDeadline > 0 ? `${item.daysToDeadline}d left` : 'Overdue'})
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-slate-400 italic">None configured</span>
                        )}
                      </td>
                      <td className="px-4 py-3.5">
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-blue-50 text-blue-800 border border-blue-200">
                          {item.status}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            onSelectCase(item.caseId);
                          }}
                          className="p-1 rounded text-slate-500 hover:text-blue-900"
                          title="View factors"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
