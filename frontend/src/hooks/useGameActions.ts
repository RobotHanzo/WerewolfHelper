import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { api } from "@/api/client";
import { useGameStore } from "@/stores/gameStore";
import { useUiStore } from "@/stores/uiStore";
import type { GameSnapshot, Seat } from "@/types/snapshot";

/**
 * Bridges UI actions to the backend. In demo mode (no live backend) the same actions patch the
 * local snapshot optimistically so the showcase is interactive; in live mode the authoritative WS
 * snapshot follows and wins.
 */
export function useGameActions(guildId: string, demo: boolean) {
  const patch = useGameStore((s) => s.patch);
  const openProgress = useGameStore((s) => s.openProgress);
  const { t } = useTranslation();

  return useMemo(() => {
    const mapSeat = (snapshot: GameSnapshot, seat: number, fn: (s: Seat) => Seat): GameSnapshot => ({
      ...snapshot,
      seats: snapshot.seats.map((s) => (s.seat === seat ? fn(s) : s)),
    });

    const recompute = (snapshot: GameSnapshot): GameSnapshot => ({
      ...snapshot,
      aliveCount: snapshot.seats.filter((s) => s.alive && !s.unassigned).length,
    });

    const handleApiError = (err: any) => {
      const errMsg = err.message || String(err);
      useUiStore.getState().showToast(errMsg, true);
      useGameStore.setState((s) => {
        if (!s.progress) return s;
        return {
          progress: {
            ...s.progress,
            state: "error",
            lines: [...s.progress.lines, { percent: 0, line: errMsg, severity: "alert" }]
          }
        };
      });
    };

    return {
      kill: (seat: number, identityIndex: number) => {
        if (demo) {
          patch((snap) =>
            recompute(
              mapSeat(snap, seat, (s) => {
                const identities = s.identities.map((i, idx) => (idx === identityIndex ? { ...i, dead: true } : i));
                return { ...s, identities, alive: identities.some((i) => !i.dead) };
              }),
            ),
          );
        } else void api.kill(guildId, seat, identityIndex, false);
      },
      revive: (seat: number) => {
        if (demo) {
          patch((snap) =>
            recompute(mapSeat(snap, seat, (s) => ({ ...s, identities: s.identities.map((i) => ({ ...i, dead: false })), alive: true }))),
          );
        } else void api.revive(guildId, seat, null);
      },
      reviveIdentity: (seat: number, identityIndex: number) => {
        if (demo) {
          patch((snap) =>
            recompute(
              mapSeat(snap, seat, (s) => {
                const identities = s.identities.map((i, idx) => (idx === identityIndex ? { ...i, dead: false } : i));
                return { ...s, identities, alive: identities.some((i) => !i.dead) };
              }),
            ),
          );
        } else void api.revive(guildId, seat, identityIndex);
      },
      pause: () => (demo ? patch((s) => ({ ...s, paused: !s.paused })) : void api.pause(guildId)),
      startGame: () => (demo ? patch((s) => ({ ...s, phase: "NIGHT", started: true, day: 1 })) : void api.startGame(guildId)),
      assign: () => {
        if (!demo) {
          openProgress(t("longOp.title.assign"));
          api.assign(guildId).catch(handleApiError);
        }
      },
      reset: () => {
        if (!demo) {
          openProgress(t("longOp.title.reset"));
          api.reset(guildId).catch(handleApiError);
        }
      },
      forcePolice: (seat: number) => void (demo || api.forcePolice(guildId, seat)),
      setDoubleIdentity: (value: boolean) =>
         demo ? patch((s) => ({ ...s, doubleIdentity: value })) : void api.setDoubleIdentity(guildId, value),
      setMuteAfterSpeech: (value: boolean) =>
         demo ? patch((s) => ({ ...s, muteAfterSpeech: value })) : void api.setMuteAfterSpeech(guildId, value),
      setPlayerCount: (count: number) => {
        if (!demo) {
          openProgress(t("longOp.title.resize"));
          api.setPlayerCount(guildId, count).catch(handleApiError);
        }
      },
    };
  }, [guildId, demo, patch, openProgress, t]);
}
