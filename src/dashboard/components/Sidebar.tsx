
import React from 'react';
import { Moon, Activity, Settings, Code, LogOut } from 'lucide-react';

interface SidebarProps {
  onLogout: () => void;
  onShowGuide: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ onLogout, onShowGuide }) => {
  return (
    <aside className="w-full md:w-64 bg-slate-950 border-r border-slate-800 flex flex-col shrink-0">
      <div className="p-6 flex items-center gap-3 border-b border-slate-800">
        <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center">
          <Moon className="w-5 h-5 text-white" />
        </div>
        <span className="font-bold text-lg text-slate-100 tracking-tight">Werewolf<span className="text-indigo-400">Helper</span></span>
      </div>

      <nav className="flex-1 p-4 space-y-1">
        <button className="w-full flex items-center gap-3 px-4 py-3 bg-indigo-600/10 text-indigo-300 rounded-lg border border-indigo-600/20">
          <Activity className="w-5 h-5" />
          <span className="font-medium">Dashboard</span>
        </button>
        <button className="w-full flex items-center gap-3 px-4 py-3 text-slate-400 hover:text-slate-200 hover:bg-slate-800 rounded-lg transition-colors">
          <Settings className="w-5 h-5" />
          <span className="font-medium">Game Settings</span>
        </button>
        <button 
          onClick={onShowGuide}
          className="w-full flex items-center gap-3 px-4 py-3 text-slate-400 hover:text-slate-200 hover:bg-slate-800 rounded-lg transition-colors"
        >
          <Code className="w-5 h-5" />
          <span className="font-medium">Integration Guide</span>
        </button>
      </nav>

      <div className="p-4 border-t border-slate-800">
        <div className="flex items-center gap-3 mb-4 px-2">
          <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          <span className="text-xs font-medium text-emerald-500 uppercase tracking-wider">Bot Connected</span>
        </div>
        <button onClick={onLogout} className="w-full flex items-center gap-3 px-4 py-2 text-slate-500 hover:text-red-400 transition-colors">
          <LogOut className="w-4 h-4" />
          <span className="text-sm font-medium">Sign Out</span>
        </button>
      </div>
    </aside>
  );
};
