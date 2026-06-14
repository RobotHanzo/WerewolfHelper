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
      revenge: (seat: number, target: number) => {
        if (demo) {
          patch((snap) =>
            recompute(
              mapSeat(
                mapSeat(snap, seat, (s) => ({ ...s, revengePending: false })),
                target,
                (s) => {
                  const identities = s.identities.map((i, idx) => (idx === 0 ? { ...i, dead: true } : i));
                  return { ...s, identities, alive: identities.some((i) => !i.dead) };
                },
              ),
            ),
          );
        } else void api.revenge(guildId, seat, target);
      },
      duel: (seat: number, target: number) => {
        if (demo) {
          patch((snap) => {
            const targetSeat = snap.seats.find((s) => s.seat === target);
            const targetIsWolf = targetSeat?.identities.some((i) => i.faction === "WOLF") ?? false;
            const dead = targetIsWolf ? target : seat;
            return recompute(
              mapSeat({ ...snap, phase: targetIsWolf ? "NIGHT" : snap.phase }, dead, (s) => {
                const identities = s.identities.map((i, idx) => (idx === 0 ? { ...i, dead: true } : i));
                return { ...s, identities, alive: identities.some((i) => !i.dead) };
              }),
            );
          });
        } else void api.duel(guildId, seat, target);
      },
      selfDestruct: (seat: number) => {
        if (demo) {
          patch((snap) =>
            recompute(
              mapSeat({ ...snap, phase: "NIGHT" }, seat, (s) => {
                const identities = s.identities.map((i, idx) => (idx === 0 ? { ...i, dead: true } : i));
                return { ...s, identities, alive: identities.some((i) => !i.dead) };
              }),
            ),
          );
        } else void api.selfDestruct(guildId, seat);
      },
      pause: () =>
        demo
          ? patch((s) => {
              if (!s.paused) return { ...s, paused: true, pausedAt: Date.now() };
              // Resume: shift every frozen deadline forward by the paused duration (mirrors the backend).
              const elapsed = Date.now() - (s.pausedAt ?? Date.now());
              const shift = (v: number | null | undefined) => (v == null ? v : v + elapsed);
              return {
                ...s,
                paused: false,
                pausedAt: null,
                timerEndsAt: shift(s.timerEndsAt) ?? null,
                speech: s.speech ? { ...s.speech, endsAt: shift(s.speech.endsAt) ?? null } : s.speech,
                poll: s.poll ? { ...s.poll, endsAt: shift(s.poll.endsAt) ?? null } : s.poll,
                night: s.night ? { ...s.night, endsAt: shift(s.night.endsAt) ?? null } : s.night,
              };
            })
          : void api.pause(guildId),
      skipPhase: () => {
        if (demo) {
          patch((s) => {
            // mirror the backend GameFlowService.next phase machine
            let phase = s.phase;
            let day = s.day;
            switch (s.phase) {
              case "LOBBY":
              case "ASSIGNMENT":
                phase = "NIGHT"; day = day < 1 ? 1 : day; break;
              case "NIGHT": phase = "DAWN"; break;
              case "DAWN":
              case "DAY":
                phase = day === 1 ? "POLICE_ELECTION" : "SPEECHES"; break;
              case "POLICE_ELECTION": phase = "SPEECHES"; break;
              case "SPEECHES": phase = "EXPEL_VOTE"; break;
              case "EXPEL_VOTE": phase = "NIGHT"; day = day + 1; break;
              default: return s;
            }
            return { ...s, phase, day, speech: null, poll: null, night: null, timerEndsAt: null };
          });
        } else {
          void api.nextPhase(guildId);
        }
      },
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
            pausedAt: null,
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
      setWitchSelfSave: (value: boolean) =>
         demo ? patch((s) => ({ ...s, witchSelfSave: value })) : void api.setWitchSelfSave(guildId, value),
      setHiddenWolfKnife: (value: boolean) =>
         demo ? patch((s) => ({ ...s, hiddenWolfInheritsKnife: value })) : void api.setHiddenWolfKnife(guildId, value),
      setPool: (pool: Record<string, number>) =>
         demo ? patch((s) => ({ ...s, pool })) : void api.setPool(guildId, pool),
      setPlayerCount: (count: number) => {
        if (!demo) {
          openProgress(t("longOp.title.resize"));
          api.setPlayerCount(guildId, count).catch(handleApiError);
        }
      },
      setDay: (day: number) =>
         demo ? patch((s) => ({ ...s, day })) : void api.setDay(guildId, day),
      startSpeech: () => {
        if (demo) {
          patch((s) => {
            const aliveSeats = s.seats.filter((seat) => seat.alive).map((seat) => seat.seat).sort((a, b) => a - b);
            if (aliveSeats.length === 0) return s;
            const police = s.policeSeat && aliveSeats.includes(s.policeSeat) ? s.policeSeat : null;
            if (police) {
              // mirror the backend: park on the police's direction choice
              return {
                ...s,
                phase: "SPEECHES",
                speech: { active: true, waiting: true, direction: "DOWN", fromSeat: police, speakerSeat: null, endsAt: null, order: [], upcoming: [], interruptVoters: [], interruptThreshold: Math.floor(aliveSeats.length / 2) + 1, lastWords: false },
                poll: null,
              };
            }
            const fromSeat = aliveSeats[0];
            const order = [...aliveSeats];
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
                interruptVoters: [],
                interruptThreshold: Math.floor(aliveSeats.length / 2) + 1,
                lastWords: false,
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
                stage: "ENROLL",
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
                  interruptVoters: [],
                },
              };
            } else {
              // flow complete: clear it but stay in the phase (the judge advances explicitly)
              return { ...s, speech: null };
            }
          });
        } else {
          void api.skipSpeaker(guildId);
        }
      },
      terminateSpeech: () => {
        if (demo) {
          patch((s) => ({ ...s, speech: null }));
        } else {
          void api.stopSpeech(guildId);
        }
      },
      setSpeechDirection: (direction: "UP" | "DOWN") => {
        if (demo) {
          patch((s) => {
            if (!s.speech?.waiting) return s;
            const aliveSeats = s.seats.filter((seat) => seat.alive).map((seat) => seat.seat).sort((a, b) => a - b);
            const fromSeat = s.speech.fromSeat ?? aliveSeats[0];
            const startIdx = Math.max(0, aliveSeats.indexOf(fromSeat));
            const ordered =
              direction === "DOWN"
                ? [...aliveSeats.slice(startIdx), ...aliveSeats.slice(0, startIdx)]
                : [aliveSeats[startIdx], ...aliveSeats.slice(0, startIdx).reverse(), ...aliveSeats.slice(startIdx + 1).reverse()];
            return {
              ...s,
              speech: {
                ...s.speech,
                waiting: false,
                direction,
                speakerSeat: ordered[0],
                endsAt: Date.now() + 60000,
                order: ordered,
                upcoming: ordered.slice(1),
                interruptThreshold: Math.floor(aliveSeats.length / 2) + 1,
              },
            };
          });
        } else {
          void api.setSpeechDirection(guildId, direction);
        }
      },
      advancePoll: () => {
        if (demo) {
          patch((s) => {
            if (!s.poll) return s;
            const order = ["ENROLL", "CAMPAIGN", "WITHDRAW", "VOTING", "RESOLVED"] as const;
            const next = order[Math.min(order.length - 1, order.indexOf(s.poll.stage as any) + 1)];
            return next === "RESOLVED" ? { ...s, poll: null } : { ...s, poll: { ...s.poll, stage: next } };
          });
        } else {
          void api.advancePoll(guildId);
        }
      },
      resolvePoll: () => {
        if (demo) {
          patch((s) => ({ ...s, poll: null }));
        } else {
          void api.resolvePoll(guildId);
        }
      },
      muteAll: () => {
        if (demo) {
          useUiStore.getState().showToast(t("dashboard.cmd.muteAll"));
        } else {
          api.muteAll(guildId)
            .then(() => useUiStore.getState().showToast(t("dashboard.cmd.muteAll")))
            .catch(handleApiError);
        }
      },
      unmuteAll: () => {
        if (demo) {
          useUiStore.getState().showToast(t("dashboard.cmd.unmuteAll"));
        } else {
          api.unmuteAll(guildId)
            .then(() => useUiStore.getState().showToast(t("dashboard.cmd.unmuteAll")))
            .catch(handleApiError);
        }
      },
      startTimer: (seconds: number) => {
        if (demo) {
          patch((s) => ({
            ...s,
            timerEndsAt: Date.now() + seconds * 1000,
          }));
        } else {
          api.startTimer(guildId, seconds).catch(handleApiError);
        }
      },
      stopTimer: () => {
        if (demo) {
          patch((s) => ({ ...s, timerEndsAt: null }));
        } else {
          api.stopTimer(guildId).catch(handleApiError);
        }
      },
    };
  }, [guildId, demo, patch, openProgress, t]);
}
