
import React from 'react';
import { Sun, Moon, Play, Pause, SkipForward } from 'lucide-react';
import { GamePhase } from '../types';

interface GameHeaderProps {
  phase: GamePhase;
  dayCount: number;
  timerSeconds: number;
  onGlobalAction: (action: string) => void;
}

export const GameHeader: React.FC<GameHeaderProps> = ({ phase, dayCount, timerSeconds, onGlobalAction }) => {
  const btnStyle = "px-4 py-2 rounded-lg font-medium transition-all active:scale-95 flex items-center justify-center gap-2";
  const btnPrimary = "bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg shadow-indigo-900/20";
  const btnSecondary = "bg-slate-700 hover:bg-slate-600 text-slate-200";

  return (
    <header className="h-16 bg-slate-900/50 backdrop-blur-md border-b border-slate-800 flex items-center justify-between px-6 z-10">
      <div className="flex items-center gap-6">
        <div className="flex flex-col">
          <span className="text-xs text-slate-400 font-bold uppercase tracking-wider">Current Phase</span>
          <div className="flex items-center gap-2 text-slate-100">
            {phase === 'DAY' ? <Sun className="w-5 h-5 text-orange-400" /> : <Moon className="w-5 h-5 text-indigo-400" />}
            <span className="font-bold text-lg">{phase} {dayCount > 0 && `#${dayCount}`}</span>
          </div>
        </div>

        <div className="h-8 w-px bg-slate-700" />

        <div className="flex flex-col">
          <span className="text-xs text-slate-400 font-bold uppercase tracking-wider">Timer</span>
          <div className={`font-mono font-bold text-xl ${timerSeconds < 10 ? 'text-red-400' : 'text-slate-200'}`}>
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
              <Play className="w-4 h-4" /> Start Game
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
                <SkipForward className="w-4 h-4" /> Next Phase
              </button>
            </>
         )}
      </div>
    </header>
  );
};
