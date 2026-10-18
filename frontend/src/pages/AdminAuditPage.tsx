import React, { useState, useEffect } from 'react';
import { AuditLogEntry } from '../types/api';
import { api } from '../api/client';
import {
  ChevronLeft,
  ChevronRight,
  Code,
  X,
  RefreshCw,
  ShieldCheck,
  FileText,
  Database,
} from 'lucide-react';

export const AdminAuditPage: React.FC = () => {
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [pageSize] = useState<number>(20);

  // Filters
  const [actorFilter, setActorFilter] = useState<string>('');
  const [actionFilter, setActionFilter] = useState<string>('');
  const [entityTypeFilter, setEntityTypeFilter] = useState<string>('');
  const [entityIdFilter, setEntityIdFilter] = useState<string>('');

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // JSON Diff Modal state
  const [activeDiffEntry, setActiveDiffEntry] = useState<AuditLogEntry | null>(null);

  const fetchAuditLogs = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await api.listAuditLogs(
        page,
        pageSize,
        actorFilter.trim() || undefined,
        actionFilter.trim() || undefined,
        entityTypeFilter.trim() || undefined,
        entityIdFilter.trim() || undefined
      );
      setLogs(res.content || []);
      setTotalPages(res.totalPages || 0);
      setTotalElements(res.totalElements || 0);
    } catch (err: any) {
      setError(err.message || 'Failed to fetch audit log entries');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchAuditLogs();
  }, [page, actorFilter, actionFilter, entityTypeFilter, entityIdFilter]);

  const actionBadgeColor = (action: string) => {
    switch (action) {
      case 'CREATE':
        return 'bg-jf-sage/20 text-jf-ink border-jf-sage/50 font-bold';
      case 'UPDATE':
        return 'bg-jf-lavender/30 text-jf-ink border-jf-lavender/60 font-bold';
      case 'DELETE':
        return 'bg-jf-orange/15 text-jf-text-orange border-jf-orange/40 font-bold';
      case 'APPROVE':
        return 'bg-jf-violet/15 text-jf-violet border-jf-violet/40 font-bold';
      case 'REJECT':
        return 'bg-jf-orange/15 text-jf-text-orange border-jf-orange/40 font-bold';
      case 'REASSIGN':
        return 'bg-jf-apricot/40 text-jf-ink border-jf-apricot/70 font-bold';
      case 'SCHEDULING_RUN':
        return 'bg-jf-violet/20 text-jf-violet border-jf-violet/50 font-bold';
      default:
        return 'bg-jf-canvas text-jf-ink-muted border-jf-border-subtle font-medium';
    }
  };

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl sm:text-2xl font-bold text-jf-ink tracking-tight">
              Immutable Judicial Audit Trail
            </h2>
            <span className="px-2.5 py-0.5 rounded-full bg-jf-apricot/40 text-jf-ink text-[10px] font-bold uppercase tracking-wider border border-jf-apricot/70">
              Admin Only
            </span>
          </div>
          <p className="text-xs text-jf-ink-muted mt-0.5">
            Append-only cryptographic record of all case updates, proposal approvals, overrides, and engine runs.
          </p>
        </div>

        <button
          onClick={fetchAuditLogs}
          disabled={isLoading}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-jf-cream border border-jf-border-subtle rounded-lg text-xs font-semibold text-jf-ink hover:bg-white shadow-2xs self-start sm:self-auto transition-colors jf-focus-ring"
        >
          <RefreshCw className={`w-3.5 h-3.5 text-jf-violet ${isLoading ? 'animate-spin' : ''}`} />
          Refresh Audit Log
        </button>
      </div>

      {/* Audit Activity & Integrity KPI Strip */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-jf-cream rounded-2xl border-2 border-jf-border-subtle p-4.5 shadow-card jf-card-interactive hover:shadow-card-hover relative overflow-hidden flex items-center justify-between animate-stagger-1">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-violet" />
          <div className="pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink-muted block uppercase tracking-wider">
              Total Logged Records
            </span>
            <span className="text-3xl font-extrabold text-jf-ink font-mono tabular-nums tracking-tight">
              {totalElements}
            </span>
            <span className="text-[11px] text-jf-violet font-semibold block mt-0.5">
              Historical ledger
            </span>
          </div>
          <div className="w-10 h-10 rounded-xl bg-jf-lavender/35 border border-jf-lavender/60 flex items-center justify-center text-jf-violet shrink-0 shadow-2xs">
            <FileText className="w-5 h-5" />
          </div>
        </div>

        <div className="bg-jf-cream rounded-2xl border-2 border-jf-border-subtle p-4.5 shadow-card jf-card-interactive hover:shadow-card-hover relative overflow-hidden flex items-center justify-between animate-stagger-2">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-apricot" />
          <div className="pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink-muted block uppercase tracking-wider">
              Current Page Slice
            </span>
            <span className="text-3xl font-extrabold text-jf-ink font-mono tabular-nums tracking-tight">
              {logs.length} <span className="text-xs font-medium text-jf-ink-muted">/ {pageSize} per page</span>
            </span>
            <span className="text-[11px] text-jf-ink-muted font-medium block mt-0.5">
              Page {page + 1} of {Math.max(1, totalPages)}
            </span>
          </div>
          <div className="w-10 h-10 rounded-xl bg-jf-apricot/30 border border-jf-apricot/60 flex items-center justify-center text-jf-ink shrink-0 shadow-2xs">
            <Database className="w-5 h-5" />
          </div>
        </div>

        <div className="bg-jf-cream rounded-2xl border-2 border-jf-border-subtle p-4.5 shadow-card jf-card-interactive hover:shadow-card-hover relative overflow-hidden flex items-center justify-between animate-stagger-3">
          <div className="absolute top-0 inset-x-0 h-1 bg-jf-sage" />
          <div className="pt-0.5">
            <span className="text-[11px] font-bold text-jf-ink-muted block uppercase tracking-wider">
              Cryptographic Integrity
            </span>
            <span className="text-lg font-bold text-jf-ink block mt-1">
              Append-Only Tamper-Evident
            </span>
            <span className="text-[11px] text-jf-ink-muted font-medium block mt-0.5">
              Zero state modification permitted
            </span>
          </div>
          <div className="w-10 h-10 rounded-xl bg-jf-sage/30 border border-jf-sage/60 flex items-center justify-center text-jf-ink shrink-0 shadow-2xs">
            <ShieldCheck className="w-5 h-5 text-jf-sage" />
          </div>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bg-jf-cream p-4.5 rounded-2xl border-2 border-jf-border-subtle shadow-card grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3 text-xs">
        <div>
          <label className="block text-jf-ink-muted mb-1 font-medium">Actor Username</label>
          <input
            type="text"
            value={actorFilter}
            onChange={(e) => {
              setActorFilter(e.target.value);
              setPage(0);
            }}
            placeholder="e.g. registrarAlice or SYSTEM"
            className="w-full rounded-md border-2 border-jf-border-subtle bg-white px-3 py-1.5 text-jf-ink focus:outline-none jf-focus-ring shadow-2xs"
          />
        </div>

        <div>
          <label className="block text-jf-ink-muted mb-1 font-medium">Action</label>
          <select
            value={actionFilter}
            onChange={(e) => {
              setActionFilter(e.target.value);
              setPage(0);
            }}
            className="w-full rounded-md border-2 border-jf-border-subtle px-3 py-1.5 bg-white text-jf-ink focus:outline-none jf-focus-ring shadow-2xs"
          >
            <option value="">All Actions</option>
            <option value="CREATE">CREATE</option>
            <option value="UPDATE">UPDATE</option>
            <option value="DELETE">DELETE</option>
            <option value="APPROVE">APPROVE (Proposal)</option>
            <option value="REJECT">REJECT (Proposal)</option>
            <option value="REASSIGN">REASSIGN (Override)</option>
            <option value="SCHEDULING_RUN">SCHEDULING_RUN (Batch)</option>
          </select>
        </div>

        <div>
          <label className="block text-jf-ink-muted mb-1 font-medium">Entity Type</label>
          <select
            value={entityTypeFilter}
            onChange={(e) => {
              setEntityTypeFilter(e.target.value);
              setPage(0);
            }}
            className="w-full rounded-md border-2 border-jf-border-subtle px-3 py-1.5 bg-white text-jf-ink focus:outline-none jf-focus-ring shadow-2xs"
          >
            <option value="">All Entities</option>
            <option value="Case">Case</option>
            <option value="Judge">Judge</option>
            <option value="Courtroom">Courtroom</option>
            <option value="Proposal">Proposal</option>
            <option value="Hearing">Hearing</option>
            <option value="SchedulingRun">SchedulingRun</option>
            <option value="User">User</option>
          </select>
        </div>

        <div>
          <label className="block text-jf-ink-muted mb-1 font-medium">Entity ID (Partial/Exact)</label>
          <input
            type="text"
            value={entityIdFilter}
            onChange={(e) => {
              setEntityIdFilter(e.target.value);
              setPage(0);
            }}
            placeholder="e.g. case-101 or UUID"
            className="w-full rounded-md border-2 border-jf-border-subtle bg-white px-3 py-1.5 text-jf-ink focus:outline-none jf-focus-ring shadow-2xs"
          />
        </div>
      </div>

      {/* Audit Log Table */}
      <div className="bg-jf-cream rounded-2xl border-2 border-jf-border-subtle shadow-card overflow-hidden">
        {error && (
          <div className="p-4 bg-jf-orange/10 border-b border-jf-orange/30 text-xs text-jf-text-orange font-semibold">
            {error}
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="min-w-full divide-y-2 divide-jf-border-subtle text-left text-xs">
            <thead className="bg-gradient-to-r from-jf-violet/15 via-jf-lavender/25 to-jf-violet/10 border-b-2 border-jf-violet/30 text-jf-violet font-bold uppercase tracking-wider text-[11px]">
              <tr>
                <th scope="col" className="px-4 py-3">Timestamp (UTC / IST)</th>
                <th scope="col" className="px-4 py-3">Actor & Role</th>
                <th scope="col" className="px-4 py-3">Action</th>
                <th scope="col" className="px-4 py-3">Entity Type</th>
                <th scope="col" className="px-4 py-3">Entity ID</th>
                <th scope="col" className="px-4 py-3">Reason Code</th>
                <th scope="col" className="px-4 py-3">Details / Justification</th>
                <th scope="col" className="px-4 py-3 text-right">Diff</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-jf-border-subtle bg-jf-cream">
              {isLoading ? (
                <tr>
                  <td colSpan={8} className="py-16 text-center text-jf-ink-muted">
                    <div className="w-6 h-6 border-2 border-jf-violet border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    Loading audit trail records...
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-jf-ink-muted italic">
                    No audit records match the current filters.
                  </td>
                </tr>
              ) : (
                logs.map((entry) => {
                  const entryDate = new Date(entry.timestamp);
                  const hasStateDiff = entry.beforeState || entry.afterState;

                  return (
                    <tr
                      key={entry.id}
                      className="hover:bg-jf-canvas/60 transition-colors cursor-pointer"
                      onClick={() => hasStateDiff && setActiveDiffEntry(entry)}
                    >
                      <td className="px-4 py-3 text-jf-ink-muted whitespace-nowrap font-mono tabular-nums text-[11px]">
                        {entryDate.toLocaleString('en-IN', {
                          day: '2-digit',
                          month: 'short',
                          year: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                          second: '2-digit',
                          timeZone: 'UTC',
                        })}
                      </td>
                      <td className="px-4 py-3">
                        <div className="font-semibold text-jf-ink">{entry.actorUsername}</div>
                        <span className="text-[10px] uppercase font-bold text-jf-ink-muted">
                          {entry.actorRole}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider border ${actionBadgeColor(
                            entry.action
                          )}`}
                        >
                          {entry.action}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-medium text-jf-ink">
                        {entry.entityType}
                      </td>
                      <td className="px-4 py-3 font-mono tabular-nums text-[11px] text-jf-ink-muted truncate max-w-[120px]" title={entry.entityId}>
                        {entry.entityId}
                      </td>
                      <td className="px-4 py-3 font-mono text-[10px] text-jf-ink-muted">
                        {entry.reasonCode}
                      </td>
                      <td className="px-4 py-3 text-jf-ink max-w-xs truncate" title={entry.details || ''}>
                        {entry.details || '—'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {hasStateDiff ? (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setActiveDiffEntry(entry);
                            }}
                            className="inline-flex items-center gap-1.5 px-2.5 py-1 text-[11px] font-bold text-jf-cream bg-jf-violet rounded-md hover:bg-jf-violet/90 transition-colors shadow-2xs jf-focus-ring"
                          >
                            <Code className="w-3 h-3 text-jf-apricot" /> Diff
                          </button>
                        ) : (
                          <span className="text-jf-ink-muted text-[10px]">—</span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="bg-jf-canvas/70 px-4 py-3 border-t border-jf-border-subtle flex items-center justify-between text-xs text-jf-ink-muted">
          <div>
            Page <strong className="text-jf-ink font-semibold">{page + 1}</strong> of <strong className="text-jf-ink font-semibold">{Math.max(1, totalPages)}</strong> ({totalElements} total entries)
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0 || isLoading}
              className="px-3 py-1.5 border border-jf-border-subtle rounded-md bg-jf-cream hover:bg-white text-jf-ink disabled:opacity-50 flex items-center gap-1 transition-colors jf-focus-ring"
            >
              <ChevronLeft className="w-3.5 h-3.5" /> Previous
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1 || isLoading}
              className="px-3 py-1.5 border border-jf-border-subtle rounded-md bg-jf-cream hover:bg-white text-jf-ink disabled:opacity-50 flex items-center gap-1 transition-colors jf-focus-ring"
            >
              Next <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* Before / After JSON Diff Modal */}
      {activeDiffEntry && (
        <div className="fixed inset-0 z-50 bg-jf-ink/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-jf-cream rounded-xl shadow-2xl max-w-3xl w-full border border-jf-border-subtle overflow-hidden flex flex-col max-h-[85vh] animate-in fade-in">
            <div className="bg-jf-ink px-6 py-4 text-white flex items-center justify-between">
              <div>
                <h3 className="text-sm font-semibold flex items-center gap-2">
                  <span>State Transition Diff:</span>
                  <span className="font-mono text-jf-apricot">
                    {activeDiffEntry.entityType} ({activeDiffEntry.entityId})
                  </span>
                </h3>
                <p className="text-xs text-jf-lavender/80 mt-0.5">
                  Action: {activeDiffEntry.action} | Performed by: {activeDiffEntry.actorUsername} ({activeDiffEntry.actorRole})
                </p>
              </div>
              <button
                onClick={() => setActiveDiffEntry(null)}
                className="text-jf-lavender hover:text-white p-1 rounded-md transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 overflow-y-auto flex-1 space-y-4 text-xs">
              {activeDiffEntry.details && (
                <div className="p-3 bg-white rounded-lg border border-jf-border-subtle">
                  <span className="font-semibold text-jf-ink">Audit Justification: </span>
                  <span className="text-jf-ink-muted">{activeDiffEntry.details}</span>
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Before State */}
                <div className="space-y-1.5">
                  <div className="font-semibold text-jf-ink flex items-center gap-1.5 text-xs">
                    <span className="w-2 h-2 rounded-full bg-jf-orange"></span>
                    Before State (JSONB)
                  </div>
                  <pre className="p-3 bg-[#1E1D33] text-jf-lavender rounded-lg font-mono text-[11px] overflow-x-auto max-h-80 border border-jf-ink/30 leading-relaxed">
                    {activeDiffEntry.beforeState
                      ? JSON.stringify(activeDiffEntry.beforeState, null, 2)
                      : '// No prior state (CREATE or Initial Record)'}
                  </pre>
                </div>

                {/* After State */}
                <div className="space-y-1.5">
                  <div className="font-semibold text-jf-ink flex items-center gap-1.5 text-xs">
                    <span className="w-2 h-2 rounded-full bg-jf-sage"></span>
                    After State (JSONB)
                  </div>
                  <pre className="p-3 bg-[#1E1D33] text-emerald-300 rounded-lg font-mono text-[11px] overflow-x-auto max-h-80 border border-jf-ink/30 leading-relaxed">
                    {activeDiffEntry.afterState
                      ? JSON.stringify(activeDiffEntry.afterState, null, 2)
                      : '// Entity deleted'}
                  </pre>
                </div>
              </div>
            </div>

            <div className="px-6 py-3 bg-jf-canvas/70 border-t border-jf-border-subtle flex justify-end">
              <button
                onClick={() => setActiveDiffEntry(null)}
                className="px-4 py-1.5 jf-btn-primary rounded-md text-xs font-semibold"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
