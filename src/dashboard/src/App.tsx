import { useState, useEffect } from 'react';
import { Users } from 'lucide-react';
import './index.css';

import { GameState, GamePhase } from './types';
import { INITIAL_PLAYERS } from './mockData';
import { LoginScreen } from './components/LoginScreen';
import { Sidebar } from './components/Sidebar';
import { GameHeader } from './components/GameHeader';
import { PlayerCard } from './components/PlayerCard';
import { GameLog } from './components/GameLog';
import { IntegrationGuide } from './components/IntegrationGuide';
import { useTranslation } from './lib/i18n';

const App = () => {
  const { t } = useTranslation();
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [showIntegrationGuide, setShowIntegrationGuide] = useState(false);
  const [gameState, setGameState] = useState<GameState>({
    phase: 'LOBBY',
    dayCount: 0,
    timerSeconds: 0,
    players: INITIAL_PLAYERS,
    logs: [{ id: '1', timestamp: '12:00:00', message: t('gameLog.systemInit'), type: 'info' }],
  });

  useEffect(() => {
    if (!isAuthenticated) return;
    const interval = setInterval(() => {
      setGameState(prev => {
        let newTimer = prev.timerSeconds;
        if (prev.phase !== 'LOBBY' && prev.phase !== 'GAME_OVER' && prev.timerSeconds > 0) {
          newTimer -= 1;
        }
        return { ...prev, timerSeconds: newTimer };
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [isAuthenticated]);

  const handleLogin = () => {
    setTimeout(() => {
      setIsAuthenticated(true);
      addLog(t('gameLog.adminLogin'));
    }, 800);
  };

  const handleAction = (playerId: string, actionType: string) => {
    const player = gameState.players.find(p => p.id === playerId)?.name || playerId;
    addLog(t('gameLog.adminCommand', { action: actionType, player }));
    setGameState(prev => ({
      ...prev,
      players: prev.players.map(p => {
        if (p.id === playerId) {
          if (actionType === 'kill') return { ...p, isAlive: false };
          if (actionType === 'revive') return { ...p, isAlive: true };
          if (actionType === 'toggle-jin') return { ...p, isJinBaoBao: !p.isJinBaoBao };
        }
        return p;
      })
    }));
  };

  const handleGlobalAction = (action: string) => {
    addLog(t('gameLog.adminGlobalCommand', { action }));
    if (action === 'start_game') {
      setGameState(prev => ({
        ...prev, phase: 'NIGHT', dayCount: 1, timerSeconds: 30,
        logs: [...prev.logs, { id: Date.now().toString(), timestamp: new Date().toLocaleTimeString(), message: t('gameLog.gameStarted'), type: 'alert' }]
      }));
    } else if (action === 'next_phase') {
      setGameState(prev => {
        const phases: GamePhase[] = ['NIGHT', 'DAY', 'VOTING'];
        const currentIdx = phases.indexOf(prev.phase as any);
        const nextPhase = currentIdx > -1 ? phases[(currentIdx + 1) % phases.length] : 'NIGHT';
        return { ...prev, phase: nextPhase, timerSeconds: nextPhase === 'NIGHT' ? 30 : 60, dayCount: nextPhase === 'NIGHT' ? prev.dayCount + 1 : prev.dayCount };
      });
    } else if (action === 'pause') {
      addLog(t('gameLog.gamePaused'));
    } else if (action === 'reset') {
      setGameState(prev => ({
        ...prev, phase: 'LOBBY', dayCount: 0, timerSeconds: 0, players: INITIAL_PLAYERS,
        logs: [...prev.logs, { id: Date.now().toString(), timestamp: new Date().toLocaleTimeString(), message: t('gameLog.gameReset'), type: 'alert' }]
      }));
    } else if (action === 'broadcast_role') {
      addLog(t('gameLog.broadcastRoles'));
    } else if (action === 'random_assign') {
      addLog(t('gameLog.randomizeRoles'));
    }
  };

  const addLog = (msg: string) => {
    setGameState(prev => ({
      ...prev,
      logs: [{ id: Date.now().toString(), timestamp: new Date().toLocaleTimeString(), message: msg, type: 'info' as const }, ...prev.logs].slice(0, 50)
    }));
  };

  if (!isAuthenticated) {
    return <LoginScreen onLogin={handleLogin} />;
  }

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-slate-100 font-sans flex flex-col md:flex-row overflow-hidden">
      <Sidebar onLogout={() => setIsAuthenticated(false)} onShowGuide={() => setShowIntegrationGuide(true)} />
      <main className="flex-1 flex flex-col h-screen overflow-hidden relative">
        <GameHeader phase={gameState.phase} dayCount={gameState.dayCount} timerSeconds={gameState.timerSeconds} onGlobalAction={handleGlobalAction} />
        <div className="flex-1 overflow-y-auto p-6 scrollbar-hide">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 h-full">
            <div className="lg:col-span-2 flex flex-col gap-6">
              <div className="flex items-center justify-between mb-2">
                <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                  <Users className="w-5 h-5 text-slate-500 dark:text-slate-400" />
                  {t('players.title')} <span className="text-slate-500 dark:text-slate-500 text-sm font-normal">({gameState.players.filter(p => p.isAlive).length} {t('players.alive')})</span>
                </h2>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                {gameState.players.map(player => (<PlayerCard key={player.id} player={player} onAction={handleAction} />))}
              </div>
              <div className="bg-white/50 dark:bg-slate-900/50 p-4 rounded-xl border border-slate-300 dark:border-slate-800 mt-auto">
                <h3 className="text-sm font-bold text-slate-600 dark:text-slate-400 uppercase tracking-wider mb-3">{t('globalCommands.title')}</h3>
                <div className="flex flex-wrap gap-2">
                  <button onClick={() => handleGlobalAction('broadcast_role')} className="text-xs bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 px-3 py-2 rounded border border-slate-400 dark:border-slate-700">{t('globalCommands.broadcastRole')}</button>
                  <button onClick={() => handleGlobalAction('random_assign')} className="text-xs bg-slate-200 dark:bg-slate-800 hover:bg-slate-300 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 px-3 py-2 rounded border border-slate-400 dark:border-slate-700">{t('globalCommands.randomAssign')}</button>
                  <button onClick={() => handleGlobalAction('reset')} className="text-xs bg-red-100 dark:bg-red-900/20 hover:bg-red-200 dark:hover:bg-red-900/40 text-red-700 dark:text-red-300 px-3 py-2 rounded border border-red-300 dark:border-red-900/30">{t('globalCommands.forceReset')}</button>
                </div>
              </div>
            </div>
            <GameLog logs={gameState.logs} onClear={() => setGameState(prev => ({ ...prev, logs: [] }))} onManualCommand={(cmd) => addLog(t('gameLog.manualCommand', { cmd }))} />
          </div>
        </div>
        {showIntegrationGuide && <IntegrationGuide onClose={() => setShowIntegrationGuide(false)} />}
      </main>
    </div>
  );
};

export default App;