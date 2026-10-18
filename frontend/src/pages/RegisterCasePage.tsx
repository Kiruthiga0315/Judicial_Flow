import React, { useState } from 'react';
import { api } from '../api/client';
import { SearchableCaseSelect } from '../components/SearchableCaseSelect';
import { Case } from '../types/api';
export interface RegisterCaseResponse { caseDetails: any; initialScore: number; scoreBreakdown: any[]; }
import {
  AlertCircle,
  CheckCircle,
  
  Calendar,
  AlertTriangle,
  FileCheck,
  Mail,
  Scale
} from 'lucide-react';

interface Props {
  onRegistered: () => void;
}

export const RegisterCasePage: React.FC<Props> = ({ onRegistered }) => {
  const [caseType, setCaseType] = useState('CIVIL');
  const [filingDate, setFilingDate] = useState(new Date().toISOString().split('T')[0]);
  const [statutoryDeadline, setStatutoryDeadline] = useState('');
  const [litigantEmail, setLitigantEmail] = useState('');
  const [linkedCases, setLinkedCases] = useState<Case[]>([]);

  const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [successData, setSuccessData] = useState<RegisterCaseResponse | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
        setError(null);
    setFieldErrors({});
    setSuccessData(null);

    try {
      let linkedCaseIds: string[] = [];
      if (linkedCases.length > 0) {
        linkedCaseIds = linkedCases.map(c => c.id);
      }

      const res: any = await api.request('/api/v1/cases/register', { method: 'POST', body: JSON.stringify({
        caseType,
        filingDate,
        statutoryDeadline: statutoryDeadline ? statutoryDeadline : undefined,
        linkedCaseIds: linkedCaseIds.length > 0 ? linkedCaseIds : undefined,
        litigantContactEmail: litigantEmail ? litigantEmail : undefined,
      })});

      setSuccessData(res);
        } catch (err: any) {
      if (err.status === 409) {
        setError('Case conflict: An identical case or duplicate case number already exists.');
      } else if (err.fieldErrors) {
        setFieldErrors(err.fieldErrors);
        setError('Please fix the validation errors below.');
      } else {
        setError(err.message || err.message || 'Registration failed');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    setSuccessData(null);
    setCaseType('CIVIL');
    setFilingDate(new Date().toISOString().split('T')[0]);
    setStatutoryDeadline('');
    setLitigantEmail('');
    setLinkedCases([]);
  };

  if (successData) {
    return (
      <div className="max-w-2xl mx-auto p-6 bg-white rounded-xl shadow-card border border-jf-border-subtle animate-jf-fade-in">
        <div className="text-center mb-6">
          <CheckCircle className="w-16 h-16 text-jf-sage mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-jf-ink">Case Registered Successfully</h2>
          <p className="text-jf-ink-muted mt-2">
            This case will be included in the next scheduling run.
          </p>
        </div>

        <div className="bg-jf-canvas rounded-lg p-4 mb-6 border border-jf-border-subtle">
          <div className="grid grid-cols-2 gap-4 mb-4">
            <div>
              <p className="text-sm text-jf-ink-muted">Case Number</p>
              <p className="text-lg font-bold text-jf-ink">{successData.caseDetails.caseNumber}</p>
            </div>
            <div>
              <p className="text-sm text-jf-ink-muted">Initial Priority Score</p>
              <p className="text-lg font-bold text-jf-apricot">{successData.initialScore.toFixed(2)}</p>
            </div>
          </div>
          
          <div className="border-t border-jf-border-subtle pt-4">
            <h4 className="text-sm font-semibold mb-2">Score Breakdown</h4>
            <ul className="space-y-2 text-sm text-jf-ink-muted">
              {successData.scoreBreakdown.map((f: any, idx: number) => (
                <li key={idx} className="flex justify-between border-b border-jf-border-subtle/50 pb-1">
                  <span>{f.factorName}</span>
                  <span className="font-medium text-jf-ink">+{f.contribution.toFixed(2)}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <div className="flex gap-4">
          <button
            onClick={handleReset}
            className="flex-1 py-2 px-4 border border-jf-violet text-jf-violet font-semibold rounded-lg hover:bg-jf-lavender"
          >
            Register Another Case
          </button>
          <button
            onClick={onRegistered}
            className="flex-1 py-2 px-4 bg-jf-violet text-white font-semibold rounded-lg hover:bg-jf-violet/90 shadow-md"
          >
            View Cases
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl md:text-3xl font-black tracking-tight text-jf-ink flex items-center">
          <FileCheck className="w-8 h-8 mr-3 text-jf-violet" />
          Register New Case
        </h1>
        <p className="mt-2 text-jf-ink-muted">
          Intake a newly filed case into the JudicialFlow system.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="bg-white p-6 rounded-xl shadow-card border border-jf-border-subtle space-y-6">
        {error && (
          <div className="p-4 bg-red-50 text-red-700 rounded-lg flex items-start text-sm">
            <AlertCircle className="w-5 h-5 mr-2 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-bold text-jf-ink mb-1">
              Case Type <span className="text-jf-orange">*</span>
            </label>
            <select
              value={caseType}
              onChange={(e) => setCaseType(e.target.value)}
              className="w-full px-3 py-2 border border-jf-border-subtle rounded-lg focus:ring-2 focus:ring-jf-violet focus:border-jf-violet text-sm"
              required
            >
              <option value="BAIL">Bail</option>
              <option value="POCSO">POCSO</option>
              <option value="MATRIMONIAL">Matrimonial</option>
              <option value="CIVIL">Civil</option>
                            <option value="CRIMINAL_OTHER">Criminal (Other)</option>
            </select>
            {fieldErrors.caseType && <p className="text-xs text-red-600 mt-1">{fieldErrors.caseType}</p>}
          </div>

          <div>
            <label className="block text-sm font-bold text-jf-ink mb-1 flex items-center gap-1">
              <Calendar className="w-4 h-4 text-jf-ink-muted" /> Filing Date <span className="text-jf-orange">*</span>
            </label>
            <input
              type="date"
              value={filingDate}
              max={new Date().toISOString().split('T')[0]}
              onChange={(e) => setFilingDate(e.target.value)}
              className="w-full px-3 py-2 border border-jf-border-subtle rounded-lg focus:ring-2 focus:ring-jf-violet text-sm"
                            required
            />
            {fieldErrors.filingDate && <p className="text-xs text-red-600 mt-1">{fieldErrors.filingDate}</p>}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-bold text-jf-ink mb-1 flex items-center gap-1">
              <AlertTriangle className="w-4 h-4 text-jf-ink-muted" /> Statutory Deadline (Optional)
            </label>
            <input
              type="date"
              value={statutoryDeadline}
              min={filingDate}
              onChange={(e) => setStatutoryDeadline(e.target.value)}
                            className="w-full px-3 py-2 border border-jf-border-subtle rounded-lg focus:ring-2 focus:ring-jf-violet text-sm"
            />
            <p className="text-xs text-jf-ink-muted mt-1">Must be on or after filing date.</p>
            {fieldErrors.statutoryDeadline && <p className="text-xs text-red-600 mt-1">{fieldErrors.statutoryDeadline}</p>}
          </div>

          <div>
            <label className="block text-sm font-bold text-jf-ink mb-1 flex items-center gap-1">
              <Mail className="w-4 h-4 text-jf-ink-muted" /> Litigant Email (Optional)
            </label>
            <input
              type="email"
              value={litigantEmail}
              onChange={(e) => setLitigantEmail(e.target.value)}
              placeholder="litigant@example.com"
                            className="w-full px-3 py-2 border border-jf-border-subtle rounded-lg focus:ring-2 focus:ring-jf-violet text-sm"
            />
            <p className="text-xs text-jf-ink-muted mt-1">For registration notifications.</p>
            {fieldErrors.litigantContactEmail && <p className="text-xs text-red-600 mt-1">{fieldErrors.litigantContactEmail}</p>}
          </div>
        </div>

        <div>
          <label className="block text-sm font-bold text-jf-ink mb-1 flex items-center gap-1">
            <Scale className="w-4 h-4 text-jf-ink-muted" /> Linked Cases UUIDs (Optional)
          </label>
          
          <SearchableCaseSelect
            selectedCases={linkedCases}
            onChange={setLinkedCases}
          />

          <p className="text-xs text-jf-ink-muted mt-1">Comma-separated case UUIDs.</p>
        </div>

        <div className="pt-4 border-t border-jf-border-subtle">
          <button
            type="submit"
            disabled={loading}
            className="w-full md:w-auto px-6 py-2.5 bg-jf-violet hover:bg-jf-violet/90 text-white font-bold rounded-lg shadow-md transition-all flex justify-center items-center jf-focus-ring disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? (
              <span className="flex items-center gap-2">
                <span className="w-4 h-4 border-2 border-white/20 border-t-white rounded-full animate-spin" />
                Registering...
              </span>
            ) : (
              'Register Case'
            )}
          </button>
        </div>
      </form>
    </div>
  );
};
