import {useEffect, useState} from 'react';
import {useTranslation} from '@/lib/i18n';
import {useWebSocket} from '@/lib/websocket';
import {api} from '@/lib/api';
import {GameState, Player, User} from '@/types';
import {INITIAL_PLAYERS} from '@/utils/mockData';
import {usePlayerContext} from '@/features/players/contexts/PlayerContext';

export interface OverlayState {
    visible: boolean;
    title: string;
    logs: string[];
    status: 'processing' | 'success' | 'error';
    error?: string;
    progress?: number;
}

export const useGameState = (guildId: string | undefined, user: User | null) => {
    const {t} = useTranslation();
    const {userInfoCache, fetchUserInfo} = usePlayerContext();
    const [gameState, setGameState] = useState<GameState>({
        phase: 'LOBBY',
        day: 0,
        timerSeconds: 0,
        players: INITIAL_PLAYERS,
        logs: [],
        isManualStep: false,
    });

    const [overlayState, setOverlayState] = useState<OverlayState>({
        visible: false,
        title: '',
        logs: [],
        status: 'processing',
        error: undefined,
        progress: undefined
    });

    const [showSessionExpired, setShowSessionExpired] = useState(false);

    // Helper to map session data to GameState players
    const mapSessionToPlayers = (sessionData: any): Player[] => {
        return sessionData.players.map((player: any) => {
            const cachedUser = player.userId ? userInfoCache[player.userId] : null;

            // Trigger fetch if missing and we have a userId
            if (player.userId && !cachedUser && guildId) {
                fetchUserInfo(player.userId, guildId);
            }

            return {
                id: player.id,
                name: cachedUser ? cachedUser.name : (player.name || `${t('messages.player')} ${player.id}`),
                userId: player.userId,
                username: cachedUser ? cachedUser.name : player.username, // Fallback or use name as username
                avatar: cachedUser ? cachedUser.avatar : (player.avatar || null),
                roles: player.roles || [],
                deadRoles: player.deadRoles || [],
                isAlive: player.isAlive,
                isSheriff: player.police,
                isJinBaoBao: player.jinBaoBao,
                isProtected: false,
                isPoisoned: false,
                isSilenced: false,
                isDuplicated: player.duplicated,
                isJudge: player.isJudge || false,
                rolePositionLocked: player.rolePositionLocked,
                statuses: [
                    ...(player.police ? ['sheriff'] : []),
                    ...(player.jinBaoBao ? ['jinBaoBao'] : []),
                ] as any,
            };
        });
    };

    // WebSocket connection
    const {isConnected} = useWebSocket((message) => {
        const {type, data} = message;

        // Check for progress events
        if (type === 'PROGRESS') {
            if (data.guildId?.toString() === guildId) {
                setOverlayState(prev => {
                    const newState = {...prev, visible: true};
                    const isError = data.message && (data.message.includes('錯誤') || data.message.includes('Error') || data.message.includes('Failed'));

                    if (data.percent === 0) {
                        newState.logs = data.message ? [data.message] : [];
                        newState.status = 'processing';
                        newState.error = undefined;
                        newState.title = t('progressOverlay.processing');
                    } else if (data.message) {
                        const trimmedMessage = data.message.trim();
                        // Check if specific message already exists to avoid any duplicates
                        if (!prev.logs.some(log => log.trim() === trimmedMessage)) {
                            newState.logs = [...prev.logs, data.message];
                        }
                    }

                    if (isError) {
                        newState.status = 'error';
                        newState.error = data.message || 'Unknown Error';
                    }

                    if (data.percent !== undefined) {
                        newState.progress = data.percent;
                        if (data.percent >= 100 && !isError) {
                            newState.status = 'success';
                        }
                    }
                    return newState;
                });
                return;
            }
        }

        // Check if the update is for the current guild
        if (type === 'UPDATE' && data && data.guildId && data.guildId.toString() === guildId) {
            const players = mapSessionToPlayers(data);
            setGameState(prev => ({
                ...prev,
                players: players,
                doubleIdentities: data.doubleIdentities,
                availableRoles: data.roles || [],
                hasAssignedRoles: data.hasAssignedRoles,
                speech: data.speech || undefined,
                police: data.police,
                expel: data.expel, // Ensure expel is updated too
                logs: data.logs || prev.logs,
                currentState: data.currentState,
                currentStep: data.currentStep,
                stateData: data.stateData,
                day: data.day || 0,
                timerSeconds: typeof data.timerSeconds === 'number' ? data.timerSeconds : prev.timerSeconds,
                isManualStep: data.isManualStep || false,
                phase: (() => {
                    const s = data.currentState;
                    if (!s || s === 'SETUP' || s === 'LOBBY') return 'LOBBY';
                    if (s.includes('NIGHT')) return 'NIGHT';
                    if (s.includes('DAY') || s.includes('VOTE') || s.includes('ELECTION')) return 'DAY'; // Treat voting/election as day for UI
                    return 'DAY'; // Default to DAY for standard game phases
                })()
            }));
        }
    }, guildId, () => setShowSessionExpired(true));

    // Timer Interval
    useEffect(() => {
        const interval = setInterval(() => {
            setGameState(prev => {
                let newTimer = prev.timerSeconds;
                if (prev.phase !== 'LOBBY' && prev.phase !== 'GAME_OVER' && prev.timerSeconds > 0) {
                    newTimer -= 1;
                }
                return {...prev, timerSeconds: newTimer};
            });
        }, 1000);
        return () => clearInterval(interval);
    }, []);

    // Load game state
    useEffect(() => {
        if (!guildId) return;

        // Skip fetch if user is not loaded or not on the correct guild yet
        if (!user || !user.guildId || user.guildId.toString() !== guildId) {
            return;
        }

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
                    speech: sessionData.speech || undefined,
                    police: sessionData.police,
                    expel: sessionData.expel,
                    logs: sessionData.logs || [],
                    currentState: sessionData.currentState,
                    currentStep: sessionData.currentStep,
                    stateData: sessionData.stateData,
                    day: sessionData.day || 0,
                    timerSeconds: typeof sessionData.timerSeconds === 'number' ? sessionData.timerSeconds : 0,
                    isManualStep: sessionData.isManualStep || false,
                    phase: (() => {
                        const s = sessionData.currentState;
                        if (!s || s === 'SETUP' || s === 'LOBBY') return 'LOBBY';
                        if (s.includes('NIGHT')) return 'NIGHT';
                        if (s.includes('DAY') || s.includes('VOTE') || s.includes('ELECTION')) return 'DAY';
                        return 'DAY';
                    })()
                }));
            } catch (error) {
                console.error('Failed to load session data:', error);
            }
        };

        loadGameState();
    }, [guildId, user, userInfoCache]); // Add userInfoCache to dependency to re-render when cache updates

    return {
        gameState,
        setGameState,
        isConnected,
        overlayState,
        setOverlayState,
        showSessionExpired,
        setShowSessionExpired
    };
};
