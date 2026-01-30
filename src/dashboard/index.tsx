import React, { useState, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { Users } from 'lucide-react';
import './index.css'; // Import Tailwind directives

import { GameState, GamePhase } from './types';
import { INITIAL_PLAYERS } from './mockData';
import { LoginScreen } from './components/LoginScreen';
import { Sidebar } from './components/Sidebar';
import { GameHeader } from './components/GameHeader';
import { PlayerCard } from './components/PlayerCard';
import { GameLog } from './components/GameLog';
import { IntegrationGuide } from './components/IntegrationGuide';

const App = () => {
  // State
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [showIntegrationGuide, setShowIntegrationGuide] = useState(false);
  const [gameState, setGameState] = useState<GameState>({
    phase: 'LOBBY',
    dayCount: 0,
    timerSeconds: 0,
    players: INITIAL_PLAYERS,
    logs: [{ id: '1', timestamp: '12:00:00', message: 'System initialized. Waiting for connection...', type: 'info' }],
  });

  // Simulation Logic (Simulating Real-time updates)
  useEffect(() => {
    if (!isAuthenticated) return;

    // Simulate WebSocket heartbeat / polling
    const interval = setInterval(() => {
      setGameState(prev => {
        // Decrease timer if active
        let newTimer = prev.timerSeconds;
        let newPhase = prev.phase;

        if (prev.phase !== 'LOBBY' && prev.phase !== 'GAME_OVER' && prev.timerSeconds > 0) {
          newTimer -= 1;
        }

        return {
          ...prev,
          timerSeconds: newTimer,
          phase: newPhase
        };
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [isAuthenticated]);

  // Auth Simulation
  const handleLogin = () => {
    setTimeout(() => {
      setIsAuthenticated(true);
      addLog('Admin logged in via Discord OAuth simulation.');
    }, 800);
  };

  const handleAction = (playerId: string, actionType: string) => {
    // Simulate API call to Java Bot
    addLog(`Admin executed command: /${actionType} on ${playerId}`);
    
    setGameState(prev => {
      const updatedPlayers = prev.players.map(p => {
        if (p.id === playerId) {
          if (actionType === 'kill') return { ...p, isAlive: false };
          if (actionType === 'revive') return { ...p, isAlive: true };
          if (actionType === 'toggle-jin') return { ...p, isJinBaoBao: !p.isJinBaoBao };
        }
        return p;
      });
      return { ...prev, players: updatedPlayers };
    });
  };

  const handleGlobalAction = (action: string) => {
    addLog(`Admin executed global command: /${action}`);
    
    if (action === 'start_game') {
      setGameState(prev => ({ 
        ...prev, 
        phase: 'NIGHT', 
        dayCount: 1, 
        timerSeconds: 30,
        logs: [...prev.logs, { id: Date.now().toString(), timestamp: new Date().toLocaleTimeString(), message: 'Game Started!', type: 'alert' }] 
      }));
    } else if (action === 'next_phase') {
      setGameState(prev => {
        const phases: GamePhase[] = ['NIGHT', 'DAY', 'VOTING'];
        const currentIdx = phases.indexOf(prev.phase as any);
        const nextPhase = currentIdx > -1 ? phases[(currentIdx + 1) % phases.length] : 'NIGHT';
        return {
          ...prev,
          phase: nextPhase,
          timerSeconds: nextPhase === 'NIGHT' ? 30 : 60,
          dayCount: nextPhase === 'NIGHT' ? prev.dayCount + 1 : prev.dayCount
        };
      });
    } else if (action === 'pause') {
       addLog('Game timer paused by Admin.');
    } else if (action === 'reset') {
       setGameState(prev => ({
         ...prev,
         phase: 'LOBBY',
         dayCount: 0,
         timerSeconds: 0,
         players: INITIAL_PLAYERS,
         logs: [...prev.logs, { id: Date.now().toString(), timestamp: new Date().toLocaleTimeString(), message: 'Game Reset.', type: 'alert' }]
       }));
    } else if (action === 'broadcast_role') {
       addLog('Broadcasting roles to all players via DM...');
    } else if (action === 'random_assign') {
       addLog('Randomizing roles...');
    }
  };

  const addLog = (msg: string) => {
    setGameState(prev => ({
      ...prev,
      logs: [{
        id: Date.now().toString(),
        timestamp: new Date().toLocaleTimeString(),
        message: msg,
        type: 'info' as const
      }, ...prev.logs].slice(0, 50)
    }));
  };

  if (!isAuthenticated) {
    return <LoginScreen onLogin={handleLogin} />;
  }

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 font-sans flex flex-col md:flex-row overflow-hidden">
      <Sidebar onLogout={() => setIsAuthenticated(false)} onShowGuide={() => setShowIntegrationGuide(true)} />

      <main className="flex-1 flex flex-col h-screen overflow-hidden relative">
        <GameHeader 
          phase={gameState.phase} 
          dayCount={gameState.dayCount} 
          timerSeconds={gameState.timerSeconds} 
          onGlobalAction={handleGlobalAction} 
        />

        <div className="flex-1 overflow-y-auto p-6 scrollbar-hide">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 h-full">
            
            {/* Left: Players Grid */}
            <div className="lg:col-span-2 flex flex-col gap-6">
              <div className="flex items-center justify-between mb-2">
                <h2 className="text-lg font-bold text-slate-100 flex items-center gap-2">
                  <Users className="w-5 h-5 text-slate-400" /> 
                  Players <span className="text-slate-500 text-sm font-normal">({gameState.players.filter(p => p.isAlive).length} Alive)</span>
                </h2>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                {gameState.players.map(player => (
                  <PlayerCard key={player.id} player={player} onAction={handleAction} />
                ))}
              </div>

              {/* Quick Actions Bar */}
              <div className="bg-slate-900/50 p-4 rounded-xl border border-slate-800 mt-auto">
                <h3 className="text-sm font-bold text-slate-400 uppercase tracking-wider mb-3">Global Admin Commands</h3>
                <div className="flex flex-wrap gap-2">
                   <button onClick={() => handleGlobalAction('broadcast_role')} className="text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 px-3 py-2 rounded border border-slate-700">Broadcast Roles (DM)</button>
                   <button onClick={() => handleGlobalAction('random_assign')} className="text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 px-3 py-2 rounded border border-slate-700">Randomize Roles</button>
                   <button onClick={() => handleGlobalAction('reset')} className="text-xs bg-red-900/20 hover:bg-red-900/40 text-red-300 px-3 py-2 rounded border border-red-900/30">Force Reset</button>
                </div>
              </div>
            </div>

            {/* Right: Logs & Event Stream */}
            <GameLog 
              logs={gameState.logs} 
              onClear={() => setGameState(prev => ({...prev, logs: []}))}
              onManualCommand={(cmd) => addLog(`Manual command: ${cmd}`)}
            />

          </div>
        </div>

        {/* Modals */}
        {showIntegrationGuide && <IntegrationGuide onClose={() => setShowIntegrationGuide(false)} />}
      </main>
    </div>
  );
};

const root = createRoot(document.getElementById('root')!);
root.render(<App />);