import { useState } from 'react';
import { useTranslation } from '@/lib/i18n';
import { Session } from '@/api/types.gen';
import { OverlayState } from './useGameState';
import { useMutation } from '@tanstack/react-query';
import {
  assignRolesMutation,
  manualStartTimerMutation,
  muteAllMutation,
  nextStateMutation,
  resetGameMutation,
  reviveMutation,
  reviveRoleMutation,
  setPoliceMutation,
  startGameMutation,
  switchRoleOrderMutation,
  unmuteAllMutation,
  updateUserRoleMutation,
} from '@/api/@tanstack/react-query.gen';
import { getMembers } from '@/api/sdk.gen';

export const useGameActions = (
  guildId: string | undefined,
  gameState: Session | null,
  setGameState: React.Dispatch<React.SetStateAction<Session | null>>,
  setOverlayState: React.Dispatch<React.SetStateAction<OverlayState>>
) => {
  const { t } = useTranslation();

  // Mutations
  const revivePlayer = useMutation(reviveMutation());
  const reviveRole = useMutation(reviveRoleMutation());
  const setPolice = useMutation(setPoliceMutation());
  const switchRoleOrder = useMutation(switchRoleOrderMutation());
  const assignRoles = useMutation(assignRolesMutation());
  const startGame = useMutation(startGameMutation());
  const nextState = useMutation(nextStateMutation());
  const resetGame = useMutation(resetGameMutation());
  const manualStartTimer = useMutation(manualStartTimerMutation());
  const muteAll = useMutation(muteAllMutation());
  const unmuteAll = useMutation(unmuteAllMutation());
  const updateUserRole = useMutation(updateUserRoleMutation());

  // Modal States that are triggered by actions
  const [showTimerModal, setShowTimerModal] = useState(false);
  const [editingPlayerId, setEditingPlayerId] = useState<number | null>(null);
  const [deathConfirmPlayerId, setDeathConfirmPlayerId] = useState<number | null>(null);
  const [playerSelectModal, setPlayerSelectModal] = useState<{
    visible: boolean;
    type:
      | 'ASSIGN_JUDGE'
      | 'DEMOTE_JUDGE'
      | 'FORCE_POLICE'
      | 'ADD_SPECTATOR'
      | 'REMOVE_SPECTATOR'
      | null;
    customPlayers?: any[];
  }>({ visible: false, type: null });

  const handleAction = async (playerId: number, actionType: string) => {
    if (!guildId || !gameState?.players) return;
    const playersArray = Object.values(gameState.players);
    const player = playersArray.find((p) => p.id === playerId);
    if (!player) return;

    const gId = guildId;

    if (actionType === 'role') {
      setEditingPlayerId(playerId);
      return;
    }

    try {
      if (actionType === 'kill') {
        setDeathConfirmPlayerId(playerId);
      } else if (actionType === 'revive') {
        await revivePlayer.mutateAsync({ path: { guildId: gId, playerId } });
      } else if (actionType.startsWith('revive_role:')) {
        const role = actionType.split(':')[1];
        await reviveRole.mutateAsync({ path: { guildId: gId, playerId }, query: { role } });
      } else if (actionType === 'toggle-jin') {
        // Toggle Jin Bao Bao logic
      } else if (actionType === 'sheriff') {
        await setPolice.mutateAsync({ path: { guildId: gId, playerId } });
      } else if (actionType === 'switch_role_order') {
        await switchRoleOrder.mutateAsync({ path: { guildId: gId, playerId } });
      }
    } catch (error) {
      console.error('Action failed:', error);
    }
  };

  const handleGlobalAction = (action: string) => {
    if (!guildId) return;
    const gId = guildId;

    if (action === 'assign_roles') {
      const performAssign = async () => {
        try {
          await assignRoles.mutateAsync({ path: { guildId: gId } });
        } catch (error: any) {
          console.error('Assign roles failed', error);
        }
      };
      performAssign();
    } else if (action === 'start_game') {
      setGameState((prev) => {
        if (!prev) return null;
        return {
          ...prev,
          currentState: 'NIGHT_START', // Approximate
          day: 1,
          // logs: [...prev.logs] // logs is mutable in state?
        };
      });
      // Also call API to start game logic on backend
      const performStart = async () => {
        try {
          await startGame.mutateAsync({ path: { guildId: gId } });
        } catch (error: any) {
          console.error('Start game failed', error);
        }
      };
      performStart();
    } else if (action === 'next_step') {
      if (guildId) {
        // Use new API if available
        nextState
          .mutateAsync({ path: { guildId: gId } })
          .then(() => {})
          .catch((err) => {
            console.error('Next state failed', err);
          });
      } else {
        // Fallback for demo/no-backend if needed, but we essentially require backend now
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
          progress: 0,
        });

        try {
          await resetGame.mutateAsync({ path: { guildId: gId } });

          setOverlayState((prev) => ({
            ...prev,
            status: 'success',
            logs: [...prev.logs, t('overlayMessages.resetSuccess')],
          }));
        } catch (error: any) {
          console.error('Reset failed', error);
          setOverlayState((prev) => {
            const errorMsg = `${t('errors.error')}: ${error.message || t('errors.unknownError')}`;
            const logs = [...prev.logs];
            if (!logs.some((l) => l.includes(error.message))) {
              logs.push(errorMsg);
            }
            return {
              ...prev,
              status: 'error',
              logs: logs,
              error: error.message || t('errors.resetFailed'),
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
          progress: 0,
        });

        try {
          await assignRoles.mutateAsync({ path: { guildId: gId } });

          setOverlayState((prev) => ({
            ...prev,
            status: 'success',
            logs: [...prev.logs, t('overlayMessages.assignSuccess')],
          }));
        } catch (error: any) {
          console.error('Assign failed', error);
          setOverlayState((prev) => {
            const errorMsg = `${t('errors.error')}: ${error.message || t('errors.unknownError')}`;
            const logs = [...prev.logs];
            if (!logs.some((l) => l.includes(error.message))) {
              logs.push(errorMsg);
            }

            return {
              ...prev,
              status: 'error',
              logs: logs,
              error: error.message || t('errors.assignFailed'),
            };
          });
        }
      };
      performRandomAssign();
    } else if (action === 'timer_start') {
      setShowTimerModal(true);
    } else if (action === 'mute_all') {
      muteAll.mutate({ path: { guildId: gId } });
    } else if (action === 'unmute_all') {
      unmuteAll.mutate({ path: { guildId: gId } });
    } else if (
      action === 'assign_judge' ||
      action === 'demote_judge' ||
      action === 'add_spectator' ||
      action === 'remove_spectator'
    ) {
      getMembers({ path: { guildId: gId } })
        .then((response) => {
          const members = response.data?.data;
          if (!members || !Array.isArray(members)) {
            console.warn('No members returned from API or invalid format', response.data);
            return;
          }

          const mappedPlayers = members.map((m: any) => ({
            id: m.id,
            name: m.display || m.name,
            nickname: m.display || m.name,
            userId: m.id,
            avatar: m.avatar,
            roles: m.roles || [],
            isJudge: false,
            // Defaults
            deadRoles: [],
            isAlive: true,
            isSheriff: false,
            isJinBaoBao: false,
            isProtected: false,
            isPoisoned: false,
            isSilenced: false,
            statuses: [],
          }));
          setPlayerSelectModal({
            visible: true,
            type:
              action === 'assign_judge'
                ? 'ASSIGN_JUDGE'
                : action === 'demote_judge'
                  ? 'DEMOTE_JUDGE'
                  : action === 'add_spectator'
                    ? 'ADD_SPECTATOR'
                    : 'REMOVE_SPECTATOR',
            customPlayers: mappedPlayers,
          });
        })
        .catch((err) => {
          console.error('Failed to fetch members', err);
        });
    } else if (action === 'force_police') {
      setPlayerSelectModal({ visible: true, type: 'FORCE_POLICE' });
    }
  };

  const handleTimerStart = (seconds: number) => {
    if (guildId) {
      manualStartTimer.mutate({ path: { guildId }, body: { duration: seconds } });
    }
  };

  const handlePlayerSelect = async (playerId: number | string) => {
    const players =
      playerSelectModal.customPlayers ||
      (gameState?.players ? Object.values(gameState.players) : []);
    const player = players.find((p: any) => p.id == playerId);
    if (!player || !guildId) return;
    const gId = guildId;

    if (playerSelectModal.type === 'ASSIGN_JUDGE' && player.userId) {
      await updateUserRole.mutateAsync({
        path: { guildId: gId, userId: player.userId.toString() },
        body: { role: 'JUDGE' },
      });
    } else if (playerSelectModal.type === 'DEMOTE_JUDGE' && player.userId) {
      await updateUserRole.mutateAsync({
        path: { guildId: gId, userId: player.userId.toString() },
        body: { role: 'SPECTATOR' },
      });
    } else if (playerSelectModal.type === 'ADD_SPECTATOR' && player.userId) {
      await updateUserRole.mutateAsync({
        path: { guildId: gId, userId: player.userId.toString() },
        body: { role: 'SPECTATOR' },
      });
    } else if (playerSelectModal.type === 'REMOVE_SPECTATOR' && player.userId) {
      await updateUserRole.mutateAsync({
        path: { guildId: gId, userId: player.userId.toString() },
        body: { role: 'PENDING' },
      });
    } else if (playerSelectModal.type === 'FORCE_POLICE') {
      await setPolice.mutateAsync({ path: { guildId: gId, playerId: Number(player.id) } });
    }

    // Close modal after action?
    setPlayerSelectModal((prev) => ({ ...prev, visible: false, customPlayers: undefined }));
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
