import { useTranslation } from "react-i18next";

/**
 * Placeholder shell — replaced in the screens phase with the full router
 * (entry states → app shell → dashboard / speech / spectator / settings).
 */
export function App() {
  const { t } = useTranslation();
  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: "var(--sp-3)",
      }}
    >
      <img src="/logo.svg" alt="" width={64} height={64} />
      <h1 style={{ margin: 0, fontWeight: 900, letterSpacing: "0.04em" }}>{t("app.name")}</h1>
      <span
        className="mono"
        style={{ fontSize: 11, color: "var(--moon-400)", letterSpacing: "0.24em" }}
      >
        {t("app.wordmark")}
      </span>
    </div>
  );
}
