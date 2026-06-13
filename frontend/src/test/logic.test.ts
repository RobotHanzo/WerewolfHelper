import { describe, expect, it } from "vitest";
import { formatClock, secondsUntil, URGENT_THRESHOLD } from "@/components/ui/Countdown";
import {
  nextBackoff,
  WS_BACKOFF_CAP_MS,
  WS_BACKOFF_FACTOR,
  WS_BACKOFF_START_MS,
} from "@/api/ws";
import { diffChangedSeats } from "@/stores/gameStore";
import { buildScenario } from "@/mock/scenarios";
import type { GameSnapshot } from "@/types/snapshot";

describe("countdown math", () => {
  it("formats mm:ss", () => {
    expect(formatClock(0)).toBe("00:00");
    expect(formatClock(9)).toBe("00:09");
    expect(formatClock(90)).toBe("01:30");
    expect(formatClock(605)).toBe("10:05");
  });

  it("computes whole seconds remaining from endsAt, never negative", () => {
    const now = 1_000_000;
    expect(secondsUntil(now + 30_000, now)).toBe(30);
    expect(secondsUntil(now + 500, now)).toBe(1);
    expect(secondsUntil(now - 5_000, now)).toBe(0);
  });

  it("urgent threshold is 10 seconds", () => {
    expect(URGENT_THRESHOLD).toBe(10);
  });
});

describe("ws reconnect backoff", () => {
  it("starts at 1s and grows by 1.5x, capped at 10s", () => {
    let b = nextBackoff(0);
    expect(b).toBe(WS_BACKOFF_START_MS);
    b = nextBackoff(b);
    expect(b).toBe(Math.round(WS_BACKOFF_START_MS * WS_BACKOFF_FACTOR));
    // grows until the cap, then stays there
    for (let i = 0; i < 20; i++) b = nextBackoff(b);
    expect(b).toBe(WS_BACKOFF_CAP_MS);
  });
});

describe("snapshot changed-seat diffing", () => {
  const base = buildScenario("night");

  it("reports no change against itself", () => {
    expect(diffChangedSeats(base, base)).toEqual([]);
  });

  it("returns [] when there is no previous snapshot", () => {
    expect(diffChangedSeats(null, base)).toEqual([]);
  });

  it("detects a seat whose identity died", () => {
    const next: GameSnapshot = {
      ...base,
      seats: base.seats.map((s) =>
        s.seat === 9 ? { ...s, alive: false, identities: s.identities.map((i) => ({ ...i, dead: true })) } : s,
      ),
    };
    expect(diffChangedSeats(base, next)).toEqual([9]);
  });

  it("detects a police badge transfer as a change on the receiving seat", () => {
    const next: GameSnapshot = {
      ...base,
      seats: base.seats.map((s) => (s.seat === 5 ? { ...s, police: true } : s)),
    };
    expect(diffChangedSeats(base, next)).toContain(5);
  });
});
