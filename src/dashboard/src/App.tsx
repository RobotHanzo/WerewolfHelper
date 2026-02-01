import { useState, useEffect, useRef } from 'react';
import { Routes, Route, useParams, useNavigate } from 'react-router-dom';
import { Users } from 'lucide-react';
import './index.css';

import { GameState, GamePhase } from './types';
import { INITIAL_PLAYERS } from './mockData';
import { LoginScreen } from './components/LoginScreen';
import { ServerSelector } from './components/ServerSelector';
import { Sidebar } from './components/Sidebar';
import { GameHeader } from './components/GameHeader';
import { PlayerCard } from './components/PlayerCard';
import { GameLog } from './components/GameLog';
import { SettingsModal } from './components/SettingsModal';
import { PlayerEditModal } from './components/PlayerEditModal';
import { DeathConfirmModal } from './components/DeathConfirmModal';
import { SpectatorView } from './components/SpectatorView';
import { SpeechManager } from './components/SpeechManager';
import { GameSettingsPage } from './components/GameSettingsPage';
import { AuthCallback } from './components/AuthCallback';
import { useTranslation } from './lib/i18n';
import { useWebSocket } from './lib/websocket';
import { AccessDenied } from './components/AccessDenied';
import { ProgressOverlay } from './components/ProgressOverlay';
import { TimerControlModal } from './components/TimerControlModal';
import { PlayerSelectModal } from './components/PlayerSelectModal';
import { api } from './lib/api';
import { useAuth } from './contexts/AuthContext';

