import { useState } from 'react';
import { MessageSquare, AlertTriangle } from 'lucide-react';
import { LogEntry } from '../types';
import { useTranslation } from '../lib/i18n';

interface GameLogProps {
  logs: LogEntry[];
  onGlobalAction: (action: string) => void;
  readonly?: boolean;
  className?: string;
}

export const GameLog: React.FC<GameLogProps> = ({ logs, onGlobalAction, readonly = false, className = "" }) => {
  const { t } = useTranslation();
  const [resetConfirming, setResetConfirming] = useState(false);

  return (
    <div className={`flex flex-col gap-4 bg-white/50 dark:bg-slate-950/50 rounded-xl border border-slate-300 dark:border-slate-800 p-4 overflow-hidden ${className || 'h-[calc(100vh-140px)]'}`}>
      <div className="flex items-center justify-between border-b border-slate-300 dark:border-slate-800 pb-3">
        <h2 className="font-bold text-slate-800 dark:text-slate-200 flex items-center gap-2">
          <MessageSquare className="w-4 h-4 text-indigo-500 dark:text-indigo-400" /> {t('gameLog.title')}
        </h2>
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

      {/* Admin Actions */}
      {!readonly && (
        <div className="mt-auto pt-3 border-t border-slate-300 dark:border-slate-800">
          <div className="space-y-4">

            {/* Game Flow */}
            <div className="space-y-2">
              <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">{t('globalCommands.gameFlow')}</h3>
              <div className="grid grid-cols-3 gap-2">
                <button onClick={() => onGlobalAction('random_assign')} className="text-xs bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 px-2 py-2 rounded border border-slate-400 dark:border-slate-700 truncate" title={t('globalCommands.randomAssign')}>{t('globalCommands.randomAssign')}</button>
                <button onClick={() => onGlobalAction('start_game')} className="text-xs bg-blue-100 dark:bg-blue-900/20 hover:bg-blue-200 dark:hover:bg-blue-900/40 text-blue-700 dark:text-blue-300 px-2 py-2 rounded border border-blue-300 dark:border-blue-900/30 truncate" title={t('globalCommands.startGame')}>{t('globalCommands.startGame')}</button>
                <button
                  onClick={() => {
                    if (resetConfirming) {
                      onGlobalAction('reset');
                      setResetConfirming(false);
                    } else {
                      setResetConfirming(true);
                      setTimeout(() => setResetConfirming(false), 3000);
                    }
                  }}
                  className={`text-xs px-2 py-2 rounded border truncate transition-all duration-200 ${resetConfirming
                    ? 'bg-red-600 text-white border-red-700 hover:bg-red-700 font-bold'
                    : 'bg-red-100 dark:bg-red-900/20 hover:bg-red-200 dark:hover:bg-red-900/40 text-red-700 dark:text-red-300 border-red-300 dark:border-red-900/30'
                    }`}
                  title={t('globalCommands.forceReset')}
                >
                  {resetConfirming ? t('globalCommands.confirmReset') : t('globalCommands.forceReset')}
                </button>
              </div>
            </div>

            {/* Voice & Timer */}
            <div className="space-y-2">
              <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">{t('globalCommands.voiceTimer')}</h3>
              <div className="grid grid-cols-3 gap-2">
                <button onClick={() => onGlobalAction('timer_start')} className="text-xs bg-amber-100 dark:bg-amber-900/20 hover:bg-amber-200 dark:hover:bg-amber-900/40 text-amber-700 dark:text-amber-300 px-2 py-2 rounded border border-amber-300 dark:border-amber-900/30 truncate" title={t('timer.start')}>{t('timer.start')}</button>
                <button onClick={() => onGlobalAction('mute_all')} className="text-xs bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 px-2 py-2 rounded border border-slate-400 dark:border-slate-700 truncate" title={t('voice.muteAll')}>{t('voice.muteAll')}</button>
                <button onClick={() => onGlobalAction('unmute_all')} className="text-xs bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 px-2 py-2 rounded border border-slate-400 dark:border-slate-700 truncate" title={t('voice.unmuteAll')}>{t('voice.unmuteAll')}</button>
              </div>
            </div>

            {/* Admin & Roles */}
            <div className="space-y-2">
              <h3 className="text-xs font-bold text-slate-500 uppercase tracking-wider">{t('globalCommands.adminRoles')}</h3>
              <div className="grid grid-cols-3 gap-2">
                <button onClick={() => onGlobalAction('assign_judge')} className="text-xs bg-purple-100 dark:bg-purple-900/20 hover:bg-purple-200 dark:hover:bg-purple-900/40 text-purple-700 dark:text-purple-300 px-2 py-2 rounded border border-purple-300 dark:border-purple-900/30 truncate" title={t('admin.assignJudge')}>{t('admin.assignJudge')}</button>
                <button onClick={() => onGlobalAction('demote_judge')} className="text-xs bg-purple-100 dark:bg-purple-900/20 hover:bg-purple-200 dark:hover:bg-purple-900/40 text-purple-700 dark:text-purple-300 px-2 py-2 rounded border border-purple-300 dark:border-purple-900/30 truncate" title={t('admin.demoteJudge')}>{t('admin.demoteJudge')}</button>
                <button onClick={() => onGlobalAction('force_police')} className="text-xs bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 px-2 py-2 rounded border border-slate-400 dark:border-slate-700 truncate" title={t('admin.forcePolice')}>{t('admin.forcePolice')}</button>
              </div>
            </div>

          </div>
        </div>
      )}
    </div>
  );
};
