import { useTranslation } from "react-i18next";
import { useGameStore } from "@/stores/gameStore";

/** The always-visible connection trust signal — breathing cyan dot when live, red when offline. */
export function LiveIndicator({ connected, compact }: { connected: boolean; compact?: boolean }) {
  const { t } = useTranslation();
  const pongCount = useGameStore((s) => s.pongCount);

  return (
    <span className={`wh-live ${connected ? "wh-live--on" : "wh-live--off"}`}>
      <span className="wh-live__dot-wrapper">
        <span className="wh-live__dot" />
        {connected && pongCount > 0 && (
          <span
            key={pongCount}
            className="wh-live__pong-pulse"
          />
        )}
      </span>
      {!compact && <span>{connected ? t("connection.live") : t("connection.offline")}</span>}
    </span>
  );
}

