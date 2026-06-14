import { useEffect, useState } from "react";
import { useGameStore } from "@/stores/gameStore";

type Size = "sm" | "md" | "stage";

/** Whole seconds remaining until [endsAt] (epoch ms), never negative. Pure — unit tested. */
export function secondsUntil(endsAt: number, now: number): number {
  return Math.max(0, Math.ceil((endsAt - now) / 1000));
}

/** mm:ss for a non-negative second count. Pure — unit tested. */
export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const mm = Math.floor(s / 60).toString().padStart(2, "0");
  const ss = (s % 60).toString().padStart(2, "0");
  return `${mm}:${ss}`;
}

/** Seconds are "urgent" (red + pulse) in the final 10. */
export const URGENT_THRESHOLD = 10;

export function Countdown({ endsAt, size = "sm", label }: { endsAt: number | null; size?: Size; label?: string }) {
  // When the game is paused the backend leaves `endsAt` untouched, so we freeze the displayed time at
  // the pause instant (any live countdown would otherwise keep draining to 00:00 while halted). On
  // resume the backend shifts `endsAt` forward by the paused duration and the tick continues seamlessly.
  const pausedAt = useGameStore((s) => (s.snapshot?.paused ? s.snapshot.pausedAt : null));
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (endsAt == null || pausedAt != null) return;
    const id = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(id);
  }, [endsAt, pausedAt]);

  if (endsAt == null) return null;
  const remaining = secondsUntil(endsAt, pausedAt ?? now);
  const urgent = remaining <= URGENT_THRESHOLD;

  return (
    <span className={`wh-countdown wh-countdown--${size} ${urgent ? "wh-urgent" : ""}`}>
      {label && <span className="wh-countdown__label">{label}</span>}
      {urgent && <span aria-hidden>⚠</span>}
      {formatClock(remaining)}
    </span>
  );
}
