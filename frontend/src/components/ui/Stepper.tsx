export function Stepper({
  label,
  value,
  min = 0,
  max = 99,
  onChange,
  dirty,
}: {
  label: string;
  value: number;
  min?: number;
  max?: number;
  onChange: (value: number) => void;
  dirty?: boolean;
}) {
  const clamp = (n: number) => Math.min(max, Math.max(min, n));
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      <span style={{ fontSize: 12, color: "var(--text-muted)", fontWeight: 700 }}>{label}</span>
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <button className="wh-btn wh-btn--secondary wh-btn--sm" onClick={() => onChange(clamp(value - 1))} type="button">
          −
        </button>
        <span
          className="mono"
          style={{ fontSize: 22, fontWeight: 700, minWidth: 40, textAlign: "center", color: dirty ? "var(--warning-500)" : "var(--text-body)" }}
        >
          {value}
        </span>
        <button className="wh-btn wh-btn--secondary wh-btn--sm" onClick={() => onChange(clamp(value + 1))} type="button">
          ＋
        </button>
      </div>
    </div>
  );
}
