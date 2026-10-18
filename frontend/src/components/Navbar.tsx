import React from 'react';
import { useAuth } from '../context/AuthContext';
import {
  Briefcase,
  Calendar,
  Clock,
  FileCheck,
  ShieldAlert,
  Sliders,
  LogOut,
  UserCheck,
} from 'lucide-react';
import { JudicialLogomark } from './JudicialLogomark';

interface NavbarProps {
  currentTab: string;
  onSelectTab: (tab: string) => void;
}

export const Navbar: React.FC<NavbarProps> = ({ currentTab, onSelectTab }) => {
  const { user, role, logout } = useAuth();

  const navItems = [
    { id: 'cases', label: 'Cases & Registry', icon: Briefcase, roles: ['ADMIN', 'REGISTRAR', 'JUDGE'] },
    { id: 'register', label: 'Register Case', icon: FileCheck, roles: ['ADMIN', 'REGISTRAR'] },
    { id: 'aging', label: 'Aging & Urgency Report', icon: Clock, roles: ['ADMIN', 'REGISTRAR', 'JUDGE'] },
    { id: 'schedule', label: 'Cause List & Schedule', icon: Calendar, roles: ['ADMIN', 'REGISTRAR', 'JUDGE'] },
    { id: 'proposals', label: 'Proposed Allocations', icon: FileCheck, roles: ['ADMIN', 'REGISTRAR'] },
    { id: 'audit', label: 'Audit Log', icon: ShieldAlert, roles: ['ADMIN'] },
    { id: 'batch', label: 'Nightly Batch Engine', icon: Sliders, roles: ['ADMIN'] },
  ];

  const visibleItems = navItems.filter((item) => role && item.roles.includes(role));

  const roleBadgeColor =
    role === 'ADMIN'
      ? 'bg-jf-apricot text-jf-ink border-jf-apricot/80 font-bold shadow-2xs'
      : role === 'REGISTRAR'
      ? 'bg-jf-lavender text-jf-violet border-jf-violet/40 font-bold shadow-2xs'
      : 'bg-jf-sage text-jf-ink border-jf-sage/80 font-bold shadow-2xs';

  return (
    <header className="bg-jf-cream/95 backdrop-blur-md border-b border-jf-border-subtle shadow-card-soft sticky top-0 z-30">
      {/* Signature institutional brand accent strip */}
      <div className="h-1 w-full bg-gradient-to-r from-jf-violet via-jf-orange to-jf-apricot" aria-hidden="true" />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center space-x-6">
            <div className="flex items-center gap-3">
              <div
                className="flex items-center cursor-pointer select-none py-1 hover:opacity-90 transition-opacity"
                onClick={() => onSelectTab('cases')}
                title="Go to Cases & Registry"
              >
                <JudicialLogomark size="sm" />
              </div>

              <div className="hidden xl:flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-jf-sage/20 border border-jf-sage/40 text-[11px] font-semibold text-jf-ink select-none">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-jf-sage opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-jf-sage"></span>
                </span>
                <span>Registry Live</span>
              </div>
            </div>

            <nav className="hidden md:flex items-center space-x-1.5" aria-label="Main Navigation">
              {visibleItems.map((item) => {
                const Icon = item.icon;
                const isActive = currentTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => onSelectTab(item.id)}
                    className={`relative inline-flex items-center px-3.5 py-2 rounded-lg text-sm transition-all duration-200 jf-focus-ring ${
                      isActive
                        ? 'bg-jf-violet text-jf-cream shadow-md shadow-jf-violet/20 font-semibold -translate-y-0.5'
                        : 'text-jf-ink-muted hover:text-jf-ink hover:bg-jf-cream-darker/70 hover:scale-[1.01] font-medium'
                    }`}
                  >
                    <Icon className={`w-4 h-4 mr-2 ${isActive ? 'text-jf-apricot' : 'text-jf-violet/60'}`} />
                    {item.label}
                  </button>
                );
              })}
            </nav>
          </div>

          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-2 text-right">
              <div>
                <div className="text-sm font-bold text-jf-ink flex items-center justify-end gap-1.5">
                  <UserCheck className="w-3.5 h-3.5 text-jf-violet" />
                  <span>{user?.username}</span>
                </div>
                <div className="flex items-center justify-end mt-0.5">
                  <span className={`text-[10px] uppercase tracking-wider px-2.5 py-0.5 rounded-full border ${roleBadgeColor}`}>
                    {role}
                  </span>
                </div>
              </div>
            </div>

            <button
              onClick={logout}
              title="Logout from session"
              className="inline-flex items-center px-3.5 py-1.5 border border-jf-border-subtle rounded-lg text-xs font-semibold text-jf-ink bg-jf-cream hover:bg-jf-orange/10 hover:text-jf-text-orange hover:border-jf-orange/40 jf-focus-ring transition-all shadow-2xs"
            >
              <LogOut className="w-3.5 h-3.5 mr-1 text-jf-violet" />
              Sign Out
            </button>
          </div>
        </div>
      </div>

      {/* Mobile navigation row */}
      <div className="md:hidden border-t border-jf-border-subtle px-4 py-2 flex overflow-x-auto space-x-1.5 bg-jf-cream">
        {visibleItems.map((item) => (
          <button
            key={item.id}
            onClick={() => onSelectTab(item.id)}
            className={`px-3 py-1.5 rounded-md text-xs whitespace-nowrap jf-focus-ring ${
              currentTab === item.id
                ? 'bg-jf-violet text-jf-cream font-bold shadow-xs'
                : 'text-jf-ink-muted hover:bg-jf-cream-darker font-medium'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>
    </header>
  );
};
