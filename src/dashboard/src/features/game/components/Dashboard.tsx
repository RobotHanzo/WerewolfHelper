import {useEffect, useState} from 'react';
import {Route, Routes, useNavigate, useParams} from 'react-router-dom';
import {MessageSquare, Users, X} from 'lucide-react';
import {useTranslation} from '@/lib/i18n';
import {useAuth} from '@/features/auth/contexts/AuthContext';

// Hooks
import {useGameState} from '../hooks/useGameState';
import {useGameActions} from '../hooks/useGameActions';
import {useDashboardAuth} from '@/features/auth/hooks/useDashboardAuth';

// Components
import {Sidebar} from '@/components/layout/Sidebar';
import {GameHeader} from './GameHeader';
import {GameLog} from './GameLog';
import {PlayerCard} from '@/features/players/components/PlayerCard';
import {SpectatorView} from '@/features/spectator/components/SpectatorView';
import {SpeechManager} from '@/features/speech/components/SpeechManager';
import {GameSettingsPage} from './GameSettingsPage';
import {PlayerEditModal} from '@/features/players/components/PlayerEditModal';
import {DeathConfirmModal} from './DeathConfirmModal';
import {ProgressOverlay} from '@/components/ui/ProgressOverlay';
import {VoteStatus} from './VoteStatus';
import {TimerControlModal} from './TimerControlModal';
import {PlayerSelectModal} from '@/features/players/components/PlayerSelectModal';
import {SessionExpiredModal} from '@/features/auth/components/SessionExpiredModal';
import {SettingsModal} from './SettingsModal';

