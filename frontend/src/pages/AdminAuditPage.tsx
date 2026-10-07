import React, { useState, useEffect } from 'react';
import { AuditLogEntry } from '../types/api';
import { api } from '../api/client';
import {
  ChevronLeft,
  ChevronRight,
  Code,
  X,
  RefreshCw,
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
        return 'bg-emerald-50 text-emerald-800 border-emerald-200';
      case 'UPDATE':
        return 'bg-blue-50 text-blue-800 border-blue-200';
      case 'DELETE':
        return 'bg-red-50 text-red-800 border-red-200';
      case 'APPROVE':
        return 'bg-purple-50 text-purple-800 border-purple-200';
      case 'REJECT':
        return 'bg-rose-50 text-rose-800 border-rose-200';
      case 'REASSIGN':
        return 'bg-amber-50 text-amber-800 border-amber-200';
      case 'SCHEDULING_RUN':
        return 'bg-indigo-50 text-indigo-800 border-indigo-200';
      default:
        return 'bg-slate-50 text-slate-700 border-slate-200';
    }
  };

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-xl font-bold text-slate-900 tracking-tight">
              Immutable Judicial Audit Trail
            </h2>
            <span className="px-2.5 py-0.5 rounded-full bg-purple-100 text-purple-800 text-[10px] font-bold uppercase tracking-wider border border-purple-200">
              Admin Only
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-0.5">
            Append-only cryptographic record of all case updates, proposal approvals, overrides, and engine runs.
          </p>
        </div>

        <button
          onClick={fetchAuditLogs}
          disabled={isLoading}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-white border border-slate-300 rounded-lg text-xs font-medium text-slate-700 hover:bg-slate-50 shadow-2xs self-start sm:self-auto"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          Refresh Audit Log
        </button>
      </div>

      {/* Filter Bar */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-xs grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3 text-xs">
        <div>
          <label className="block text-slate-500 mb-1">Actor Username</label>
          <input
            type="text"
            value={actorFilter}
            onChange={(e) => {
              setActorFilter(e.target.value);
              setPage(0);
            }}
            placeholder="e.g. registrarAlice or SYSTEM"
            className="w-full rounded-md border border-slate-300 px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        <div>
          <label className="block text-slate-500 mb-1">Action</label>
          <select
            value={actionFilter}
            onChange={(e) => {
              setActionFilter(e.target.value);
              setPage(0);
            }}
            className="w-full rounded-md border border-slate-300 px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
          <label className="block text-slate-500 mb-1">Entity Type</label>
          <select
            value={entityTypeFilter}
            onChange={(e) => {
              setEntityTypeFilter(e.target.value);
              setPage(0);
            }}
            className="w-full rounded-md border border-slate-300 px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-blue-500"
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
          <label className="block text-slate-500 mb-1">Entity ID (Partial/Exact)</label>
          <input
            type="text"
            value={entityIdFilter}
            onChange={(e) => {
              setEntityIdFilter(e.target.value);
              setPage(0);
            }}
            placeholder="e.g. case-101 or UUID"
            className="w-full rounded-md border border-slate-300 px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>
      </div>

      {/* Audit Log Table */}
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
            <tbody className="divide-y divide-slate-100 bg-white">
              {isLoading ? (
                <tr>
                  <td colSpan={8} className="py-16 text-center text-slate-500">
                    <div className="w-6 h-6 border-2 border-blue-900 border-t-transparent rounded-full animate-spin mx-auto mb-2"></div>
                    Loading audit trail records...
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-slate-400 italic">
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
                      className="hover:bg-slate-50/60 transition-colors cursor-pointer"
                      onClick={() => hasStateDiff && setActiveDiffEntry(entry)}
                    >
                      <td className="px-4 py-3 text-slate-600 whitespace-nowrap font-mono text-[11px]">
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
                        <div className="font-semibold text-slate-900">{entry.actorUsername}</div>
                        <span className="text-[10px] uppercase font-bold text-slate-500">
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
                      <td className="px-4 py-3 font-medium text-slate-800">
                        {entry.entityType}
                      </td>
                      <td className="px-4 py-3 font-mono text-[11px] text-slate-600 truncate max-w-[120px]" title={entry.entityId}>
                        {entry.entityId}
                      </td>
                      <td className="px-4 py-3 font-mono text-[10px] text-slate-500">
                        {entry.reasonCode}
                      </td>
                      <td className="px-4 py-3 text-slate-700 max-w-xs truncate" title={entry.details || ''}>
                        {entry.details || '—'}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {hasStateDiff ? (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setActiveDiffEntry(entry);
                            }}
                            className="inline-flex items-center gap-1 px-2 py-1 text-[11px] font-semibold text-blue-900 bg-blue-50 border border-blue-200 rounded hover:bg-blue-100"
                          >
                            <Code className="w-3 h-3" /> Diff
                          </button>
                        ) : (
                          <span className="text-slate-400 text-[10px]">—</span>
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
        <div className="bg-slate-50 px-4 py-3 border-t border-slate-200 flex items-center justify-between text-xs text-slate-600">
          <div>
            Page <strong>{page + 1}</strong> of <strong>{Math.max(1, totalPages)}</strong> ({totalElements} total entries)
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

      {/* Before / After JSON Diff Modal */}
      {activeDiffEntry && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-2xl max-w-3xl w-full border border-slate-200 overflow-hidden flex flex-col max-h-[85vh] animate-in fade-in">
            <div className="bg-slate-900 px-6 py-4 text-white flex items-center justify-between">
              <div>
                <h3 className="text-sm font-semibold flex items-center gap-2">
                  <span>State Transition Diff:</span>
                  <span className="font-mono text-amber-300">
                    {activeDiffEntry.entityType} ({activeDiffEntry.entityId})
                  </span>
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Action: {activeDiffEntry.action} | Performed by: {activeDiffEntry.actorUsername} ({activeDiffEntry.actorRole})
                </p>
              </div>
              <button
                onClick={() => setActiveDiffEntry(null)}
                className="text-slate-400 hover:text-white p-1 rounded-md"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 overflow-y-auto flex-1 space-y-4 text-xs">
              {activeDiffEntry.details && (
                <div className="p-3 bg-slate-50 rounded-lg border border-slate-200">
                  <span className="font-semibold text-slate-700">Audit Justification: </span>
                  <span className="text-slate-600">{activeDiffEntry.details}</span>
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Before State */}
                <div className="space-y-1.5">
                  <div className="font-semibold text-slate-700 flex items-center gap-1.5 text-xs">
                    <span className="w-2 h-2 rounded-full bg-amber-500"></span>
                    Before State (JSONB)
                  </div>
                  <pre className="p-3 bg-slate-900 text-slate-100 rounded-lg font-mono text-[11px] overflow-x-auto max-h-80 border border-slate-800 leading-relaxed">
                    {activeDiffEntry.beforeState
                      ? JSON.stringify(activeDiffEntry.beforeState, null, 2)
                      : '// No prior state (CREATE or Initial Record)'}
                  </pre>
                </div>

                {/* After State */}
                <div className="space-y-1.5">
                  <div className="font-semibold text-slate-700 flex items-center gap-1.5 text-xs">
                    <span className="w-2 h-2 rounded-full bg-emerald-500"></span>
                    After State (JSONB)
                  </div>
                  <pre className="p-3 bg-slate-900 text-emerald-300 rounded-lg font-mono text-[11px] overflow-x-auto max-h-80 border border-slate-800 leading-relaxed">
                    {activeDiffEntry.afterState
                      ? JSON.stringify(activeDiffEntry.afterState, null, 2)
                      : '// Entity deleted'}
                  </pre>
                </div>
              </div>
            </div>

            <div className="px-6 py-3 bg-slate-50 border-t border-slate-200 flex justify-end">
              <button
                onClick={() => setActiveDiffEntry(null)}
                className="px-4 py-1.5 bg-slate-800 text-white rounded-md text-xs font-medium hover:bg-slate-700"
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
