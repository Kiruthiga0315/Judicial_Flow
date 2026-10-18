import React, { useState } from 'react';
import { useAuth } from './context/AuthContext';
import { Navbar } from './components/Navbar';
import { Footer } from './components/Footer';
import { LoginPage } from './pages/LoginPage';
import { CaseListPage } from './pages/CaseListPage';
import { RegisterCasePage } from './pages/RegisterCasePage';
import { AgingReportPage } from './pages/AgingReportPage';
import { SchedulePage } from './pages/SchedulePage';
import { ProposalsPage } from './pages/ProposalsPage';
import { AdminAuditPage } from './pages/AdminAuditPage';
import { AdminBatchPage } from './pages/AdminBatchPage';
import { CaseDetailDrawer } from './components/CaseDetailDrawer';
import { ReassignModal } from './components/ReassignModal';
import { Hearing } from './types/api';
import bgBrush from './assets/bg-brush.jpeg';

export const App: React.FC = () => {
  const { user, role } = useAuth();
  const [currentTab, setCurrentTab] = useState<string>('cases');

  // Modals state
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(null);
  const [reassignHearing, setReassignHearing] = useState<Hearing | null>(null);
  const [refreshTrigger, setRefreshTrigger] = useState<number>(0);

  // If not authenticated, present Login Page
  if (!user) {
    return <LoginPage />;
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
    <div className="flex flex-col min-h-screen relative bg-jf-canvas text-jf-ink font-sans selection:bg-jf-apricot selection:text-jf-ink overflow-x-hidden">
      {/* Background Layer 1: Tactile canvas texture matching login */}
      <div
        className="fixed inset-0 bg-cover bg-center pointer-events-none opacity-[0.22] mix-blend-multiply z-0"
        style={{ backgroundImage: `url(${bgBrush})` }}
        aria-hidden="true"
      />

      {/* Background Layer 2: Atmospheric radial gradient scrim */}
      <div
        className="fixed inset-0 pointer-events-none bg-[radial-gradient(ellipse_100%_60%_at_50%_0%,rgba(196,195,227,0.45),rgba(253,248,226,0.25)_45%,rgba(246,245,251,0)_85%)] z-0"
        aria-hidden="true"
      />

      {/* Foreground application structure */}
      <div className="relative z-10 flex flex-col flex-1">
        <Navbar currentTab={currentTab} onSelectTab={handleSelectTab} />

        <main key={currentTab} className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-6 animate-jf-page-enter">
        {currentTab === 'register' && (role === 'ADMIN' || role === 'REGISTRAR') && (
          <RegisterCasePage onRegistered={() => setCurrentTab('cases')} />
        )}

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
    </div>
  );
};
