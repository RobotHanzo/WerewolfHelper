import { MessageSquare, AlertTriangle } from 'lucide-react';
import { LogEntry } from '../types';
import { useTranslation } from '../lib/i18n';

interface GameLogProps {
  logs: LogEntry[];
  onClear: () => void;
  onManualCommand: (cmd: string) => void;
}

export const GameLog: React.FC<GameLogProps> = ({ logs, onClear, onManualCommand }) => {
  const { t } = useTranslation();
  const inputStyle = "bg-slate-100 dark:bg-slate-950 border border-slate-300 dark:border-slate-700 rounded-lg px-4 py-2 text-slate-900 dark:text-slate-200 focus:outline-none focus:border-indigo-500 w-full";

  return (
    <div className="flex flex-col gap-4 bg-white/50 dark:bg-slate-950/50 rounded-xl border border-slate-300 dark:border-slate-800 p-4 overflow-hidden h-[calc(100vh-140px)]">
      <div className="flex items-center justify-between border-b border-slate-300 dark:border-slate-800 pb-3">
        <h2 className="font-bold text-slate-800 dark:text-slate-200 flex items-center gap-2">
          <MessageSquare className="w-4 h-4 text-indigo-500 dark:text-indigo-400" /> {t('gameLog.title')}
        </h2>
        <button onClick={onClear} className="text-xs text-slate-500 hover:text-slate-700 dark:hover:text-slate-300">{t('gameLog.clear')}</button>
      </div>

      <div className="flex-1 overflow-y-auto space-y-3 pr-2 scrollbar-thin scrollbar-thumb-slate-300 dark:scrollbar-thumb-slate-700 scrollbar-track-transparent">
        {logs.map(log => (
          <div key={log.id} className="text-sm flex gap-2 animate-in fade-in slide-in-from-bottom-2 duration-300">
            <span className="text-slate-400 dark:text-slate-600 font-mono text-xs pt-0.5">{log.timestamp}</span>
            <div className={`flex-1 ${log.type === 'alert' ? 'text-yellow-600 dark:text-yellow-400 font-bold' :
                log.type === 'action' ? 'text-indigo-600 dark:text-indigo-300' :
                  'text-slate-700 dark:text-slate-300'
              }`}>
              {log.type === 'alert' && <AlertTriangle className="w-3 h-3 inline mr-1" />}
              {log.message}
            </div>
          </div>
        ))}
      </div>

      {/* Console Input */}
      <div className="mt-auto pt-3 border-t border-slate-300 dark:border-slate-800">
        <input
          type="text"
          placeholder={t('gameLog.placeholder')}
          className={inputStyle}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              onManualCommand(e.currentTarget.value);
              e.currentTarget.value = '';
            }
          }}
        />
      </div>
    </div>
  );
};
