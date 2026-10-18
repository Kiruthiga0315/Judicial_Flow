import React, { useState, useEffect } from 'react';
import { AgingReportItem } from '../types/api';
import { api } from '../api/client';
import { AlertTriangle, Filter, Eye, RefreshCw, Clock, ShieldAlert, Zap } from 'lucide-react';
import { NumberCounter } from '../components/NumberCounter';

interface AgingReportPageProps {
  onSelectCase: (caseId: string) => void;
}

export const AgingReportPage: React.FC<AgingReportPageProps> = ({ onSelectCase }) => {
  const [items, setItems] = useState<AgingReportItem[]>([]);
  const [stats, setStats] = useState<any>(null);
  const [caseTypeFilter, setCaseTypeFilter] = useState<string>('');
  const [limit, setLimit] = useState<number>(50);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchAgingData = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [data, statsData] = await Promise.all([
        api.getAgingReport(caseTypeFilter || undefined, limit),
        api.getRegistryStats(caseTypeFilter || undefined).catch(() => null)
      ]);
      setItems(data || []);
      if (statsData) {
        setStats(statsData);
      }
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
    if (score >= 70) return 'bg-[#E65100] text-white border-[#BF360C] font-extrabold shadow-xs';
    if (score >= 45) return 'bg-[#F9D68A] text-[#201F35] border-[#D4A746] font-extrabold shadow-2xs';
    return 'bg-[#D2DE9E] text-[#1E2E08] border-[#8A9E48] font-bold shadow-2xs';
  };

  const extremeAgingCount = stats ? stats.extremePendencyCount : items.filter((i) => i.daysPending > 180).length;
  const moderateAgingCount = stats ? stats.elevatedAgingCount : items.filter((i) => i.daysPending >= 90 && i.daysPending <= 180).length;
  const statutoryAlertCount = stats ? stats.imminentStatutoryCount : items.filter((i) => i.daysToDeadline != null && i.daysToDeadline <= 15).length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl sm:text-2xl font-bold text-jf-ink tracking-tight">
              Pendency & Aging Report (At-Risk Docket)
            </h2>
            <span className="px-2.5 py-0.5 rounded-full bg-jf-orange text-white text-[10px] font-extrabold uppercase tracking-wide border border-jf-orange shadow-2xs">
              At-Risk Watch
            </span>
          </div>
          <p className="text-xs text-jf-ink-muted mt-0.5">
            Prioritized ranking of cases exhibiting statutory deadline proximity, extensive pendency, or high adjournments.
          </p>
        </div>

        <button
          onClick={fetchAgingData}
          disabled={isLoading}
          className="inline-flex items-center gap-1.5 px-3.5 py-2 bg-jf-cream border border-jf-border-subtle rounded-lg text-xs font-semibold text-jf-ink hover:bg-white shadow-2xs self-start sm:self-auto jf-focus-ring transition-colors"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin text-jf-violet' : 'text-jf-violet'}`} />
          Recalculate
        </button>
      </div>

      {/* Pendency KPI Overview Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-gradient-to-br from-jf-cream to-jf-orange/10 rounded-2xl p-4.5 border-2 border-jf-orange/40 shadow-card jf-card-interactive hover:shadow-card-hover space-y-1.5 relative overflow-hidden animate-stagger-1">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-orange" />
          <div className="flex items-center justify-between pt-0.5">
            <span className="text-[11px] font-bold text-jf-text-orange uppercase tracking-wider flex items-center gap-1.5">
              <span className="relative flex h-2 w-2">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-jf-orange opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-jf-orange"></span>
              </span>
              Extreme Pendency (&gt;180d)
            </span>
            <span className="p-1.5 rounded-lg bg-jf-orange/20 text-jf-text-orange shadow-2xs">
              <Zap className="w-4 h-4 text-jf-orange" />
            </span>
          </div>
          <div className="text-3xl font-extrabold text-jf-text-orange font-mono tabular-nums tracking-tight">
            <NumberCounter value={extremeAgingCount} duration={1200} />
          </div>
          <p className="text-[11px] text-jf-ink-muted font-medium">Cases in prolonged queue delay</p>
        </div>

        <div className="bg-gradient-to-br from-jf-cream to-jf-apricot/15 rounded-2xl p-4.5 border-2 border-jf-apricot/60 shadow-card jf-card-interactive hover:shadow-card-hover space-y-1.5 relative overflow-hidden animate-stagger-2">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-apricot" />
          <div className="flex items-center justify-between pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink uppercase tracking-wider">Elevated Aging (90–180d)</span>
            <span className="p-1.5 rounded-lg bg-jf-apricot/40 text-jf-ink shadow-2xs">
              <Clock className="w-4 h-4 text-jf-ink" />
            </span>
          </div>
          <div className="text-3xl font-extrabold text-jf-ink font-mono tabular-nums tracking-tight">
            <NumberCounter value={moderateAgingCount} duration={1200} />
          </div>
          <p className="text-[11px] text-jf-ink-muted font-medium">Monitored for anti-starvation boost</p>
        </div>

        <div className="bg-jf-cream rounded-2xl p-4.5 border-2 border-jf-border-subtle shadow-card jf-card-interactive hover:shadow-card-hover space-y-1.5 relative overflow-hidden animate-stagger-3">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-violet" />
          <div className="flex items-center justify-between pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink-muted uppercase tracking-wider">Imminent Statutory Limits</span>
            <span className="p-1.5 rounded-lg bg-jf-violet/10 text-jf-violet shadow-2xs">
              <ShieldAlert className="w-4 h-4" />
            </span>
          </div>
          <div className="text-3xl font-extrabold text-jf-ink font-mono tabular-nums tracking-tight">
            <NumberCounter value={statutoryAlertCount} duration={1200} />
          </div>
          <p className="text-[11px] text-jf-ink-muted font-medium">Deadline within next 15 days</p>
        </div>
      </div>

      {/* Filter controls */}
      <div className="bg-jf-cream p-4.5 rounded-2xl border-2 border-jf-border-subtle shadow-card flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2 text-xs text-jf-ink font-semibold">
          <Filter className="w-3.5 h-3.5 text-jf-violet" />
          <span>Filter by Case Type:</span>
          <select
            value={caseTypeFilter}
            onChange={(e) => setCaseTypeFilter(e.target.value)}
            className="text-xs rounded-md border-2 border-jf-border-subtle px-3 py-1.5 bg-white text-jf-ink focus:border-jf-violet jf-focus-ring transition-colors shadow-2xs"
          >
            <option value="">All Case Types (Court-Wide)</option>
            <option value="BAIL">Bail Matters (Statutory Liberty)</option>
            <option value="POCSO">POCSO (Expedited 1-Year Target)</option>
            <option value="MATRIMONIAL">Matrimonial (Family Court)</option>
            <option value="CIVIL">Civil Suits (Commercial / Title)</option>
            <option value="CRIMINAL_OTHER">General Criminal</option>
          </select>
        </div>

        <div className="flex items-center gap-2 text-xs text-jf-ink font-semibold">
          <span>Top Display Limit:</span>
          <select
            value={limit}
            onChange={(e) => setLimit(Number(e.target.value))}
            className="text-xs rounded-md border-2 border-jf-border-subtle px-2.5 py-1.5 bg-white text-jf-ink focus:border-jf-violet jf-focus-ring transition-colors shadow-2xs"
          >
            <option value={25}>Top 25 At-Risk</option>
            <option value={50}>Top 50 At-Risk</option>
            <option value={100}>Top 100 At-Risk</option>
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="bg-jf-cream rounded-2xl border-2 border-jf-border-subtle shadow-card overflow-hidden">
        {error && (
          <div className="p-4 bg-jf-cream-darker border-b border-jf-text-orange/25 text-xs text-jf-text-orange font-medium">
            {error}
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y-2 divide-jf-border-subtle text-left text-xs">
            <thead className="bg-gradient-to-r from-jf-violet/15 via-jf-lavender/25 to-jf-violet/10 border-b-2 border-jf-violet/30 text-jf-violet font-bold uppercase tracking-wider text-[11px]">
              <tr>
                <th scope="col" className="px-4 py-3.5">Rank & Urgency</th>
                <th scope="col" className="px-4 py-3.5">Case Number</th>
                <th scope="col" className="px-4 py-3.5">Type</th>
                <th scope="col" className="px-4 py-3.5">Days Pending</th>
                <th scope="col" className="px-4 py-3.5">Prior Adjournments</th>
                <th scope="col" className="px-4 py-3.5">Statutory Deadline</th>
                <th scope="col" className="px-4 py-3.5">Status</th>
                <th scope="col" className="px-4 py-3.5 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-jf-border-subtle/70 bg-jf-cream">
              {isLoading ? (
                <tr>
                  <td colSpan={8} className="py-16 text-center text-jf-ink-muted">
                    <div className="w-6 h-6 border-2 border-jf-violet border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    Generating pendency metrics...
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-jf-ink-muted italic">
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
                      className="hover:bg-jf-lavender/25 cursor-pointer transition-colors"
                    >
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-jf-ink-muted/70 font-bold w-5 tabular-nums">
                            #{index + 1}
                          </span>
                          <span
                            className={`px-2.5 py-1 rounded-md text-xs font-mono border tabular-nums shadow-2xs ${scoreBadgeColor(
                              item.priorityScore
                            )}`}
                          >
                            {item.priorityScore.toFixed(1)}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5 font-mono font-bold text-jf-ink tabular-nums">
                        {item.caseNumber}
                      </td>
                      <td className="px-4 py-3.5">
                        <span className="px-2 py-0.5 rounded bg-jf-cream-darker text-[11px] font-medium text-jf-ink border border-jf-border-subtle">
                          {item.caseType}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-jf-ink font-medium tabular-nums">
                        <span className={item.daysPending > 365 ? 'text-jf-text-orange font-bold' : ''}>
                          {item.daysPending} days ({Math.round(item.daysPending / 30)} mos)
                        </span>
                      </td>
                      <td className="px-4 py-3.5 tabular-nums">
                        <span
                          className={`font-semibold ${
                            isHighAdjournments ? 'text-jf-text-orange font-bold' : 'text-jf-ink-muted'
                          }`}
                        >
                          {item.adjournments} times
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        {item.statutoryDeadline ? (
                          <div
                            className={`inline-flex items-center gap-1 font-medium tabular-nums ${
                              isCriticalDeadline ? 'text-jf-text-orange font-bold' : 'text-jf-ink'
                            }`}
                          >
                            {isCriticalDeadline && <AlertTriangle className="w-3.5 h-3.5 text-jf-orange shrink-0" />}
                            <span>{new Date(item.statutoryDeadline).toLocaleDateString('en-IN')}</span>
                            {item.daysToDeadline != null && (
                              <span className="text-[10px] text-jf-ink-muted/80">
                                ({item.daysToDeadline > 0 ? `${item.daysToDeadline}d left` : 'Overdue'})
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-jf-ink-muted/60 italic">None configured</span>
                        )}
                      </td>
                      <td className="px-4 py-3.5">
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-jf-lavender/30 text-jf-violet border border-jf-violet/25">
                          {item.status}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            onSelectCase(item.caseId);
                          }}
                          className="inline-flex items-center gap-1.5 px-3 py-1 rounded-md bg-[#FAF9FE] border-2 border-[#434164] text-[#434164] hover:bg-[#434164] hover:text-white font-bold text-[11px] jf-focus-ring hover:-translate-y-0.5 hover:shadow-xs active:translate-y-0 transition-all shadow-2xs group/btn cursor-pointer"
                          title="View explainable factors"
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
      </div>
    </div>
  );
};
