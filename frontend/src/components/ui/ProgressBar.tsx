export function ProgressBar({ percent, state = "running" }: { percent: number; state?: "running" | "success" | "error" }) {
  return (
    <div className={`wh-progress wh-progress--${state}`}>
      <div className="wh-progress__fill" style={{ width: `${Math.min(100, Math.max(0, percent))}%` }} />
    </div>
  );
}
