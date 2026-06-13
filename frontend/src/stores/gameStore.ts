import { create } from "zustand";
import type { GameSnapshot, Seat } from "@/types/snapshot";
import type { ProgressEvent } from "@/api/ws";

/** Serialize the parts of a seat that, if changed remotely, should flash the `.wh-changed` cue. */
function seatSignature(seat: Seat): string {
  return JSON.stringify([
    seat.memberId,
    seat.police,
    seat.goldenBaby,
    seat.orderLocked,
    seat.alive,
    seat.identities.map((i) => `${i.roleId}:${i.dead ? 1 : 0}`),
  ]);
}

/**
 * Which seats changed between two snapshots. Pure and exported for unit testing — drives the
 * deliberate "this just changed" peripheral cue on the roster.
 */
export function diffChangedSeats(prev: GameSnapshot | null, next: GameSnapshot): number[] {
  if (!prev) return [];
  const before = new Map(prev.seats.map((s) => [s.seat, seatSignature(s)]));
  return next.seats.filter((s) => before.has(s.seat) && before.get(s.seat) !== seatSignature(s)).map((s) => s.seat);
}

interface ProgressState {
  title: string;
  percent: number;
  lines: ProgressEvent[];
  state: "running" | "success" | "error";
}

interface GameState {
  snapshot: GameSnapshot | null;
  connected: boolean;
  pongCount: number;
  sessionExpired: boolean;
  changedSeats: Set<number>;
  unreadLogs: number;
  logPanelVisible: boolean;
  progress: ProgressState | null;

  applySnapshot: (snapshot: GameSnapshot) => void;
  setConnected: (connected: boolean) => void;
  incrementPongCount: () => void;
  setExpired: (expired: boolean) => void;
  setLogPanelVisible: (visible: boolean) => void;
  markLogsRead: () => void;
  pushProgress: (event: ProgressEvent) => void;
  openProgress: (title: string) => void;
  closeProgress: () => void;
  /** Optimistic / demo-mode local patch; the next server snapshot still wins. */
  patch: (fn: (snapshot: GameSnapshot) => GameSnapshot) => void;
  reset: () => void;
}

export const useGameStore = create<GameState>((set, get) => ({
  snapshot: null,
  connected: false,
  pongCount: 0,
  sessionExpired: false,
  changedSeats: new Set(),
  unreadLogs: 0,
  logPanelVisible: true,
  progress: null,

  applySnapshot: (snapshot) => {
    const prev = get().snapshot;
    const changed = diffChangedSeats(prev, snapshot);
    const newLogs = prev ? Math.max(0, snapshot.log.length - prev.log.length) : 0;
    set((s) => ({
      snapshot,
      changedSeats: new Set(changed),
      unreadLogs: s.logPanelVisible ? 0 : s.unreadLogs + newLogs,
    }));
    if (changed.length > 0) {
      setTimeout(() => set({ changedSeats: new Set() }), 1500);
    }
  },

  setConnected: (connected) => set((s) => ({ connected, pongCount: connected ? s.pongCount : 0 })),
  incrementPongCount: () => set((s) => ({ pongCount: s.pongCount + 1 })),
  setExpired: (sessionExpired) => set({ sessionExpired }),
  setLogPanelVisible: (logPanelVisible) => set((s) => ({ logPanelVisible, unreadLogs: logPanelVisible ? 0 : s.unreadLogs })),
  markLogsRead: () => set({ unreadLogs: 0 }),

  openProgress: (title) => set({ progress: { title, percent: 0, lines: [], state: "running" } }),
  pushProgress: (event) =>
    set((s) => {
      if (!s.progress) return s;
      const isError = /錯誤|Error|Failed/.test(event.line);
      return {
        progress: {
          ...s.progress,
          percent: event.percent,
          lines: [...s.progress.lines, event],
          state: isError ? "error" : event.percent >= 100 ? "success" : "running",
        },
      };
    }),
  closeProgress: () => set({ progress: null }),

  patch: (fn) => {
    const current = get().snapshot;
    if (current) get().applySnapshot(fn(current));
  },

  reset: () => set({ snapshot: null, changedSeats: new Set(), unreadLogs: 0, progress: null, pongCount: 0 }),
}));