export const Dashboard = () => {
    const {guildId} = useParams<{ guildId: string }>();
    const navigate = useNavigate();
    const {t} = useTranslation();
    const {user, loading, logout, checkAuth} = useAuth();

    // Local UI State
    const [showSettings, setShowSettings] = useState(false);
    const [showLogs, setShowLogs] = useState(false);
    const [lastSeenLogCount, setLastSeenLogCount] = useState(0);
    const [isSpectatorSimulation, setIsSpectatorSimulation] = useState(false);

    // Business Logic Hooks
    const {
        gameState,
        setGameState,
        isConnected,
        overlayState,
        setOverlayState,
        showSessionExpired,
        setShowSessionExpired
    } = useGameState(guildId, user);

    const {
        handleAction,
        handleGlobalAction,
        handleTimerStart,
        handlePlayerSelect,
        showTimerModal,
        setShowTimerModal,
        editingPlayerId,
        setEditingPlayerId,
        deathConfirmPlayerId,
        setDeathConfirmPlayerId,
        playerSelectModal,
        setPlayerSelectModal
    } = useGameActions(guildId, gameState, setGameState, setOverlayState);

    const isGuildReady = useDashboardAuth(guildId, user, loading, checkAuth, gameState.players);

    // Editing player helper
    const editingPlayer = gameState.players.find(p => p.id === editingPlayerId);

    const toggleSpectatorSimulation = () => {
        const newMode = !isSpectatorSimulation;
        setIsSpectatorSimulation(newMode);
        if (newMode) {
            navigate(`/server/${guildId}/spectator`);
        } else {
            navigate(`/server/${guildId}`);
        }
    };

    const toggleLogs = () => {
        const newShowLogs = !showLogs;
        setShowLogs(newShowLogs);
        if (newShowLogs) {
            setLastSeenLogCount(gameState.logs.length);
        }
    };

    // Update log count if logs change while open
    useEffect(() => {
        if (showLogs) {
            setLastSeenLogCount(gameState.logs.length);
        }
    }, [gameState.logs.length, showLogs]);

    return (
        <div
            className="min-h-screen bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-slate-100 font-sans flex flex-col md:flex-row overflow-hidden">
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
                <div
                    className="flex-1 overflow-hidden relative flex flex-col lg:flex-row bg-slate-50 dark:bg-slate-900/30">
                    <div className="flex-1 overflow-y-auto scrollbar-hide p-4 md:p-8">
                        <div className="max-w-7xl mx-auto space-y-8">
                            {isGuildReady ? (
                                <>
                                    <Routes>
                                        <Route path="/" element={
                                            <>
                                                <div className="flex items-center justify-between mb-2">
                                                    <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100 flex items-center gap-2">
                                                        <Users className="w-5 h-5 text-slate-500 dark:text-slate-400"/>
                                                        {t('players.title')} <span
                                                        className="text-slate-500 dark:text-slate-500 text-sm font-normal">
                                                            ({gameState.players.filter(p => p.isAlive).length} {t('players.alive')})
                                                        </span>
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
                                        }/>
                                        <Route path="/spectator" element={
                                            <SpectatorView
                                                players={gameState.players}
                                                doubleIdentities={gameState.doubleIdentities ?? false}
                                            />
                                        }/>
                                        <Route path="/speech" element={
                                            <SpeechManager
                                                speech={gameState.speech}
                                                police={gameState.police}
                                                players={gameState.players}
                                                guildId={guildId!}
                                                readonly={user?.role === 'SPECTATOR' || isSpectatorSimulation}
                                            />
                                        }/>
                                        <Route path="/settings" element={
                                            <GameSettingsPage/>
                                        }/>
                                    </Routes>
                                </>
                            ) : (
                                <div className="flex items-center justify-center h-full p-12">
                                    <div className="flex flex-col items-center gap-4">
                                        <div
                                            className="w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full animate-spin"></div>
                                        <p className="text-slate-500 font-medium">{t('serverSelector.switching')}</p>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {isGuildReady && (
                    <>
                        <button
                            onClick={toggleLogs}
                            className="fixed bottom-6 right-6 z-50 p-4 bg-indigo-600 hover:bg-indigo-700 text-white rounded-full shadow-2xl transition-transform hover:scale-110 active:scale-95 group"
                        >
                            {showLogs ? <X className="w-6 h-6"/> : <MessageSquare className="w-6 h-6"/>}
                            {!showLogs && gameState.logs.length > lastSeenLogCount && (
                                <span
                                    className="absolute top-0 right-0 w-3 h-3 bg-red-500 border-2 border-white dark:border-slate-900 rounded-full"></span>
                            )}
                        </button>

                        {showLogs && (
                            <div
                                className="fixed bottom-24 right-6 z-50 w-[350px] md:w-[400px] h-[500px] max-h-[70vh] shadow-2xl animate-in fade-in slide-in-from-bottom-4 duration-300 pointer-events-auto">
                                <GameLog
                                    logs={gameState.logs}
                                    onGlobalAction={handleGlobalAction}
                                    readonly={user?.role !== 'JUDGE' || isSpectatorSimulation}
                                    className="h-full shadow-2xl border-2 border-indigo-500/20"
                                />
                            </div>
                        )}
                    </>
                )}

                {showSettings && <SettingsModal onClose={() => setShowSettings(false)}/>}

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
                    isVisible={overlayState.visible}
                    title={overlayState.title}
                    status={overlayState.status}
                    logs={overlayState.logs}
                    error={overlayState.error}
                    progress={overlayState.progress}
                    onComplete={() => setOverlayState(prev => ({...prev, visible: false}))}
                />

                <ProgressOverlay
                    isVisible={!!gameState.expel?.voting}
                    title={t('vote.expelVote')}
                    status="processing"
                    logs={[]}
                >
                    <VoteStatus
                        candidates={gameState.expel?.candidates || []}
                        totalVoters={gameState.players.filter(p => p.isAlive).length}
                        endTime={undefined}
                        players={gameState.players}
                        title={t('vote.expelVote')}
                    />
                </ProgressOverlay>

                {showTimerModal && (
                    <TimerControlModal
                        onClose={() => setShowTimerModal(false)}
                        onStart={handleTimerStart}
                    />
                )}

                <SessionExpiredModal
                    isOpen={showSessionExpired}
                    onClose={() => {
                        setShowSessionExpired(false);
                        window.location.href = '/api/auth/login';
                    }}
                />

                {playerSelectModal.visible && (
                    <PlayerSelectModal
                        title={
                            playerSelectModal.type === 'ASSIGN_JUDGE' ? t('modal.assignJudge') :
                                playerSelectModal.type === 'DEMOTE_JUDGE' ? t('modal.demoteJudge') :
                                    playerSelectModal.type === 'FORCE_POLICE' ? t('modal.forcePolice') : ''
                        }
                        players={playerSelectModal.customPlayers || gameState.players}
                        onClose={() => setPlayerSelectModal({
                            ...playerSelectModal,
                            visible: false,
                            customPlayers: undefined
                        })}
                        onSelect={handlePlayerSelect}
                        filter={(p) => {
                            if (!p.userId) return false;
                            if (playerSelectModal.type === 'ASSIGN_JUDGE') return !p.isJudge;
                            if (playerSelectModal.type === 'DEMOTE_JUDGE') return !!p.isJudge;
                            if (playerSelectModal.type === 'FORCE_POLICE') return p.isAlive;
                            return true;
                        }}
                    />
                )}
            </main>
        </div>
    );
};
