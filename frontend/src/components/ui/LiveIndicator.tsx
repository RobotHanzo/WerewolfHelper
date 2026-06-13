import { useTranslation } from "react-i18next";

/** The always-visible connection trust signal — breathing cyan dot when live, red when offline. */
export function LiveIndicator({ connected, compact }: { connected: boolean; compact?: boolean }) {
  const { t } = useTranslation();
  return (
    <span className={`wh-live ${connected ? "wh-live--on" : "wh-live--off"}`}>
      <span className="wh-live__dot" />
      {!compact && <span>{connected ? t("connection.live") : t("connection.offline")}</span>}
    </span>
  );
}
