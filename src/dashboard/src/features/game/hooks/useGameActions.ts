import {useState} from 'react';
import {useTranslation} from '@/lib/i18n';
import {api} from '@/lib/api';
import {GamePhase, GameState} from '@/types';
import {OverlayState} from './useGameState';

export const useGameActions = (
    guildId: string | undefined,
    gameState: GameState,
    setGameState: React.Dispatch<React.SetStateAction<GameState>>,
    setOverlayState: React.Dispatch<React.SetStateAction<OverlayState>>
) => {
    const {t} = useTranslation();

    // Modal States that are triggered by actions
    const [showTimerModal, setShowTimerModal] = useState(false);
    const [editingPlayerId, setEditingPlayerId] = useState<string | null>(null);
    const [deathConfirmPlayerId, setDeathConfirmPlayerId] = useState<string | null>(null);
    const [playerSelectModal, setPlayerSelectModal] = useState<{
        visible: boolean;
        type: 'ASSIGN_JUDGE' | 'DEMOTE_JUDGE' | 'FORCE_POLICE' | null;
        customPlayers?: any[];
    }>({visible: false, type: null});

    const addLog = (msg: string) => {
        setGameState(prev => ({
            ...prev,
            logs: [{
                id: Date.now().toString() + Math.random().toString(36).slice(2),
                timestamp: new Date().toLocaleTimeString(),
                message: msg,
                type: 'info' as const
            }, ...prev.logs].slice(0, 50)
        }));
    };

    const handleAction = async (playerId: string, actionType: string) => {
        if (!guildId) return;
        const player = gameState.players.find(p => p.id === playerId);
        if (!player) return;

        if (actionType === 'role') {
            setEditingPlayerId(playerId);
            return;
        }

        const playerName = player.name;
        addLog(t('gameLog.adminCommand', {action: actionType, player: playerName}));

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
                // Toggle Jin Bao Bao logic
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
            addLog(t('errors.actionFailed', {action: actionType}));
        }
    };

    const handleGlobalAction = (action: string) => {
        addLog(t('gameLog.adminGlobalCommand', {action}));
        if (action === 'start_game') {
            setGameState(prev => ({
                ...prev, phase: 'NIGHT', dayCount: 1, timerSeconds: 30,
                logs: [...prev.logs, {
                    id: Date.now().toString() + Math.random().toString(36).slice(2),
                    timestamp: new Date().toLocaleTimeString(),
                    message: t('gameLog.gameStarted'),
                    type: 'alert'
                }]
            }));
            // Also call API to start game logic on backend
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
            addLog(t('gameLog.gamePaused'));
        } else if (action === 'reset') {
            const performReset = async () => {
                setOverlayState({
                    visible: true,
                    title: t('progressOverlay.resetTitle'),
                    status: 'processing',
                    logs: [t('overlayMessages.resetting')],
                    error: undefined,
                    progress: 0
                });

                try {
                    if (guildId) {
                        await api.resetSession(guildId);
                    } else {
                        throw new Error("Missing Guild ID");
                    }

                    setOverlayState(prev => ({
                        ...prev,
                        status: 'success',
                        logs: [...prev.logs, t('overlayMessages.resetSuccess')]
                    }));
                } catch (error: any) {
                    console.error("Reset failed", error);
                    setOverlayState(prev => ({
                        ...prev,
                        status: 'error',
                        logs: [...prev.logs, `${t('errors.error')}: ${error.message || t('errors.unknownError')}`],
                        error: error.message || t('errors.resetFailed')
                    }));
                }
            };
            performReset();
        } else if (action === 'random_assign') {
            addLog(t('gameLog.randomizeRoles'));

            const performRandomAssign = async () => {
                setOverlayState({
                    visible: true,
                    title: t('messages.randomAssignRoles'),
                    status: 'processing',
                    logs: [t('overlayMessages.requestingAssign')],
                    error: undefined,
                    progress: 0
                });

                try {
                    if (guildId) {
                        await api.assignRoles(guildId);
                    } else {
                        throw new Error("Missing Guild ID");
                    }

                    setOverlayState(prev => ({
                        ...prev,
                        status: 'success',
                        logs: [...prev.logs, t('overlayMessages.assignSuccess')]
                    }));
                } catch (error: any) {
                    console.error("Assign failed", error);
                    setOverlayState(prev => ({
                        ...prev,
                        status: 'error',
                        logs: [...prev.logs, `${t('errors.error')}: ${error.message || t('errors.unknownError')}`],
                        error: error.message || t('errors.assignFailed')
                    }));
                }
            };
            performRandomAssign();
        } else if (action === 'timer_start') {
            setShowTimerModal(true);
        } else if (action === 'mute_all') {
            if (guildId) api.muteAll(guildId).then(() => addLog(t('gameLog.manualCommand', {cmd: 'Mute All'})));
        } else if (action === 'unmute_all') {
            if (guildId) api.unmuteAll(guildId).then(() => addLog(t('gameLog.manualCommand', {cmd: 'Unmute All'})));
        } else if (action === 'assign_judge' || action === 'demote_judge') {
            if (guildId) {
                api.getGuildMembers(guildId).then(members => {
                    const mappedPlayers = members.map(m => ({
                        id: m.userId,
                        name: m.name,
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
            setPlayerSelectModal({visible: true, type: 'FORCE_POLICE'});
        }
    };

    const handleTimerStart = (seconds: number) => {
        if (guildId) {
            api.manualStartTimer(guildId, seconds);
            addLog(t('gameLog.manualCommand', {cmd: `Timer ${seconds}s`}));
        }
    };

    const handlePlayerSelect = async (playerId: string) => {
        const player = (playerSelectModal.customPlayers || gameState.players).find(p => p.id === playerId);
        if (!player || !guildId || !player.userId) return;

        if (playerSelectModal.type === 'ASSIGN_JUDGE') {
            await api.updateUserRole(guildId, player.userId, 'JUDGE');
            addLog(t('gameLog.manualCommand', {cmd: `Promote ${player.name} to Judge`}));
        } else if (playerSelectModal.type === 'DEMOTE_JUDGE') {
            await api.updateUserRole(guildId, player.userId, 'SPECTATOR');
            addLog(t('gameLog.manualCommand', {cmd: `Demote ${player.name}`}));
        } else if (playerSelectModal.type === 'FORCE_POLICE') {
            await api.setPolice(guildId, player.userId);
            addLog(t('gameLog.manualCommand', {cmd: `Force Police ${player.name}`}));
        }

        // Close modal after action?
        setPlayerSelectModal(prev => ({...prev, visible: false, customPlayers: undefined}));
    };

    return {
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
        setPlayerSelectModal,
        addLog
    };
};
