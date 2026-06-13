import { useEffect, useState } from "react";

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
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (endsAt == null) return;
    const id = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(id);
  }, [endsAt]);

  if (endsAt == null) return null;
  const remaining = secondsUntil(endsAt, now);
  const urgent = remaining <= URGENT_THRESHOLD;

  return (
    <span className={`wh-countdown wh-countdown--${size} ${urgent ? "wh-urgent" : ""}`}>
      {label && <span className="wh-countdown__label">{label}</span>}
      {urgent && <span aria-hidden>⚠</span>}
      {formatClock(remaining)}
    </span>
  );
}
