import React, { useState } from 'react';
import { useAuth } from './context/AuthContext';
import { Navbar } from './components/Navbar';
import { Footer } from './components/Footer';
import { LoginPage } from './pages/LoginPage';
import { CaseListPage } from './pages/CaseListPage';
import { AgingReportPage } from './pages/AgingReportPage';
import { SchedulePage } from './pages/SchedulePage';
import { ProposalsPage } from './pages/ProposalsPage';
import { AdminAuditPage } from './pages/AdminAuditPage';
import { AdminBatchPage } from './pages/AdminBatchPage';
import { CaseDetailDrawer } from './components/CaseDetailDrawer';
import { ReassignModal } from './components/ReassignModal';
import { Hearing } from './types/api';

export const App: React.FC = () => {
  const { user, role } = useAuth();
  const [currentTab, setCurrentTab] = useState<string>('cases');

  // Modals state
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(null);
  const [reassignHearing, setReassignHearing] = useState<Hearing | null>(null);
  const [refreshTrigger, setRefreshTrigger] = useState<number>(0);

  // If not authenticated, present Login Page
  if (!user) {
    return (
      <div className="flex flex-col min-h-screen">
        <div className="flex-1">
          <LoginPage />
        </div>
        <Footer />
      </div>
    );
  }

  // Role Guard validation for tabs
  const handleSelectTab = (tab: string) => {
    if ((tab === 'audit' || tab === 'batch') && role !== 'ADMIN') {
      setCurrentTab('cases');
      return;
    }
    if (tab === 'proposals' && role !== 'ADMIN' && role !== 'REGISTRAR') {
      setCurrentTab('cases');
      return;
    }
    setCurrentTab(tab);
  };

  return (
    <div className="flex flex-col min-h-screen bg-slate-50 text-slate-900 font-sans">
      <Navbar currentTab={currentTab} onSelectTab={handleSelectTab} />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6">
        {currentTab === 'cases' && (
          <CaseListPage
            key={refreshTrigger}
            onSelectCase={(id) => setSelectedCaseId(id)}
          />
        )}

        {currentTab === 'aging' && (
          <AgingReportPage
            onSelectCase={(id) => setSelectedCaseId(id)}
          />
        )}

        {currentTab === 'schedule' && (
          <SchedulePage
            key={refreshTrigger}
            onSelectCase={(id) => setSelectedCaseId(id)}
            onOpenReassign={(h) => setReassignHearing(h)}
          />
        )}

        {currentTab === 'proposals' && (role === 'ADMIN' || role === 'REGISTRAR') && (
          <ProposalsPage
            key={refreshTrigger}
            onSelectCase={(id) => setSelectedCaseId(id)}
          />
        )}

        {currentTab === 'audit' && role === 'ADMIN' && (
          <AdminAuditPage />
        )}

        {currentTab === 'batch' && role === 'ADMIN' && (
          <AdminBatchPage />
        )}
      </main>

      {/* Case Details Drawer */}
      {selectedCaseId && (
        <CaseDetailDrawer
          caseId={selectedCaseId}
          onClose={() => setSelectedCaseId(null)}
          onReassignHearing={(h) => {
            setSelectedCaseId(null);
            setReassignHearing(h);
          }}
          onHearingUpdated={() => {
            setRefreshTrigger((v) => v + 1);
          }}
        />
      )}

      {/* Hearing Reassignment Modal */}
      {reassignHearing && (
        <ReassignModal
          hearing={reassignHearing}
          onClose={() => setReassignHearing(null)}
          onSuccess={() => {
            setRefreshTrigger((v) => v + 1);
          }}
        />
      )}

      <Footer />
    </div>
  );
};
