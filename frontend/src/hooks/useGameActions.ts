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
      startGame: () => {
        if (demo) {
          patch((s) => {
            if (!s.assigned) {
              useUiStore.getState().showToast(t("dashboard.error.not_assigned"), true);
              return s;
            }
            return { ...s, phase: "NIGHT", started: true, day: 1 };
          });
        } else {
          void api.startGame(guildId);
        }
      },
      assign: () => {
        if (demo) {
          patch((snap) => {
            const sampleRoles = [
              { roleId: "villager", name: "平民", faction: "VILLAGER" },
              { roleId: "villager", name: "平民", faction: "VILLAGER" },
              { roleId: "seer", name: "預言家", faction: "GOD" },
              { roleId: "witch", name: "女巫", faction: "GOD" },
              { roleId: "hunter", name: "獵人", faction: "GOD" },
              { roleId: "guard", name: "守衛", faction: "GOD" },
              { roleId: "wolf", name: "狼人", faction: "WOLF" },
              { roleId: "wolf", name: "狼人", faction: "WOLF" },
              { roleId: "wolf_king", name: "狼王", faction: "WOLF" },
              { roleId: "idiot", name: "白癡", faction: "GOD" },
              { roleId: "villager", name: "平民", faction: "VILLAGER" },
              { roleId: "villager", name: "平民", faction: "VILLAGER" },
            ] as const;

            const seats = snap.seats.map((seat, i) => {
              const r = sampleRoles[i % sampleRoles.length];
              return {
                ...seat,
                unassigned: false,
                alive: true,
                identities: [
                  {
                    roleId: r.roleId,
                    name: r.name,
                    faction: r.faction as any,
                    dead: false,
                  },
                ],
              };
            });
            return {
              ...snap,
              seats,
              assigned: true,
              aliveCount: seats.length,
            };
          });
        } else {
          openProgress(t("longOp.title.assign"));
          api.assign(guildId).catch(handleApiError);
        }
      },
      reset: () => {
        if (demo) {
          patch((s) => ({
            ...s,
            phase: "LOBBY",
            day: 0,
            started: false,
            assigned: false,
            paused: false,
            winner: null,
            timerEndsAt: null,
            speech: null,
            poll: null,
            night: null,
            seats: s.seats.map((seat) => ({
              ...seat,
              unassigned: true,
              alive: false,
              identities: [],
              police: false,
              goldenBaby: false,
            })),
          }));
        } else {
          openProgress(t("longOp.title.reset"));
          api.reset(guildId).catch(handleApiError);
        }
      },
      forcePolice: (seat: number) => void (demo || api.forcePolice(guildId, seat)),
      setDoubleIdentity: (value: boolean) =>
         demo ? patch((s) => ({ ...s, doubleIdentity: value })) : void api.setDoubleIdentity(guildId, value),
      setMuteAfterSpeech: (value: boolean) =>
         demo ? patch((s) => ({ ...s, muteAfterSpeech: value })) : void api.setMuteAfterSpeech(guildId, value),
      setPool: (pool: Record<string, number>) =>
         demo ? patch((s) => ({ ...s, pool })) : void api.setPool(guildId, pool),
      setPlayerCount: (count: number) => {
        if (!demo) {
          openProgress(t("longOp.title.resize"));
          api.setPlayerCount(guildId, count).catch(handleApiError);
        }
      },
      startSpeech: () => {
        if (demo) {
          patch((s) => {
            const aliveSeats = s.seats.filter((seat) => seat.alive).map((seat) => seat.seat).sort((a, b) => a - b);
            if (aliveSeats.length === 0) return s;
            const fromSeat = s.policeSeat && aliveSeats.includes(s.policeSeat) ? s.policeSeat : aliveSeats[0];
            const startIdx = aliveSeats.indexOf(fromSeat);
            const order = [...aliveSeats.slice(startIdx), ...aliveSeats.slice(0, startIdx)];
            return {
              ...s,
              phase: "SPEECHES",
              speech: {
                active: true,
                waiting: false,
                direction: "DOWN",
                fromSeat,
                speakerSeat: fromSeat,
                endsAt: Date.now() + 60000,
                order,
                upcoming: order.slice(1),
              },
              poll: null,
            };
          });
        } else {
          void api.nextPhase(guildId);
        }
      },
      startElection: () => {
        if (demo) {
          patch((s) => {
            const aliveSeats = s.seats.filter((seat) => seat.alive).map((seat) => seat.seat).sort((a, b) => a - b);
            return {
              ...s,
              phase: "POLICE_ELECTION",
              speech: null,
              poll: {
                kind: "POLICE",
                stage: "VOTING",
                endsAt: Date.now() + 30000,
                eligibleVoters: aliveSeats.length,
                votesCast: 0,
                candidates: aliveSeats.slice(0, 3).map((seat) => ({
                  seat,
                  withdrawn: false,
                  weight: 0.0,
                  voters: [],
                })),
              },
            };
          });
        } else {
          void api.nextPhase(guildId);
        }
      },
      startExpel: () => {
        if (demo) {
          patch((s) => {
            const aliveSeats = s.seats.filter((seat) => seat.alive).map((seat) => seat.seat).sort((a, b) => a - b);
            return {
              ...s,
              phase: "EXPEL_VOTE",
              speech: null,
              poll: {
                kind: "EXPEL",
                stage: "VOTING",
                endsAt: Date.now() + 30000,
                eligibleVoters: aliveSeats.length,
                votesCast: 0,
                candidates: aliveSeats.slice(0, 3).map((seat) => ({
                  seat,
                  withdrawn: false,
                  weight: 0.0,
                  voters: [],
                })),
              },
            };
          });
        } else {
          void api.nextPhase(guildId);
        }
      },
      skipSpeech: () => {
        if (demo) {
          patch((s) => {
            if (!s.speech || !s.speech.active) return s;
            if (s.speech.upcoming.length > 0) {
              const nextSpeaker = s.speech.upcoming[0];
              const upcoming = s.speech.upcoming.slice(1);
              return {
                ...s,
                speech: {
                  ...s.speech,
                  speakerSeat: nextSpeaker,
                  endsAt: Date.now() + 60000,
                  upcoming,
                },
              };
            } else {
              return {
                ...s,
                phase: "EXPEL_VOTE",
                speech: null,
              };
            }
          });
        } else {
          void api.nextPhase(guildId);
        }
      },
      terminateSpeech: () => {
        if (demo) {
          patch((s) => ({
            ...s,
            phase: "EXPEL_VOTE",
            speech: null,
          }));
        } else {
          void api.nextPhase(guildId);
        }
      },
    };
  }, [guildId, demo, patch, openProgress, t]);
}
