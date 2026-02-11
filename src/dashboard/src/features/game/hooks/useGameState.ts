import { useEffect, useState } from 'react';
import { useTranslation } from '@/lib/i18n';
import { useWebSocket } from '@/lib/websocket';
import { Session, AuthData as User } from '@/api/types.gen';
import { usePlayerContext } from '@/features/players/contexts/PlayerContext';
import { useQuery } from '@tanstack/react-query';
import { getSession } from '@/api/sdk.gen';

export interface OverlayState {
  visible: boolean;
  title: string;
  logs: string[];
  status: 'processing' | 'success' | 'error';
  error?: string;
  progress?: number;
}

export const useGameState = (guildId: string | undefined, user: User | null) => {
  const { t } = useTranslation();
  const { userInfoCache, fetchUserInfo, updateSinglePlayerCache } = usePlayerContext();
  const [gameState, setGameState] = useState<Session | null>(null);
  const [timerSeconds, setTimerSeconds] = useState<number>(0);

  const [overlayState, setOverlayState] = useState<OverlayState>({
    visible: false,
    title: '',
    logs: [],
    status: 'processing',
    error: undefined,
    progress: undefined,
  });

  const [showSessionExpired, setShowSessionExpired] = useState(false);

  // React Query for initial state load
  const { data: sessionData, error: sessionError } = useQuery({
    queryKey: ['getSession', { guildId: guildId || '0' }],
    queryFn: async () => {
      if (!guildId) return null;
      const response = await getSession({
        path: { guildId: guildId },
      });
      return (response.data?.data as Session) || null;
    },
    enabled: !!guildId && !!user && user.user.guildId === guildId,
    refetchOnWindowFocus: false,
  });

  // Update state when data is loaded from query or WebSocket
  const updateState = (data: Session) => {
    if (!data) return;
    setGameState(data);

    // Trigger user info fetch for players we don't have in cache
    if (data.players) {
      Object.values(data.players).forEach((player) => {
        if (player.userId && !userInfoCache[player.userId] && guildId) {
          fetchUserInfo(player.userId.toString(), guildId);
        }
      });
    }
  };

  // Update timer based on currentStepEndTime
  useEffect(() => {
    if (!gameState?.currentStepEndTime) {
      setTimerSeconds(0);
      return;
    }

    const tick = () => {
      const now = BigInt(Date.now());
      const endTime = BigInt(gameState.currentStepEndTime);
      const diff = Number((endTime - now) / 1000n);
      setTimerSeconds(Math.max(0, diff));
    };

    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [gameState?.currentStepEndTime]);

  // Initialize state from Query data
  useEffect(() => {
    if (sessionData) {
      updateState(sessionData);
    }
  }, [sessionData]);

  // Handle Query Error (Session Expired)
  useEffect(() => {
    if (sessionError) {
      console.error('Failed to load session data:', sessionError);
    }
  }, [sessionError]);

  // WebSocket connection
  const { isConnected } = useWebSocket(
    (message) => {
      const { type, data } = message;

      // Check for progress events
      if (type === 'PROGRESS') {
        if (data.guildId === guildId) {
          setOverlayState((prev) => {
            const newState = { ...prev, visible: true };
            const isError =
              data.message &&
              (data.message.includes('錯誤') ||
                data.message.includes('Error') ||
                data.message.includes('Failed'));

            if (data.percent === 0) {
              newState.logs = data.message ? [data.message] : [];
              newState.status = 'processing';
              newState.error = undefined;
              newState.title = t('progressOverlay.processing');
            } else if (data.message) {
              const trimmedMessage = data.message.trim();
              if (!prev.logs.some((log) => log.trim() === trimmedMessage)) {
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

      // Check for player updates
      if (type === 'PLAYER_UPDATE') {
        if (data && data.userId && data.name && data.avatar) {
          updateSinglePlayerCache(data.userId, { name: data.name, avatar: data.avatar });
        }
      }

      // Check if the update is for the current guild
      if (type === 'UPDATE' && data && data.guildId === guildId) {
        updateState(data.session);
      }
    },
    user?.user?.role && user.user.role !== 'PENDING' && user.user.role !== 'BLOCKED'
      ? guildId
      : null,
    () => setShowSessionExpired(true)
  );

  return {
    gameState,
    setGameState,
    isConnected,
    overlayState,
    setOverlayState,
    showSessionExpired,
    setShowSessionExpired,
    timerSeconds,
  };
};