const Dashboard = () => {
  const { guildId } = useParams<{ guildId: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const { user, loading, logout, checkAuth } = useAuth();
  const [showSettings, setShowSettings] = useState(false);
  const [editingPlayerId, setEditingPlayerId] = useState<string | null>(null);
  const [deathConfirmPlayerId, setDeathConfirmPlayerId] = useState<string | null>(null);

  // New Modal States
  const [showTimerModal, setShowTimerModal] = useState(false);
  const [playerSelectModal, setPlayerSelectModal] = useState<{
    visible: boolean;
    type: 'ASSIGN_JUDGE' | 'DEMOTE_JUDGE' | 'FORCE_POLICE' | null;
    customPlayers?: any[]; // Allow partial player objects or mapped ones
  }>({ visible: false, type: null });

  const [gameState, setGameState] = useState<GameState>({
    phase: 'LOBBY',
    dayCount: 0,
    timerSeconds: 0,
    players: INITIAL_PLAYERS,
    logs: [],
  });

  // Progress Overlay State
  const [overlayVisible, setOverlayVisible] = useState(false);
  const [overlayTitle, setOverlayTitle] = useState<string>('');
  const [overlayLogs, setOverlayLogs] = useState<string[]>([]);
  const [overlayStatus, setOverlayStatus] = useState<'processing' | 'success' | 'error'>('processing');
  const [overlayError, setOverlayError] = useState<string | undefined>(undefined);
  const [overlayProgress, setOverlayProgress] = useState<number | undefined>(undefined);

  const isSelectingGuild = useRef(false);

  // Check authentication and authorization
  useEffect(() => {
    if (loading) return;

    if (!user) {
      navigate('/login');
      return;
    }

    // PENDING users haven't selected a server yet, skip initial check
    if (user.role === 'PENDING') {
      // If they're trying to access a specific server, update their role
      if (guildId && !isSelectingGuild.current) {
        isSelectingGuild.current = true;
        const selectGuild = async () => {
          try {
            const response = await fetch(`/api/auth/select-guild/${guildId}`, {
              method: 'POST',
              credentials: 'include',
            });

            if (response.ok) {
              const data = await response.json();
              if (data.success) {
                // Refresh auth state to get updated role
                await checkAuth();
              }
            }
          } catch (error) {
            console.error('Failed to select guild:', error);
          } finally {
            isSelectingGuild.current = false;
          }
        };
        selectGuild();
      }
      return;
    }

    // For non-PENDING users, check if they're accessing a different guild
    if (guildId && user.guildId && user.guildId.toString() !== guildId.toString() && !isSelectingGuild.current) {
      // Allow switching guilds by calling select-guild
      isSelectingGuild.current = true;
      const switchGuild = async () => {
        try {
          const response = await fetch(`/api/auth/select-guild/${guildId}`, {
            method: 'POST',
            credentials: 'include',
          });

          if (response.ok) {
            const data = await response.json();
            if (data.success) {
              // Refresh auth state to get updated role for new guild
              await checkAuth();
            }
          }
        } catch (error) {
          console.error('Failed to switch guild:', error);
          alert('Failed to switch server. Please try again.');
        } finally {
          isSelectingGuild.current = false;
        }
      };
      switchGuild();
      return;
    }

    // After ensuring we are on the correct guild, check if the user is BLOCKED
    if (user.role === 'BLOCKED') {
      if (!window.location.pathname.includes('/access-denied')) {
        navigate('/access-denied');
      }
      return;
    }

    // Role-based route redirection for already authorized users
    if (user.role === 'SPECTATOR') {
      const path = window.location.pathname;
      const baseUrl = `/server/${guildId}`;
      if (path === baseUrl || path === `${baseUrl}/` || path.includes('/settings')) {
        navigate(`${baseUrl}/spectator`);
      }
    }
  }, [user, loading, guildId, navigate, checkAuth]);

  // Helper to map session data to GameState players
  const mapSessionToPlayers = (sessionData: any) => {
    return sessionData.players.map((player: any) => ({
      id: player.id,
      name: player.userId ? player.name : `${t('messages.player')} ${player.id} `,
      userId: player.userId,
      username: player.username,
      avatar: player.userId ? player.avatar : null,
      roles: player.roles || [],
      deadRoles: player.deadRoles || [],
      isAlive: player.isAlive,
      isSheriff: player.police,
      isJinBaoBao: player.jinBaoBao,
      isProtected: false, // Not explicitly exposed in API JSON, assumed handled by statuses or hidden
      isPoisoned: false,
      isSilenced: false,
      isDuplicated: player.duplicated,
      isJudge: player.isJudge || false,
      rolePositionLocked: player.rolePositionLocked,
      statuses: [
        ...(player.police ? ['sheriff'] : []),
        ...(player.jinBaoBao ? ['jinBaoBao'] : []),
      ] as Array<'sheriff' | 'jinBaoBao' | 'protected' | 'poisoned' | 'silenced'>,
    }));
  };

  // WebSocket connection for real-time updates
  const { isConnected } = useWebSocket((data) => {
    // Check for progress events
    if (data.type === 'PROGRESS') {
      console.log('Incoming PROGRESS event:', {
        serverGuildId: data.guildId,
        clientGuildId: guildId,
        match: data.guildId?.toString() === guildId,
        message: data.message,
        percent: data.percent
      });

      if (data.guildId?.toString() === guildId) {
        if (data.message) {
          setOverlayLogs(prev => [...prev, data.message]);
        }
        if (data.percent !== undefined) {
          setOverlayProgress(data.percent);
        }
        return;
      }
    }

    // Check if the update is for the current guild
    if (data.guildId && data.guildId.toString() === guildId) {
      console.log('WebSocket update received:', data);

      const players = mapSessionToPlayers(data);
      setGameState(prev => ({
        ...prev,
        players: players,
        doubleIdentities: data.doubleIdentities,
        availableRoles: data.roles || [],
        speech: data.speech,
        police: data.police,
        logs: data.logs || prev.logs,
      }));
    }
  });

  useEffect(() => {
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
  }, []);

  // Load game state when component mounts or guild ID changes
  useEffect(() => {
    if (!guildId) return;

    const loadGameState = async () => {
      try {
        const sessionData: any = await api.getSession(guildId);
        console.log('Session data:', sessionData);

        const players = mapSessionToPlayers(sessionData);

        setGameState(prev => ({
          ...prev,
          players: players,
          doubleIdentities: sessionData.doubleIdentities,
          availableRoles: sessionData.roles || [],
          speech: sessionData.speech,
          police: sessionData.police,
          logs: sessionData.logs || [],
        }));
      } catch (error) {
        console.error('Failed to load session data:', error);
      }
    };

    loadGameState();
  }, [guildId]); // Removed 't' and addLog calls to prevent infinite loop

  const handleAction = async (playerId: string, actionType: string) => {
    if (!guildId) return;
    const player = gameState.players.find(p => p.id === playerId);
    if (!player) return;

    if (actionType === 'role') {
      setEditingPlayerId(playerId);
      return;
    }

    const playerName = player.name;
    addLog(t('gameLog.adminCommand', { action: actionType, player: playerName }));

    try {
      if (actionType === 'kill') {
        if (player.userId) {
          setDeathConfirmPlayerId(playerId);
        } else {
          console.warn('Cannot kill unassigned player via API');
        }
      } else if (actionType === 'revive') {
        if (player.userId) {
          await api.revivePlayer(guildId, player.userId);
        }
      } else if (actionType.startsWith('revive_role:')) {
        const role = actionType.split(':')[1];
        if (player.userId) {
          await api.reviveRole(guildId, player.userId, role);
        }
      } else if (actionType === 'toggle-jin') {
        // Toggle Jin Bao Bao logic (not seen in API yet, skipping)
      } else if (actionType === 'sheriff') {
        if (player.userId) {
          await api.setPolice(guildId, player.userId);
        }
      } else if (actionType === 'switch_role_order') {
        if (player.userId) {
          await api.switchRoleOrder(guildId, player.userId);
        }
      }

    } catch (error) {
      console.error('Action failed:', error);
      addLog(t('errors.actionFailed', { action: actionType }));
    }
  };

  const handleGlobalAction = (action: string) => {
    addLog(t('gameLog.adminGlobalCommand', { action }));
    if (action === 'start_game') {
      setGameState(prev => ({
        ...prev, phase: 'NIGHT', dayCount: 1, timerSeconds: 30,
        logs: [...prev.logs, { id: Date.now().toString() + Math.random().toString(36).slice(2), timestamp: new Date().toLocaleTimeString(), message: t('gameLog.gameStarted'), type: 'alert' }]
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
      const performReset = async () => {
        setOverlayVisible(true);
        setOverlayTitle(t('progressOverlay.resetTitle'));
        setOverlayStatus('processing');
        setOverlayLogs([t('overlayMessages.resetting')]);
        setOverlayError(undefined);
        setOverlayProgress(0);

        try {
          if (guildId) {
            await api.resetSession(guildId);
          } else {
            throw new Error("Missing Guild ID");
          }

          setOverlayStatus('success');
          setOverlayLogs(prev => [...prev, t('overlayMessages.resetSuccess')]);
        } catch (error: any) {
          console.error("Reset failed", error);
          setOverlayStatus('error');
          setOverlayLogs(prev => [...prev, `${t('errors.error')}: ${error.message || t('errors.unknownError')}`]);
          setOverlayError(error.message || t('errors.resetFailed'));
        }
      };
      performReset();
    } else if (action === 'random_assign') {
      addLog(t('gameLog.randomizeRoles'));

      const performRandomAssign = async () => {
        setOverlayVisible(true);
        setOverlayTitle(t('messages.randomAssignRoles'));
        setOverlayStatus('processing');
        setOverlayLogs([t('overlayMessages.requestingAssign')]);
        setOverlayError(undefined);
        setOverlayProgress(0);

        try {
          if (guildId) {
            await api.assignRoles(guildId);
          } else {
            throw new Error("Missing Guild ID");
          }

          setOverlayLogs(prev => [...prev]);

          setOverlayStatus('success');
          setOverlayLogs(prev => [...prev, t('overlayMessages.assignSuccess')]);
        } catch (error: any) {
          console.error("Assign failed", error);
          setOverlayStatus('error');
          setOverlayLogs(prev => [...prev, `${t('errors.error')}: ${error.message || t('errors.unknownError')}`]);
          setOverlayError(error.message || t('errors.assignFailed'));
        }
      };
      performRandomAssign();
    } else if (action === 'start_game') {
      const performStart = async () => {
        try {
          if (guildId) {
            await api.startGame(guildId);
          }
        } catch (error: any) {
          console.error("Start game failed", error);
        }
      };
      performStart();
    } else if (action === 'timer_start') {
      setShowTimerModal(true);
    } else if (action === 'mute_all') {
      if (guildId) api.muteAll(guildId).then(() => addLog(t('gameLog.manualCommand', { cmd: 'Mute All' })));
    } else if (action === 'unmute_all') {
      if (guildId) api.unmuteAll(guildId).then(() => addLog(t('gameLog.manualCommand', { cmd: 'Unmute All' })));
    } else if (action === 'manage_judges') {
      // PROPOSE: This should ideally be two buttons? Or one "Judge Manager"? 
      // User asked for: /player judge (assign) AND /player demote (remove)
      // I will implement two separate actions in GameLog, handled here.
    } else if (action === 'assign_judge' || action === 'demote_judge') {
      if (guildId) {
        api.getGuildMembers(guildId).then(members => {
          const mappedPlayers = members.map(m => ({
            id: m.userId,
            name: m.name, // Use effective name
            userId: m.userId,
            avatar: m.avatar,
            roles: [],
            isJudge: m.isJudge,
            // Defaults
            deadRoles: [],
            isAlive: true,
            isSheriff: false,
            isJinBaoBao: false,
            isProtected: false,
            isPoisoned: false,
            isSilenced: false,
            statuses: []
          }));
          setPlayerSelectModal({
            visible: true,
            type: action === 'assign_judge' ? 'ASSIGN_JUDGE' : 'DEMOTE_JUDGE',
            customPlayers: mappedPlayers
          });
        }).catch(err => {
          console.error("Failed to fetch members", err);
          addLog(t('errors.error'));
        });
      }
    } else if (action === 'force_police') {
      setPlayerSelectModal({ visible: true, type: 'FORCE_POLICE' });
    }
  };

  const handleTimerStart = (seconds: number) => {
    if (guildId) {
      api.manualStartTimer(guildId, seconds);
      addLog(t('gameLog.manualCommand', { cmd: `Timer ${seconds}s` }));
    }
  };

  const handlePlayerSelect = async (playerId: string) => {
    const player = (playerSelectModal.customPlayers || gameState.players).find(p => p.id === playerId);
    if (!player || !guildId || !player.userId) return;

    if (playerSelectModal.type === 'ASSIGN_JUDGE') {
      await api.updateUserRole(guildId, player.userId, 'JUDGE');
      addLog(t('gameLog.manualCommand', { cmd: `Promote ${player.name} to Judge` }));
    } else if (playerSelectModal.type === 'DEMOTE_JUDGE') {
      await api.updateUserRole(guildId, player.userId, 'SPECTATOR'); // Default demote back to spectator? Or PENDING? Safe to say Spectator or allow re-login. Let's start with Spectator.
      addLog(t('gameLog.manualCommand', { cmd: `Demote ${player.name}` }));
    } else if (playerSelectModal.type === 'FORCE_POLICE') {
      await api.setPolice(guildId, player.userId);
      addLog(t('gameLog.manualCommand', { cmd: `Force Police ${player.name}` }));
    }
  };

  const addLog = (msg: string) => {
    setGameState(prev => ({
      ...prev,
      logs: [{ id: Date.now().toString() + Math.random().toString(36).slice(2), timestamp: new Date().toLocaleTimeString(), message: msg, type: 'info' as const }, ...prev.logs].slice(0, 50)
    }));
  };

  const [isSpectatorSimulation, setIsSpectatorSimulation] = useState(false);

  const toggleSpectatorSimulation = () => {
    const newMode = !isSpectatorSimulation;
    setIsSpectatorSimulation(newMode);
    if (newMode) {
      navigate(`/server/${guildId}/spectator`);
    } else {
      navigate(`/server/${guildId}`);
    }
  };

  const editingPlayer = gameState.players.find(p => p.id === editingPlayerId);

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-slate-100 font-sans flex flex-col md:flex-row overflow-hidden">
      <Sidebar
        onLogout={logout}
        onSettingsClick={() => navigate(`/server/${guildId}/settings`)}
        onDashboardClick={() => navigate(`/server/${guildId}`)}
        onSpectatorClick={() => navigate(`/server/${guildId}/spectator`)}
        onSpeechClick={() => navigate(`/server/${guildId}/speech`)}
        onSwitchServer={() => navigate('/')}
        onToggleSpectatorMode={toggleSpectatorSimulation}
        isSpectatorMode={isSpectatorSimulation}
        isConnected={isConnected}
      />
      <main className="flex-1 flex flex-col h-screen overflow-hidden relative">
        <GameHeader
          phase={gameState.phase}
          dayCount={gameState.dayCount}
          timerSeconds={gameState.timerSeconds}
          onGlobalAction={handleGlobalAction}
          speech={gameState.speech}
          players={gameState.players}
          readonly={user?.role === 'SPECTATOR' || isSpectatorSimulation}
        />
        <div className="flex-1 overflow-hidden relative flex flex-col lg:flex-row bg-slate-50 dark:bg-slate-900/30">
          {/* Main Content Area */}
          <div className="flex-1 overflow-y-auto p-4 md:p-8 scrollbar-thin scrollbar-thumb-slate-300 dark:scrollbar-thumb-slate-700 scrollbar-track-transparent">
            <div className="max-w-7xl mx-auto space-y-8">
              <Routes>
                <Route path="/" element={
                  <>
                    <div className="flex items-center justify-between mb-2">
                      <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                        <Users className="w-5 h-5 text-slate-500 dark:text-slate-400" />
                        {t('players.title')} <span className="text-slate-500 dark:text-slate-500 text-sm font-normal">({gameState.players.filter(p => p.isAlive).length} {t('players.alive')})</span>
                      </h2>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
                      {gameState.players.map(player => (
                        <PlayerCard
                          key={player.id}
                          player={player}
                          onAction={handleAction}
                          readonly={user?.role === 'SPECTATOR' || isSpectatorSimulation}
                        />
                      ))}
                    </div>
                  </>
                } />
                <Route path="/spectator" element={
                  <SpectatorView players={gameState.players} doubleIdentities={gameState.doubleIdentities ?? false} />
                } />
                <Route path="/speech" element={
                  <SpeechManager
                    speech={gameState.speech}
                    police={gameState.police}
                    players={gameState.players}
                    guildId={guildId!}
                    readonly={user?.role === 'SPECTATOR' || isSpectatorSimulation}
                  />
                } />
                <Route path="/settings" element={
                  <GameSettingsPage />
                } />
              </Routes>

              {/* Mobile Game Log */}
              <div className="lg:hidden">
                <GameLog
                  logs={gameState.logs}
                  onGlobalAction={handleGlobalAction}
                  readonly={user?.role !== 'JUDGE' || isSpectatorSimulation}
                />
              </div>
            </div>
          </div>

          {/* Desktop Right Sidebar Game Log */}
          <div className="hidden lg:block w-[400px] shrink-0 border-l border-slate-200 dark:border-slate-800 bg-white/30 dark:bg-slate-900/30 p-4">
            <GameLog
              logs={gameState.logs}
              onGlobalAction={handleGlobalAction}
              readonly={user?.role !== 'JUDGE' || isSpectatorSimulation}
              className="h-full"
            />
          </div>
        </div>
        {showSettings && <SettingsModal onClose={() => setShowSettings(false)} />}
        {editingPlayerId && editingPlayer && guildId && (
          <PlayerEditModal
            player={editingPlayer}
            allPlayers={gameState.players}
            guildId={guildId}
            onClose={() => setEditingPlayerId(null)}
            doubleIdentities={gameState.doubleIdentities}
            availableRoles={gameState.availableRoles || []}
          />
        )}
        {deathConfirmPlayerId && guildId && (
          <DeathConfirmModal
            player={gameState.players.find(p => p.id === deathConfirmPlayerId)!}
            guildId={guildId}
            onClose={() => setDeathConfirmPlayerId(null)}
          />
        )}

        <ProgressOverlay
          isVisible={overlayVisible}
          title={overlayTitle}
          status={overlayStatus}
          logs={overlayLogs}
          error={overlayError}
          progress={overlayProgress}
          onComplete={() => setOverlayVisible(false)}
        />

        {showTimerModal && (
          <TimerControlModal
            onClose={() => setShowTimerModal(false)}
            onStart={handleTimerStart}
          />
        )}

        {playerSelectModal.visible && (
          <PlayerSelectModal
            title={
              playerSelectModal.type === 'ASSIGN_JUDGE' ? t('modal.assignJudge') :
                playerSelectModal.type === 'DEMOTE_JUDGE' ? t('modal.demoteJudge') :
                  playerSelectModal.type === 'FORCE_POLICE' ? t('modal.forcePolice') : ''
            }
            players={playerSelectModal.customPlayers || gameState.players}
            onClose={() => setPlayerSelectModal({ ...playerSelectModal, visible: false, customPlayers: undefined })}
            onSelect={handlePlayerSelect}
            filter={(p) => {
              // Filtering logic based on user roles and requirements
              if (!p.userId) return false; // Must be a real user

              if (playerSelectModal.type === 'ASSIGN_JUDGE') {
                return !p.isJudge;
              }
              if (playerSelectModal.type === 'DEMOTE_JUDGE') {
                return !!p.isJudge;
              }
              if (playerSelectModal.type === 'FORCE_POLICE') {
                // Should only show players who are alive? or just all players?
                // Usually force police is for alive players.
                return p.isAlive;
              }
              return true;
            }}
          />
        )}
      </main>
    </div >
  );
};

const LoginPage = () => {
  const handleLogin = () => {
    // Redirect to OAuth login (no guild_id yet)
    window.location.href = '/api/auth/login';
  };
  return <LoginScreen onLogin={handleLogin} />;
};

const ServerSelectionPage = () => {
  const navigate = useNavigate();
  const { user, loading } = useAuth();

  // Redirect to login if not authenticated
  useEffect(() => {
    if (!loading && !user) {
      navigate('/login');
    }
  }, [user, loading, navigate]);

  // Show loading while checking auth
  if (loading) {
    return (
      <div className="min-h-screen bg-slate-50 dark:bg-slate-950 flex items-center justify-center">
        <div className="text-slate-600 dark:text-slate-400">Loading...</div>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  const handleSelectServer = (guildId: string) => {
    navigate(`/server/${guildId}`);
  };
  return <ServerSelector onSelectServer={handleSelectServer} onBack={() => navigate('/login')} />;
};

const App = () => {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallback />} />
      <Route path="/access-denied" element={<AccessDenied />} />
      <Route path="/" element={<ServerSelectionPage />} />
      <Route path="/server/:guildId/*" element={<Dashboard />} />
    </Routes>
  );
};

export default App;