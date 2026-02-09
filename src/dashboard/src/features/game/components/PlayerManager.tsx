import {useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {MessageSquare, X} from 'lucide-react';
import {useTranslation} from '@/lib/i18n';
import {useAuth} from '@/features/auth/contexts/AuthContext';

// Hooks
import {useGameState} from '../hooks/useGameState';
import {useGameActions} from '../hooks/useGameActions';
import {useDashboardAuth} from '@/features/auth/hooks/useDashboardAuth';
import {Player} from '@/api/types.gen';

// Components
import {Sidebar} from '@/components/layout/Sidebar';
import {GameHeader} from './GameHeader';
import {GameLog} from './GameLog';
import {GameRoutes} from './GameRoutes';
import {PlayerEditModal} from '@/features/players/components/PlayerEditModal';
import {DeathConfirmModal} from './DeathConfirmModal';
import {ProgressOverlay} from '@/components/ui/ProgressOverlay';
import {TimerControlModal} from './TimerControlModal';
import {PlayerSelectModal} from '@/features/players/components/PlayerSelectModal';
import {SessionExpiredModal} from '@/features/auth/components/SessionExpiredModal';

import {ToastContainer, useToast} from '@/components/Toast';

export const PlayerManager = () => {
    const {guildId} = useParams<{ guildId: string }>();
    const navigate = useNavigate();
    const {t} = useTranslation();
    const {user, loading, logout, checkAuth} = useAuth();
    const {toasts, removeToast} = useToast();

    // Local UI State

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
        setShowSessionExpired,
        timerSeconds
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

    // Convert players map to array for components that expect it
    const playersArray: Player[] = gameState?.players
        ? Object.values(gameState.players).sort((a, b) => a.id - b.id)
        : [];

    const isGuildReady = useDashboardAuth(guildId, user, loading, checkAuth, playersArray);

    // Update log count if logs change while open
    useEffect(() => {
        if (showLogs && gameState?.logs) {
            setLastSeenLogCount(gameState.logs.length);
        }
    }, [gameState?.logs?.length, showLogs]);

    if (user?.user?.role === 'PENDING') {
        return (
            <div className="flex items-center justify-center h-screen bg-slate-50 dark:bg-slate-900">
                <div className="text-center p-8 bg-white dark:bg-slate-800 rounded-lg shadow-lg max-w-md">
                    <h2 className="text-2xl font-bold mb-4 text-slate-800 dark:text-slate-100">{t('login.title')}</h2>
                    <p className="text-slate-600 dark:text-slate-300 mb-6">
                        {t('accessDenied.message') || 'Please wait for a judge to approve your join request.'}
                    </p>
                    <button
                        onClick={() => navigate('/')}
                        className="px-4 py-2 bg-indigo-600 text-white rounded hover:bg-indigo-700 transition"
                    >
                        {t('accessDenied.back')}
                    </button>
                </div>
            </div>
        );
    }


    if (!gameState) {
        return (
            <div className="flex items-center justify-center h-screen bg-slate-50 dark:bg-slate-900">
                <div className="flex flex-col items-center gap-4">
                    <div
                        className="w-8 h-8 border-4 border-indigo-600 border-t-transparent rounded-full animate-spin"></div>
                    <p className="text-slate-500 font-medium">{t('messages.loading')}</p>
                </div>
            </div>
        );
    }

    // Editing player helper
    const editingPlayer = playersArray.find(p => p.id === editingPlayerId);

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
            setLastSeenLogCount(gameState.logs?.length || 0);
        }
    };

    return (
        <div
            className="min-h-screen bg-slate-50 dark:bg-slate-900 text-slate-900 dark:text-slate-100 font-sans flex flex-col md:flex-row overflow-hidden">
            <Sidebar
                onLogout={logout}
                onSettingsClick={() => navigate(`/server/${guildId}/settings`)}
                onDashboardClick={() => navigate(`/server/${guildId}`)}
                onPlayersClick={() => navigate(`/server/${guildId}/players`)}
                onSpectatorClick={() => navigate(`/server/${guildId}/spectator`)}
                onSpeechClick={() => navigate(`/server/${guildId}/speech`)}
                onSwitchServer={() => navigate('/')}
                onToggleSpectatorMode={toggleSpectatorSimulation}
                isSpectatorMode={isSpectatorSimulation}
                isConnected={isConnected}
            />
            <main className="flex-1 flex flex-col h-screen overflow-hidden relative">
                <GameHeader
                    dayCount={gameState.day}
                    timerSeconds={timerSeconds}
                    onGlobalAction={handleGlobalAction}
                    speech={(gameState as any).speech}
                    players={playersArray}
                    readonly={user?.user?.role === 'SPECTATOR' || isSpectatorSimulation}
                    currentStep={gameState.currentState} // Assuming currentState for now, or use stateData if more specific
                    currentState={gameState.currentState}
                    guildId={guildId}
                    isManualStep={false} // Derive this if possible, or omit if not in SDK
                    hasAssignedRoles={gameState.hasAssignedRoles}
                />
                <div
                    className="flex-1 overflow-hidden relative flex flex-col lg:flex-row bg-slate-50 dark:bg-slate-900/30">
                    <div className="flex-1 overflow-y-auto scrollbar-hide p-4 md:p-8">
                        <div className="max-w-7xl mx-auto space-y-8">
                            {isGuildReady ? (
                                <>
                                    <GameRoutes
                                        guildId={guildId!}
                                        gameState={gameState}
                                        readonly={user?.user?.role === 'SPECTATOR' || isSpectatorSimulation}
                                        onPlayerAction={handleAction}
                                        players={playersArray}
                                    />
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
                            {!showLogs && (gameState.logs?.length || 0) > lastSeenLogCount && (
                                <span
                                    className="absolute top-0 right-0 w-3 h-3 bg-red-500 border-2 border-white dark:border-slate-900 rounded-full"></span>
                            )}
                        </button>

                        {showLogs && (
                            <div
                                className="fixed bottom-24 right-6 z-50 w-[350px] md:w-[400px] h-[500px] max-h-[70vh] shadow-2xl animate-in fade-in slide-in-from-bottom-4 animate-out fade-out slide-out-from-top-4 duration-300 pointer-events-auto">
                                <GameLog
                                    logs={gameState.logs}
                                    onGlobalAction={handleGlobalAction}
                                    readonly={user?.user?.role !== 'JUDGE' || isSpectatorSimulation}
                                    className="h-full shadow-2xl border-2 border-indigo-500/20"
                                    hasAssignedRoles={gameState.hasAssignedRoles}
                                    currentStep={gameState.currentState}
                                />
                            </div>
                        )}
                    </>
                )}



                {editingPlayerId && editingPlayer && guildId && (
                    <PlayerEditModal
                        player={editingPlayer}
                        allPlayers={playersArray}
                        guildId={guildId}
                        onClose={() => setEditingPlayerId(null)}
                        doubleIdentities={gameState.doubleIdentities}
                        availableRoles={gameState.roles || []}
                    />
                )}

                {deathConfirmPlayerId && guildId && (
                    <DeathConfirmModal
                        player={Object.values(gameState.players).find(p => p.id === deathConfirmPlayerId)!}
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
                        // Disconnect websocket and redirect to login
                        logout().then(() => {
                            navigate('/login', {replace: true});
                        }).catch(() => {
                            navigate('/login', {replace: true});
                        });
                    }}
                />

                {playerSelectModal.visible && (
                    <PlayerSelectModal
                        title={
                            playerSelectModal.type === 'ASSIGN_JUDGE' ? t('modal.assignJudge') :
                                playerSelectModal.type === 'DEMOTE_JUDGE' ? t('modal.demoteJudge') :
                                    playerSelectModal.type === 'FORCE_POLICE' ? t('modal.forcePolice') : ''
                        }
                        players={playerSelectModal.customPlayers || playersArray}
                        onClose={() => setPlayerSelectModal({
                            ...playerSelectModal,
                            visible: false,
                            customPlayers: undefined
                        })}
                        onSelect={handlePlayerSelect}
                        filter={(p) => {
                            if (!p.userId) return false;
                            const isJudge = gameState?.judgeRoleId && p.roles.includes(gameState.judgeRoleId.toString());
                            if (playerSelectModal.type === 'ASSIGN_JUDGE') return !isJudge;
                            if (playerSelectModal.type === 'DEMOTE_JUDGE') return !!isJudge;
                            if (playerSelectModal.type === 'FORCE_POLICE') return p.alive;
                            return true;
                        }}
                    />
                )}

                <ToastContainer toasts={toasts} onRemove={removeToast}/>
            </main>
        </div>
    );
};
