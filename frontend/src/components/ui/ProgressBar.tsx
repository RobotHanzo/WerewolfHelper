export function ProgressBar({ percent, state = "running" }: { percent: number; state?: "running" | "success" | "error" }) {
  return (
    <span className={`wh-progress wh-progress--${state}`} style={{ display: "block" }}>
      <span className="wh-progress__fill" style={{ width: `${Math.min(100, Math.max(0, percent))}%` }} />
    </span>
  );
}
