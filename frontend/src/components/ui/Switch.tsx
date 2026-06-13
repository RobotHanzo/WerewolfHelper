import { useTranslation } from "react-i18next";

export function Switch({
  checked,
  onChange,
  label,
  description,
  saving,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  label?: string;
  description?: string;
  saving?: boolean;
}) {
  const { t } = useTranslation();
  return (
    <div className="wh-switch">
      <span
        role="switch"
        aria-checked={checked}
        tabIndex={0}
        className={`wh-switch__track ${checked ? "wh-switch__track--on" : ""}`}
        onClick={() => onChange(!checked)}
        onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && onChange(!checked)}
      >
        <span className="wh-switch__knob" />
      </span>
      {(label || description) && (
        <span style={{ display: "flex", flexDirection: "column", flex: 1 }}>
          {label && <span style={{ fontWeight: 700, fontSize: 14 }}>{label}</span>}
          {description && <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{description}</span>}
        </span>
      )}
      {saving && <span style={{ fontSize: 11, color: "var(--moon-400)" }}>{t("settings.saving")}</span>}
    </div>
  );
}
