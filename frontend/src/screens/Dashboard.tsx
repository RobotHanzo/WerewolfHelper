import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";
import { useGameStore } from "@/stores/gameStore";
import { useUiStore, type Density } from "@/stores/uiStore";
import { useGameActions } from "@/hooks/useGameActions";
import { useGuild } from "@/hooks/useGuild";
import { PlayerCard } from "@/components/ui/PlayerCard";
import { LogFeed } from "@/components/ui/LogFeed";
import { Button } from "@/components/ui/Button";
import { Countdown } from "@/components/ui/Countdown";
import { Avatar } from "@/components/ui/Avatar";
import { NightBoard } from "./NightBoard";

const PHASE_KEY: Record<string, string> = {
  LOBBY: "phase.lobby",
  ASSIGNMENT: "phase.assignment",
  NIGHT: "phase.night",
  DAWN: "phase.dawn",
  DAY: "phase.day",
  POLICE_ELECTION: "election.title",
  SPEECHES: "phase.day",
  EXPEL_VOTE: "expel.title",
  OVER: "phase.over",
};

export function Dashboard() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { guildId, demo, readOnly } = useGuild();
  const snapshot = useGameStore((s) => s.snapshot);
  const changedSeats = useGameStore((s) => s.changedSeats);
  const unreadLogs = useGameStore((s) => s.unreadLogs);
  const density = useUiStore((s) => s.density);
  const setDensity = useUiStore((s) => s.setDensity);
  const openKill = useUiStore((s) => s.openKill);
  const openEdit = useUiStore((s) => s.openEdit);
  const setLogVisible = useGameStore((s) => s.setLogPanelVisible);
  const actions = useGameActions(guildId, demo);

  // The log panel is in view on the dashboard → mark logs read while here.
  useEffect(() => {
    setLogVisible(true);
    return () => setLogVisible(false);
  }, [setLogVisible]);

  if (!snapshot) return null;
  const isLobby = snapshot.phase === "LOBBY";
  const cols: Record<Density, number> = { comfort: 3, compact: 4, list: 1 };

  return (
    <div style={{ maxWidth: 1180, margin: "0 auto" }}>
      {/* status header */}
      <div className="wh-card" style={{ display: "flex", alignItems: "center", gap: 20, padding: "14px 18px", marginBottom: 16 }}>
        <span style={{ display: "flex", flexDirection: "column" }}>
          <span style={{ fontSize: 16, fontWeight: 900 }}>{t(PHASE_KEY[snapshot.phase])}</span>
          <span style={{ fontSize: 12, color: "var(--text-muted)" }}>
            {snapshot.phase === "NIGHT" ? t("phase.nightCounter", { day: snapshot.day }) : t("phase.dayCounter", { day: snapshot.day })}
          </span>
        </span>
        {snapshot.speech?.active && snapshot.speech.speakerSeat != null && (
          <button onClick={() => navigate(`/server/${guildId}/speech`)} style={{ display: "flex", alignItems: "center", gap: 12, background: "none", border: "none", cursor: "pointer", padding: 0 }}>
            <Avatar size="sm" speaking name={`#${snapshot.speech.speakerSeat}`} />
            <span style={{ display: "flex", flexDirection: "column", textAlign: "left" }}>
              <span style={{ fontSize: 13, fontWeight: 700 }}>{t("dashboard.speakerSpeaking", { seat: String(snapshot.speech.speakerSeat).padStart(2, "0") })}</span>
              <span style={{ fontSize: 11, color: "var(--moon-400)" }}>{t("dashboard.goSpeech")}</span>
            </span>
            {snapshot.speech.endsAt && <Countdown endsAt={snapshot.speech.endsAt} size="sm" />}
          </button>
        )}
        {snapshot.timerEndsAt && <Countdown endsAt={snapshot.timerEndsAt} size="sm" />}
        <span style={{ marginLeft: "auto", display: "flex", gap: 8 }}>
          {!readOnly && isLobby && (
            snapshot.assigned ? (
              <Button variant="primary" onClick={actions.startGame}>{t("dashboard.startGame")}</Button>
            ) : (
              <Button variant="primary" onClick={actions.assign}>{t("dashboard.cmd.assign")}</Button>
            )
          )}
          {!readOnly && !isLobby && snapshot.phase !== "OVER" && (
            <Button variant="secondary" size="sm" onClick={actions.pause}>
              {snapshot.paused ? t("dashboard.resume") : t("dashboard.pause")}
            </Button>
          )}
        </span>
      </div>

      {/* game over */}
      {snapshot.winner && (
        <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "18px 22px", marginBottom: 16, borderRadius: "var(--r-lg)", background: "var(--success-dim)", border: "1px solid rgba(62,220,151,0.4)" }}>
          <span style={{ fontSize: 18, fontWeight: 900, color: "var(--success-500)" }}>
            {t("dashboard.gameOver", { winner: snapshot.winner.faction === "WOLF" ? t("faction.wolfFull") : t("faction.godFull") })}
          </span>
          <span style={{ fontSize: 12, color: "var(--text-secondary)" }}>{snapshot.winner.reason}</span>
        </div>
      )}

      {/* night board */}
      <AnimatePresence>{snapshot.night?.active && <NightBoard night={snapshot.night} />}</AnimatePresence>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 16, alignItems: "start" }}>
        {/* roster */}
        <section style={{ display: "flex", flexDirection: "column", gap: 12, minWidth: 0 }}>
          <header style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <h2 className="wh-section-title">{t("dashboard.rosterTitle")}</h2>
            <span className="mono" style={{ fontSize: 12, color: "var(--text-muted)" }}>
              {t("dashboard.aliveCount", { alive: snapshot.aliveCount, total: snapshot.totalSeats })}
            </span>
            <span style={{ marginLeft: "auto", display: "flex", gap: 2, padding: 2, borderRadius: "var(--r-md)", background: "var(--surface-card)", border: "1px solid var(--border-1)" }}>
              {(["comfort", "compact", "list"] as Density[]).map((d) => (
                <button
                  key={d}
                  onClick={() => setDensity(d)}
                  className="wh-btn wh-btn--sm"
                  style={{ background: density === d ? "var(--surface-raised)" : "transparent", color: density === d ? "var(--text-body)" : "var(--text-muted)", border: "none" }}
                >
                  {t(`dashboard.density.${d}`)}
                </button>
              ))}
            </span>
          </header>
          <motion.div layout style={{ display: "grid", gridTemplateColumns: `repeat(${cols[density]}, 1fr)`, gap: density === "compact" ? 10 : 12 }}>
            {snapshot.seats.map((seat) => (
              <PlayerCard
                key={seat.seat}
                seat={seat}
                readOnly={readOnly}
                changed={changedSeats.has(seat.seat)}
                onKill={(idx, name) => openKill({ seat: seat.seat, identityIndex: idx, identityName: name })}
                onRevive={() => actions.revive(seat.seat)}
                onReviveIdentity={(idx) => actions.reviveIdentity(seat.seat, idx)}
                onEdit={() => openEdit(seat.seat)}
              />
            ))}
          </motion.div>
        </section>

        {/* right rail */}
        {!readOnly && (
          <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            <section className="wh-card" style={{ padding: 16, display: "flex", flexDirection: "column", gap: 12 }}>
              <header style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <h2 className="wh-section-title">{t("dashboard.logTitle")}</h2>
                <span className="mono" style={{ fontSize: 10, fontWeight: 700, color: "var(--moon-300)", background: "var(--accent-soft)", borderRadius: "var(--r-full)", padding: "1px 8px" }}>
                  {snapshot.log.length}
                </span>
                {unreadLogs > 0 && (
                  <span className="mono" style={{ fontSize: 10, fontWeight: 700, color: "var(--text-on-accent)", background: "var(--moon-500)", borderRadius: "var(--r-full)", padding: "1px 7px" }}>
                    {unreadLogs}
                  </span>
                )}
              </header>
              <LogFeed entries={snapshot.log} />
            </section>

            <section className="wh-card" style={{ padding: 16, display: "flex", flexDirection: "column", gap: 14 }}>
              <h2 className="wh-section-title">{t("dashboard.commands")}</h2>
              <CommandGroup label={t("dashboard.group.flow")}>
                <Button size="sm" variant="secondary" onClick={actions.assign}>{t("dashboard.cmd.assign")}</Button>
                <Button size="sm" variant="danger" armed armedLabel={t("dashboard.cmd.resetArmed")} onClick={actions.reset}>{t("dashboard.cmd.reset")}</Button>
              </CommandGroup>
              <CommandGroup label={t("dashboard.group.voice")}>
                <Button size="sm" variant="secondary" onClick={() => useUiStore.getState().setTimerOpen(true)}>{t("dashboard.cmd.timer")}</Button>
                <Button size="sm" variant="secondary" onClick={() => useUiStore.getState().showToast(t("dashboard.cmd.muteAll"))}>{t("dashboard.cmd.muteAll")}</Button>
                <Button size="sm" variant="secondary" onClick={() => useUiStore.getState().showToast(t("dashboard.cmd.unmuteAll"))}>{t("dashboard.cmd.unmuteAll")}</Button>
              </CommandGroup>
              <CommandGroup label={t("dashboard.group.admin")}>
                <Button size="sm" variant="ghost" onClick={() => useUiStore.getState().openPicker("promote", t("picker.promote"))}>{t("dashboard.cmd.promote")}</Button>
                <Button size="sm" variant="ghost" onClick={() => useUiStore.getState().openPicker("demote", t("picker.demote"))}>{t("dashboard.cmd.demote")}</Button>
                <Button size="sm" variant="ghost" onClick={() => useUiStore.getState().openPicker("force-police", t("picker.forcePolice"))}>{t("dashboard.cmd.forcePolice")}</Button>
              </CommandGroup>
            </section>
          </div>
        )}
      </div>
    </div>
  );
}

function CommandGroup({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      <span style={{ fontSize: 11, fontWeight: 700, color: "var(--text-muted)", letterSpacing: "0.08em" }}>{label}</span>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>{children}</div>
    </div>
  );
}
