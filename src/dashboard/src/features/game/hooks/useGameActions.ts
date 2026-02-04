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

    // Removed addLog function


    const handleAction = async (playerId: string, actionType: string) => {
        if (!guildId) return;
        const player = gameState.players.find(p => p.id === playerId);
        if (!player) return;

        if (actionType === 'role') {
            setEditingPlayerId(playerId);
            return;
        }


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

        }
    };

    const handleGlobalAction = (action: string) => {

        if (action === 'assign_roles') {
            const performAssign = async () => {
                try {
                    if (guildId) {
                        await api.assignRoles(guildId);
                    }
                } catch (error: any) {
                    console.error("Assign roles failed", error);
                }
            };
            performAssign();

        } else if (action === 'start_game') {
            setGameState(prev => ({
                ...prev, phase: 'NIGHT', dayCount: 1, timerSeconds: 30,
                logs: [...prev.logs]
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
            if (guildId) {
                // Use new API if available
                api.nextState(guildId).then(() => {

                }).catch(err => {
                    console.error("Next state failed", err);

                });
            } else {
                setGameState(prev => {
                    const phases: GamePhase[] = ['NIGHT', 'DAY', 'VOTING'];
                    const currentIdx = phases.indexOf(prev.phase as any);
                    const nextPhase = currentIdx > -1 ? phases[(currentIdx + 1) % phases.length] : 'NIGHT';
                    return {
                        ...prev,
                        phase: nextPhase,
                        timerSeconds: nextPhase === 'NIGHT' ? 30 : 60,
                        day: nextPhase === 'NIGHT' ? prev.day + 1 : prev.day
                    };
                });
            }
        } else if (action === 'pause') {

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
                    setOverlayState(prev => {
                        const errorMsg = `${t('errors.error')}: ${error.message || t('errors.unknownError')}`;
                        const logs = [...prev.logs];
                        if (!logs.some(l => l.includes(error.message))) {
                            logs.push(errorMsg);
                        }
                        return {
                            ...prev,
                            status: 'error',
                            logs: logs,
                            error: error.message || t('errors.resetFailed')
                        };
                    });
                }
            };
            performReset();
        } else if (action === 'random_assign') {


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
                    setOverlayState(prev => {
                        const errorMsg = `${t('errors.error')}: ${error.message || t('errors.unknownError')}`;
                        const logs = [...prev.logs];
                        if (!logs.some(l => l.includes(error.message))) {
                            logs.push(errorMsg);
                        }

                        return {
                            ...prev,
                            status: 'error',
                            logs: logs,
                            error: error.message || t('errors.assignFailed')
                        };
                    });
                }
            };
            performRandomAssign();
        } else if (action === 'timer_start') {
            setShowTimerModal(true);
        } else if (action === 'mute_all') {
            if (guildId) api.muteAll(guildId);
        } else if (action === 'unmute_all') {
            if (guildId) api.unmuteAll(guildId);
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

                });
            }
        } else if (action === 'force_police') {
            setPlayerSelectModal({visible: true, type: 'FORCE_POLICE'});
        }
    };

    const handleTimerStart = (seconds: number) => {
        if (guildId) {
            api.manualStartTimer(guildId, seconds);
        }
    };

    const handlePlayerSelect = async (playerId: string) => {
        const player = (playerSelectModal.customPlayers || gameState.players).find(p => p.id === playerId);
        if (!player || !guildId || !player.userId) return;

        if (playerSelectModal.type === 'ASSIGN_JUDGE') {
            await api.updateUserRole(guildId, player.userId, 'JUDGE');
        } else if (playerSelectModal.type === 'DEMOTE_JUDGE') {
            await api.updateUserRole(guildId, player.userId, 'SPECTATOR');
        } else if (playerSelectModal.type === 'FORCE_POLICE') {
            await api.setPolice(guildId, player.userId);
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

    };
};
