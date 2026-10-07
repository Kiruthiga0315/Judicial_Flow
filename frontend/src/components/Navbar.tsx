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

interface NavbarProps {
  currentTab: string;
  onSelectTab: (tab: string) => void;
}

export const Navbar: React.FC<NavbarProps> = ({ currentTab, onSelectTab }) => {
  const { user, role, logout } = useAuth();

  const navItems = [
    { id: 'cases', label: 'Cases & Registry', icon: Briefcase, roles: ['ADMIN', 'REGISTRAR', 'JUDGE'] },
    { id: 'aging', label: 'Aging & Urgency Report', icon: Clock, roles: ['ADMIN', 'REGISTRAR', 'JUDGE'] },
    { id: 'schedule', label: 'Cause List & Schedule', icon: Calendar, roles: ['ADMIN', 'REGISTRAR', 'JUDGE'] },
    { id: 'proposals', label: 'Proposed Allocations', icon: FileCheck, roles: ['ADMIN', 'REGISTRAR'] },
    { id: 'audit', label: 'Audit Log', icon: ShieldAlert, roles: ['ADMIN'] },
    { id: 'batch', label: 'Nightly Batch Engine', icon: Sliders, roles: ['ADMIN'] },
  ];

  const visibleItems = navItems.filter((item) => role && item.roles.includes(role));

  const roleBadgeColor =
    role === 'ADMIN'
      ? 'bg-purple-100 text-purple-800 border-purple-200'
      : role === 'REGISTRAR'
      ? 'bg-blue-100 text-blue-800 border-blue-200'
      : 'bg-emerald-100 text-emerald-800 border-emerald-200';

  return (
    <header className="bg-white border-b border-slate-200 shadow-sm sticky top-0 z-30">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center space-x-6">
            <div className="flex items-center space-x-3 cursor-pointer" onClick={() => onSelectTab('cases')}>
              <div className="h-10 w-10 rounded-lg bg-blue-900 flex items-center justify-center text-white font-bold text-lg shadow-sm">
                JF
              </div>
              <div>
                <h1 className="text-lg font-bold text-slate-900 tracking-tight leading-tight">JudicialFlow</h1>
                <p className="text-xs text-slate-500 font-medium">Court Registry & Bench Management</p>
              </div>
            </div>

            <nav className="hidden md:flex space-x-1">
              {visibleItems.map((item) => {
                const Icon = item.icon;
                const isActive = currentTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => onSelectTab(item.id)}
                    className={`inline-flex items-center px-3 py-2 rounded-md text-sm font-medium transition-colors ${
                      isActive
                        ? 'bg-blue-50 text-blue-900 border border-blue-200/60 shadow-xs'
                        : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                    }`}
                  >
                    <Icon className={`w-4 h-4 mr-2 ${isActive ? 'text-blue-700' : 'text-slate-400'}`} />
                    {item.label}
                  </button>
                );
              })}
            </nav>
          </div>

          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-2 text-right">
              <div>
                <div className="text-sm font-semibold text-slate-900 flex items-center justify-end gap-1.5">
                  <UserCheck className="w-3.5 h-3.5 text-slate-500" />
                  <span>{user?.username}</span>
                </div>
                <div className="flex items-center justify-end mt-0.5">
                  <span className={`text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded-full border ${roleBadgeColor}`}>
                    {role}
                  </span>
                </div>
              </div>
            </div>

            <button
              onClick={logout}
              title="Logout from session"
              className="inline-flex items-center px-3 py-1.5 border border-slate-300 rounded-md text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 hover:text-red-700 hover:border-red-300 transition-colors"
            >
              <LogOut className="w-3.5 h-3.5 mr-1 text-slate-400" />
              Sign Out
            </button>
          </div>
        </div>
      </div>

      {/* Mobile navigation row */}
      <div className="md:hidden border-t border-slate-200 px-4 py-2 flex overflow-x-auto space-x-1">
        {visibleItems.map((item) => (
          <button
            key={item.id}
            onClick={() => onSelectTab(item.id)}
            className={`px-3 py-1.5 rounded-md text-xs font-medium whitespace-nowrap ${
              currentTab === item.id
                ? 'bg-blue-900 text-white'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>
    </header>
  );
};
