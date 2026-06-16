import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";
import { useGameStore } from "@/stores/gameStore";
import { useUiStore, type Density } from "@/stores/uiStore";
import { useGameActions } from "@/hooks/useGameActions";
import { useGuild } from "@/hooks/useGuild";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { PlayerCard } from "@/components/ui/PlayerCard";
import { LogFeed } from "@/components/ui/LogFeed";
import { Button } from "@/components/ui/Button";
import { Countdown } from "@/components/ui/Countdown";
import { Avatar } from "@/components/ui/Avatar";
import { NightBoard, WolfChatPanel } from "./NightBoard";
import { SpeechBoard } from "./SpeechBoard";

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
  const isPhone = useMediaQuery("(max-width: 520px)");
  const isTablet = useMediaQuery("(max-width: 860px)");

  // Two-step revenge / duel: pick the actor, then click a target seat.
  const [targeting, setTargeting] = useState<{ kind: "revenge" | "duel"; seat: number } | null>(null);
  const roleNameById = useMemo(() => {
    const m = new Map<string, string>();
    snapshot?.seats.forEach((s) => s.identities.forEach((i) => m.set(i.roleId, i.name)));
    return m;
  }, [snapshot]);

  // The log panel is in view on the dashboard → mark logs read while here.
  useEffect(() => {
    setLogVisible(true);
    return () => setLogVisible(false);
  }, [setLogVisible]);

  if (!snapshot) return null;
  const speaker = snapshot.speech?.speakerSeat != null
    ? snapshot.seats.find((s) => s.seat === snapshot.speech!.speakerSeat)
    : undefined;
  const isLobby = snapshot.phase === "LOBBY";
  const isNight = snapshot.phase === "NIGHT";
  // Roster columns track the user's density on desktop; on narrow screens
  // we cap them so cards stay legible (phones go single/double column).
  const cols: Record<Density, number> = isPhone
    ? { comfort: 1, compact: 2, list: 1 }
    : isTablet
      ? { comfort: 2, compact: 3, list: 1 }
      : { comfort: 3, compact: 4, list: 1 };

  return (
    <div style={{ maxWidth: 1180, margin: "0 auto" }}>
      {/* status header */}
      <div className="wh-card" style={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: 16, padding: "14px 18px", marginBottom: 16 }}>
        <span style={{ display: "flex", flexDirection: "column" }}>
          <span style={{ fontSize: 16, fontWeight: 900 }}>{t(PHASE_KEY[snapshot.phase])}</span>
          <span style={{ fontSize: 12, color: "var(--text-muted)" }}>
            {snapshot.phase === "NIGHT" ? t("phase.nightCounter", { day: snapshot.day }) : t("phase.dayCounter", { day: snapshot.day })}
          </span>
        </span>
        {snapshot.speech?.active && snapshot.speech.speakerSeat != null && (
          <button onClick={() => navigate(`/server/${guildId}/speech`)} style={{ display: "flex", alignItems: "center", gap: 12, background: "none", border: "none", cursor: "pointer", padding: 0 }}>
            <Avatar size="sm" speaking name={speaker?.displayName ?? `#${snapshot.speech.speakerSeat}`} avatar={speaker?.avatar} />
            <span style={{ display: "flex", flexDirection: "column", textAlign: "left" }}>
              <span style={{ fontSize: 13, fontWeight: 700 }}>{t("dashboard.speakerSpeaking", { seat: String(snapshot.speech.speakerSeat).padStart(2, "0") })}</span>
              <span style={{ fontSize: 11, color: "var(--moon-400)" }}>{t("dashboard.goSpeech")}</span>
            </span>
            {snapshot.speech.endsAt && <Countdown endsAt={snapshot.speech.endsAt} size="sm" />}
          </button>
        )}
        {snapshot.timerEndsAt && (
          <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <Countdown endsAt={snapshot.timerEndsAt} size="sm" />
            {!readOnly && (
              <Button size="sm" variant="ghost" onClick={actions.stopTimer}>
                {t("common.cancel")}
              </Button>
            )}
          </span>
        )}
        {snapshot.orderLockEndsAt && (
          <Countdown endsAt={snapshot.orderLockEndsAt} size="sm" label={t("dashboard.orderLock")} />
        )}
        <span style={{ marginLeft: "auto", display: "flex", gap: 8 }}>
          {!readOnly && isLobby && (
            snapshot.assigned ? (
              <Button variant="primary" onClick={actions.startGame}>{t("dashboard.startGame")}</Button>
            ) : (
              <Button variant="primary" onClick={actions.assign}>{t("dashboard.cmd.assign")}</Button>
            )
          )}
          {!readOnly && !isLobby && snapshot.phase !== "OVER" && (
            <>
              <Button variant="secondary" size="sm" onClick={actions.pause}>
                {snapshot.paused ? t("dashboard.resume") : t("dashboard.pause")}
              </Button>
              <Button variant="secondary" size="sm" armed armedLabel={t("dashboard.skipArmed")} onClick={actions.skipPhase}>
                {t("dashboard.skip")}
              </Button>
            </>
          )}
        </span>
      </div>

      {/* game over */}
      {snapshot.winner && (
        <div style={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: 14, padding: "18px 22px", marginBottom: 16, borderRadius: "var(--r-lg)", background: "var(--success-dim)", border: "1px solid rgba(62,220,151,0.4)" }}>
          <span style={{ fontSize: 18, fontWeight: 900, color: "var(--success-500)" }}>
            {t("dashboard.gameOver", { winner: snapshot.winner.faction === "WOLF" ? t("faction.wolfFull") : t("faction.godFull") })}
          </span>
          <span style={{ fontSize: 12, color: "var(--text-secondary)" }}>{snapshot.winner.reason}</span>
          {!readOnly && (
            <span style={{ marginLeft: "auto" }}>
              {snapshot.winRevealed ? (
                <span style={{ fontSize: 12, fontWeight: 700, color: "var(--success-500)" }}>{t("dashboard.winRevealedTag")}</span>
              ) : (
                <Button variant="primary" size="sm" armed armedLabel={t("dashboard.confirmWinArmed")} onClick={actions.confirmWin}>
                  {t("dashboard.confirmWin")}
                </Button>
              )}
            </span>
          )}
        </div>
      )}
      {!readOnly && snapshot.winner && !snapshot.winRevealed && (
        <p style={{ margin: "-8px 0 16px", fontSize: 12, color: "var(--text-muted)", lineHeight: 1.6 }}>
          {t("dashboard.confirmWinHint")}
        </p>
      )}

      {/* During the night the board leads (carries the wolf-chat panel beside the phases). */}
      <AnimatePresence>{isNight && snapshot.night && (snapshot.night.active || snapshot.night.resolved) && <NightBoard night={snapshot.night} wolfChat={snapshot.wolfChat} />}</AnimatePresence>

      {/* speech-manager stages (發言 / 警長競選 / 放逐投票) surface here the same way the night does */}
      <AnimatePresence>{(snapshot.speech?.active || snapshot.speech?.waiting || snapshot.poll) && <SpeechBoard />}</AnimatePresence>

      {/* Once the stage moves off the night, the board drops below the active stage panel and
          collapses into a recap (click its header to expand). */}
      <AnimatePresence>{!isNight && snapshot.night && (snapshot.night.active || snapshot.night.resolved) && <NightBoard night={snapshot.night} wolfChat={snapshot.wolfChat} collapsible />}</AnimatePresence>

      {/* wolf chat keeps syncing across every phase — show it on its own when the night board is down */}
      {!(snapshot.night && (snapshot.night.active || snapshot.night.resolved)) && snapshot.wolfChat.length > 0 && (
        <div className="wh-card wh-wolfchat-standalone" style={{ marginBottom: 16, overflow: "hidden", border: "1px solid var(--wolf-700)" }}>
          <WolfChatPanel messages={snapshot.wolfChat} />
        </div>
      )}

      <div className="wh-dash-grid">
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
          {targeting && (
            <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "8px 12px", marginBottom: 10, borderRadius: "var(--r-sm)", background: "var(--wolf-dim)", border: "1px solid rgba(240,74,94,0.35)", fontSize: 12, fontWeight: 700, color: "var(--wolf-400)" }}>
              {t("dashboard.seatAction.pickTarget")}（玩家{String(targeting.seat).padStart(2, "0")} · {t(`dashboard.seatAction.${targeting.kind}`)}）
              <Button size="sm" variant="ghost" style={{ marginLeft: "auto" }} onClick={() => setTargeting(null)}>
                {t("dashboard.seatAction.cancelTarget")}
              </Button>
            </div>
          )}
          <motion.div layout style={{ display: "grid", gridTemplateColumns: `repeat(${cols[density]}, 1fr)`, gap: density === "compact" ? 10 : 12 }}>
            {snapshot.seats.map((seat) => (
              <PlayerCard
                key={seat.seat}
                seat={seat}
                readOnly={readOnly}
                phase={snapshot.phase}
                learnedRoleName={seat.learnedRoleId ? roleNameById.get(seat.learnedRoleId) : undefined}
                targeting={targeting != null && targeting.seat !== seat.seat}
                changed={changedSeats.has(seat.seat)}
                onKill={(idx, name) => openKill({ seat: seat.seat, identityIndex: idx, identityName: name })}
                onRevive={() => actions.revive(seat.seat)}
                onReviveIdentity={(idx) => actions.reviveIdentity(seat.seat, idx)}
                onEdit={() => openEdit(seat.seat)}
                onRevenge={() => setTargeting({ kind: "revenge", seat: seat.seat })}
                onDuel={() => setTargeting({ kind: "duel", seat: seat.seat })}
                onSelfDestruct={() => actions.selfDestruct(seat.seat)}
                onPickTarget={() => {
                  if (!targeting) return;
                  if (targeting.kind === "revenge") actions.revenge(targeting.seat, seat.seat);
                  else actions.duel(targeting.seat, seat.seat);
                  setTargeting(null);
                }}
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
                <Button size="sm" variant="secondary" onClick={actions.muteAll}>{t("dashboard.cmd.muteAll")}</Button>
                <Button size="sm" variant="secondary" onClick={actions.unmuteAll}>{t("dashboard.cmd.unmuteAll")}</Button>
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
