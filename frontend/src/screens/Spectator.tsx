import { useTranslation } from "react-i18next";
import { useGameStore } from "@/stores/gameStore";
import { FactionMeter } from "@/components/ui/FactionMeter";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { PlayerCard } from "@/components/ui/PlayerCard";
import { WolfChatPanel } from "./NightBoard";

/** Read-only God's view: faction proportions, a win-condition reminder, and the full roster. */
export function Spectator() {
  const { t } = useTranslation();
  const snapshot = useGameStore((s) => s.snapshot);
  const changedSeats = useGameStore((s) => s.changedSeats);
  if (!snapshot) return null;

  return (
    <div style={{ maxWidth: 1100, margin: "0 auto", display: "flex", flexDirection: "column", gap: 16 }}>
      <header style={{ display: "flex", alignItems: "baseline", gap: 12 }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 900 }}>{t("spectator.title")}</h1>
        <span style={{ fontSize: 13, color: "var(--text-muted)" }}>
          {t("spectator.subtitle", { mode: snapshot.doubleIdentity ? t("spectator.modeDouble") : t("spectator.modeSingle") })}
        </span>
      </header>

      <div className="wh-meter">
        <div className="wh-meter__head">
          <span style={{ fontWeight: 700 }}>{t("spectator.aliveTitle")}</span>
          <span className="wh-meter__count">
            {snapshot.aliveCount} / {snapshot.totalSeats}
          </span>
        </div>
        <ProgressBar percent={snapshot.totalSeats > 0 ? (snapshot.aliveCount / snapshot.totalSeats) * 100 : 0} state="success" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 12 }}>
        {snapshot.meters.map((m) => (
          <FactionMeter key={m.faction} meter={m} />
        ))}
      </div>

      <div style={{ display: "flex", gap: 12, alignItems: "center", padding: "12px 16px", borderRadius: "var(--r-md)", background: "var(--accent-soft)", border: "1px solid rgba(84,210,228,0.25)" }}>
        <p style={{ margin: 0, fontSize: 13, color: "var(--text-secondary)", lineHeight: 1.7 }}>
          {snapshot.doubleIdentity ? t("spectator.winDouble") : t("spectator.winSingle")}
        </p>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 12 }}>
        {snapshot.seats.map((seat) => (
          <PlayerCard key={seat.seat} seat={seat} readOnly changed={changedSeats.has(seat.seat)} />
        ))}
      </div>

      {snapshot.wolfChat.length > 0 && (
        <div className="wh-card wh-wolfchat-standalone" style={{ overflow: "hidden", border: "1px solid var(--wolf-700)" }}>
          <WolfChatPanel messages={snapshot.wolfChat} />
        </div>
      )}
    </div>
  );
}
