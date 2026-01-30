import { Sun, Moon, Play, Pause, SkipForward } from 'lucide-react';
import { GamePhase } from '../types';
import { useTranslation } from '../lib/i18n';

interface GameHeaderProps {
  phase: GamePhase;
  dayCount: number;
  timerSeconds: number;
  onGlobalAction: (action: string) => void;
}

export const GameHeader: React.FC<GameHeaderProps> = ({ phase, dayCount, timerSeconds, onGlobalAction }) => {
  const { t } = useTranslation();
  const btnStyle = "px-4 py-2 rounded-lg font-medium transition-all active:scale-95 flex items-center justify-center gap-2";
  const btnPrimary = "bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg shadow-indigo-900/20 dark:shadow-indigo-900/20";
  const btnSecondary = "bg-slate-300 dark:bg-slate-700 hover:bg-slate-400 dark:hover:bg-slate-600 text-slate-800 dark:text-slate-200";

  return (
    <header className="h-16 bg-white/50 dark:bg-slate-900/50 backdrop-blur-md border-b border-slate-300 dark:border-slate-800 flex items-center justify-between px-6 z-10">
      <div className="flex items-center gap-6">
        <div className="flex flex-col">
          <span className="text-xs text-slate-500 dark:text-slate-400 font-bold uppercase tracking-wider">{t('gameHeader.currentPhase')}</span>
          <div className="flex items-center gap-2 text-slate-900 dark:text-slate-100">
            {phase === 'DAY' ? <Sun className="w-5 h-5 text-orange-500" /> : <Moon className="w-5 h-5 text-indigo-500 dark:text-indigo-400" />}
            <span className="font-bold text-lg">{t(`phases.${phase}`)} {dayCount > 0 && `#${dayCount}`}</span>
          </div>
        </div>

        <div className="h-8 w-px bg-slate-300 dark:bg-slate-700" />

        <div className="flex flex-col">
          <span className="text-xs text-slate-500 dark:text-slate-400 font-bold uppercase tracking-wider">{t('gameHeader.timer')}</span>
          <div className={`font-mono font-bold text-xl ${timerSeconds < 10 ? 'text-red-500 dark:text-red-400' : 'text-slate-800 dark:text-slate-200'}`}>
            {Math.floor(timerSeconds / 60)}:{String(timerSeconds % 60).padStart(2, '0')}
          </div>
        </div>
      </div>

      <div className="flex items-center gap-3">
        {phase === 'LOBBY' ? (
          <button
            onClick={() => onGlobalAction('start_game')}
            className={`${btnStyle} ${btnPrimary}`}
          >
            <Play className="w-4 h-4" /> {t('gameHeader.startGame')}
          </button>
        ) : (
          <>
            <button onClick={() => onGlobalAction('pause')} className={`${btnStyle} ${btnSecondary}`}>
              <Pause className="w-4 h-4" />
            </button>
            <button
              onClick={() => onGlobalAction('next_phase')}
              className={`${btnStyle} ${btnSecondary}`}
            >
              <SkipForward className="w-4 h-4" /> {t('gameHeader.nextPhase')}
            </button>
          </>
        )}
      </div>
    </header>
  );
};
