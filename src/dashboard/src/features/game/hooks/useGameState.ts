import {useEffect, useState} from 'react';
import {useTranslation} from '@/lib/i18n';
import {useWebSocket} from '@/lib/websocket';
import {api} from '@/lib/api';
import {GameState, Player, User} from '@/types';
import {INITIAL_PLAYERS} from '@/utils/mockData';

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
    const [gameState, setGameState] = useState<GameState>({
        phase: 'LOBBY',
        dayCount: 0,
        timerSeconds: 0,
        players: INITIAL_PLAYERS,
        logs: [],
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
            isProtected: false,
            isPoisoned: false,
            isSilenced: false,
            isDuplicated: player.duplicated,
            isJudge: player.isJudge || false,
            rolePositionLocked: player.rolePositionLocked,
            statuses: [
                ...(player.police ? ['sheriff'] : []),
                ...(player.jinBaoBao ? ['jinBaoBao'] : []),
            ] as any, // using any to bypass strictly typed array check for now, can be improved
        }));
    };

    // WebSocket connection
    const {isConnected} = useWebSocket((message) => {
        const {type, data} = message;

        // Check for progress events
        if (type === 'PROGRESS') {
            console.log('Incoming PROGRESS event:', {
                serverGuildId: data.guildId,
                clientGuildId: guildId,
                match: data.guildId?.toString() === guildId,
                message: data.message,
                percent: data.percent
            });

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
            console.log('WebSocket update received:', data);

            const players = mapSessionToPlayers(data);
            setGameState(prev => ({
                ...prev,
                players: players,
                doubleIdentities: data.doubleIdentities,
                availableRoles: data.roles || [],
                speech: data.speech,
                police: data.police,
                expel: data.expel, // Ensure expel is updated too
                logs: data.logs || prev.logs,
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
                    speech: sessionData.speech,
                    police: sessionData.police,
                    expel: sessionData.expel,
                    logs: sessionData.logs || [],
                }));
            } catch (error) {
                console.error('Failed to load session data:', error);
            }
        };

        loadGameState();
    }, [guildId, user]);

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
