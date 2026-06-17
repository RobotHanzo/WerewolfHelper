import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "framer-motion";
import type { TFunction } from "i18next";
import { Pause, Play, SkipBack, SkipForward } from "lucide-react";
import { api, ApiError } from "@/api/client";
import { useAuthStore } from "@/stores/authStore";
import { Avatar } from "@/components/ui/Avatar";
import { FactionBadge } from "@/components/ui/Badge";
import { FactionMeter } from "@/components/ui/FactionMeter";
import { Button } from "@/components/ui/Button";
import { buildReplayScenario } from "@/mock/replayScenario";
import { formatTime, useReplayPlayer } from "@/hooks/useReplayPlayer";
import type { ReplayFilter } from "@/hooks/useReplayPlayer";
import type { Replay } from "@/types/replay";
import "@/components/replay/replay.css";

function phaseVisual(phaseType: string) {
  if (phaseType === "day") {
    return {
      glyph: "日",
      color: "var(--warning-500)",
      bg: "rgba(255,180,84,0.12)",
      border: "rgba(255,180,84,0.4)",
      glow: "0 0 0 1px rgba(255,180,84,0.28), 0 0 26px rgba(255,180,84,0.14)",
      rootBg: "radial-gradient(120% 78% at 50% -12%, rgba(255,180,84,0.09), transparent 54%), linear-gradient(180deg, #0F1421, #0B0E16)",
      stageBg: "linear-gradient(180deg, rgba(40,33,22,0.42), rgba(16,20,31,0.4))",
    };
  }
  if (phaseType === "end") {
    return {
      glyph: "終",
      color: "var(--success-500)",
      bg: "var(--success-dim)",
      border: "rgba(62,220,151,0.42)",
      glow: "0 0 0 1px rgba(62,220,151,0.3), 0 0 26px rgba(62,220,151,0.16)",
      rootBg: "radial-gradient(120% 90% at 50% 0%, rgba(62,220,151,0.11), transparent 60%), linear-gradient(180deg, #08110D, #0B0E16)",
      stageBg: "linear-gradient(180deg, rgba(16,38,28,0.5), rgba(16,20,31,0.4))",
    };
  }
  return {
    glyph: "夜",
    color: "var(--moon-300)",
    bg: "var(--accent-soft)",
    border: "rgba(84,210,228,0.4)",
    glow: "0 0 0 1px rgba(84,210,228,0.3), 0 0 26px rgba(84,210,228,0.18)",
    rootBg: "radial-gradient(120% 78% at 50% -12%, rgba(41,184,205,0.12), transparent 58%), linear-gradient(180deg, #070A11, #0B0E16)",
    stageBg: "linear-gradient(180deg, rgba(20,28,48,0.55), rgba(16,20,31,0.4))",
  };
}

const GLYPH: Record<string, string> = {
  system: "報", seer: "預", witch: "巫", hunter: "獵", speech: "言",
  police: "警", vote: "票", death: "死", wolf: "狼", result: "勝",
};
const COLOR: Record<string, { c: string; d: string }> = {
  system: { c: "var(--text-secondary)", d: "var(--surface-raised)" },
  seer: { c: "var(--god-500)", d: "var(--god-dim)" },
  witch: { c: "var(--god-500)", d: "var(--god-dim)" },
  hunter: { c: "var(--god-500)", d: "var(--god-dim)" },
  speech: { c: "var(--text-secondary)", d: "var(--surface-raised)" },
  police: { c: "var(--badge-gold)", d: "var(--badge-gold-dim)" },
  vote: { c: "var(--moon-400)", d: "var(--accent-soft)" },
  death: { c: "var(--danger-500)", d: "var(--danger-dim)" },
  wolf: { c: "var(--wolf-500)", d: "var(--wolf-dim)" },
  result: { c: "var(--success-500)", d: "var(--success-dim)" },
};
const SEG_COLOR: Record<string, { idle: string; active: string }> = {
  night: { idle: "rgba(43,52,80,0.85)", active: "rgba(60,78,120,0.95)" },
  day: { idle: "rgba(70,58,36,0.85)", active: "rgba(110,90,48,0.95)" },
  end: { idle: "rgba(22,52,40,0.85)", active: "rgba(34,78,58,0.95)" },
};

export function ReplayPlayer() {
  const { id } = useParams();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const demo = useAuthStore((s) => s.demo);
  const [replay, setReplay] = useState<Replay | null>(null);
  const [state, setState] = useState<"loading" | "error" | "ok">("loading");

  useEffect(() => {
    if (demo || id === "demo") {
      setReplay(buildReplayScenario());
      setState("ok");
      return;
    }
    if (!id) return;
    api.getReplay(id)
      .then((r) => {
        setReplay(r);
        setState("ok");
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) navigate("/login", { replace: true });
        else setState("error");
      });
  }, [id, demo, navigate]);

  if (state === "loading") {
    return <Centered>{t("replay.loading")}</Centered>;
  }
  if (state === "error" || !replay) {
    return (
      <Centered>
        <span style={{ color: "var(--danger-500)", fontWeight: 700 }}>⚠ {t("replay.notFound")}</span>
        <Button size="sm" onClick={() => navigate("/replays")}>{t("replay.back")}</Button>
      </Centered>
    );
  }
  return <Player replay={replay} onExit={() => navigate("/replays")} />;
}

function Centered({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 14, background: "#0B0E16", color: "var(--text-body)" }}>
      {children}
    </div>
  );
}

function Player({ replay, onExit }: { replay: Replay; onExit: () => void }) {
  const { t } = useTranslation();
  const p = useReplayPlayer(replay);
  const streamRef = useRef<HTMLDivElement>(null);
  const wolfRef = useRef<HTMLDivElement>(null);

  const pv = phaseVisual(p.cur?.phaseType ?? "night");
  const phaseLabel =
    p.cur?.phaseType === "day" ? t("replay.phaseDay", { day: p.cur?.day ?? 1 })
      : p.cur?.phaseType === "end" ? t("replay.phaseEnd")
        : t("replay.phaseNight", { day: p.cur?.day ?? 1 });
  const wolfOpen = (p.cur?.phaseType ?? "night") === "night";

  // auto-scroll the stream + wolf panels as the playhead advances
  useLayoutEffect(() => {
    if (streamRef.current) streamRef.current.scrollTop = streamRef.current.scrollHeight;
    if (wolfRef.current) wolfRef.current.scrollTop = wolfRef.current.scrollHeight;
  }, [p.curIndex, p.filter]);

  const causeOf = useMemo(() => {
    const m = new Map<number, string>();
    p.timeline.forEach((e) => {
      e.effect?.kill.forEach((s) => {
        m.set(s, e.type === "hunter" ? t("replay.causeHunter") : e.phaseType === "night" ? t("replay.causeNight") : t("replay.causeExile"));
      });
    });
    return m;
  }, [p.timeline, t]);

  return (
    <div style={{ position: "relative", height: "100vh", overflow: "hidden", display: "flex", flexDirection: "column", background: "#0B0E16", color: "var(--text-body)", fontFamily: "var(--font-ui)" }}>
      {/* ambiance */}
      <motion.div
        aria-hidden
        animate={{ opacity: 1 }}
        style={{ position: "absolute", inset: 0, pointerEvents: "none", background: pv.rootBg, transition: "background var(--dur-slow) var(--ease-out)" }}
      />

      {/* top bar */}
      <div style={{ position: "relative", zIndex: 2, flex: "none", height: 60, display: "flex", alignItems: "center", gap: 16, padding: "0 22px", borderBottom: "1px solid var(--border-1)", background: "rgba(11,14,22,0.66)", backdropFilter: "blur(8px)" }}>
        <img src="/logo.svg" alt="" width={34} height={34} />
        <div style={{ display: "flex", flexDirection: "column", lineHeight: 1.25 }}>
          <span style={{ fontSize: 15, fontWeight: 900, letterSpacing: "0.02em" }}>{t("replay.title")}</span>
          <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{replay.title}</span>
        </div>
        <span style={{ width: 1, height: 26, background: "var(--border-1)", margin: "0 2px" }} />
        <div style={{ display: "inline-flex", alignItems: "center", gap: 8, height: 32, padding: "0 14px", borderRadius: "var(--r-full)", border: `1px solid ${pv.border}`, background: pv.bg }}>
          <span style={{ fontSize: 13, fontWeight: 900, color: pv.color }}>{pv.glyph}</span>
          <span style={{ fontSize: 13, fontWeight: 700, color: pv.color }}>{phaseLabel}</span>
        </div>
        <div style={{ flex: 1 }} />
        <SpoilerToggle on={p.spoiler} onToggle={p.toggleSpoiler} label={t("replay.spoiler")} />
        <span style={{ display: "inline-flex", alignItems: "center", gap: 8, height: 30, padding: "0 13px", borderRadius: "var(--r-full)", border: "1px solid rgba(84,210,228,0.4)", background: "var(--accent-soft)", color: "var(--moon-300)", fontSize: 12, fontWeight: 700 }}>
          <span style={{ width: 7, height: 7, borderRadius: "50%", background: "var(--moon-400)", boxShadow: "0 0 10px var(--moon-400)" }} />
          {t("replay.mode")}
        </span>
        <Button variant="ghost" size="sm" onClick={onExit}>{t("replay.back")}</Button>
      </div>

      {/* body */}
      <div style={{ position: "relative", zIndex: 1, flex: 1, minHeight: 0, display: "flex" }}>
        {/* main column */}
        <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", padding: "20px 22px 16px", gap: 16, overflow: "hidden" }}>
          {/* phase stage */}
          <div style={{ flex: "none", display: "flex", alignItems: "center", gap: 20, padding: "18px 22px", borderRadius: "var(--r-xl)", border: "1px solid var(--border-1)", background: pv.stageBg, boxShadow: "var(--shadow-1)", transition: "background var(--dur-slow) var(--ease-out)" }}>
            <div style={{ flex: "none", width: 62, height: 62, borderRadius: "var(--r-full)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 26, fontWeight: 900, color: pv.color, background: pv.bg, border: `1px solid ${pv.border}`, boxShadow: pv.glow }}>{pv.glyph}</div>
            <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 6 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ fontSize: 22, fontWeight: 900, letterSpacing: "0.02em" }}>{phaseLabel}</span>
                {p.cur && (
                  <span style={{ fontSize: 12, fontWeight: 700, color: COLOR[p.cur.type]?.c, background: COLOR[p.cur.type]?.d, border: "1px solid var(--border-1)", borderRadius: "var(--r-sm)", padding: "2px 9px" }}>
                    {t(`replay.label.${p.cur.type}` as const, { defaultValue: t("replay.label.system") })}
                  </span>
                )}
              </div>
              <span style={{ fontSize: 15, lineHeight: 1.5, color: "var(--text-secondary)" }}>{p.cur?.text ?? ""}</span>
            </div>
            <div style={{ flex: "none", display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 3 }}>
              <span className="mono" style={{ fontSize: 13, color: "var(--text-muted)" }}>{t("replay.eventCounter", { n: p.eventNo, total: p.eventTotal })}</span>
              {p.cur && <span className="mono" style={{ fontSize: 22, fontWeight: 700 }}>{formatTime(p.cur.atMs)}</span>}
            </div>
          </div>

          {/* faction strip */}
          <div style={{ flex: "none", display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 12 }}>
            {p.factions.map((fm) => (
              <FactionMeter key={fm.faction} meter={fm} />
            ))}
          </div>

          {/* seat board */}
          <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", gap: 10 }}>
            <div style={{ flex: "none", display: "flex", alignItems: "center", gap: 8 }}>
              <span style={{ fontSize: 12, fontWeight: 700, color: "var(--text-muted)", letterSpacing: "0.08em" }}>{t("replay.seatBoard")}</span>
              <span style={{ flex: 1, height: 1, background: "var(--border-1)" }} />
              <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{p.revealAll ? t("replay.spoilerOn") : t("replay.spoilerOff")}</span>
            </div>
            <div style={{ flex: 1, minHeight: 0, overflowY: "auto", overflowX: "hidden", paddingRight: 4 }}>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0,1fr))", gap: 12 }}>
                {p.seats.map((st) => (
                  <motion.div
                    key={st.seat}
                    layout
                    className={st.changed ? "wh-changed" : ""}
                    style={{ background: "var(--surface-card)", border: "1px solid var(--border-1)", borderRadius: "var(--r-lg)", boxShadow: "var(--shadow-1)", padding: 12, display: "flex", flexDirection: "column", gap: 10, opacity: st.dead ? 0.62 : 1, transition: "opacity var(--dur-base) var(--ease-out)" }}
                  >
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <Avatar name={st.name} avatar={st.avatar} size="md" dead={st.dead} />
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                          <span className="mono" style={{ fontSize: 14, fontWeight: 700, textDecoration: st.dead ? "line-through" : "none" }}>玩家{String(st.seat).padStart(2, "0")}</span>
                          {st.police && (
                            <span style={{ display: "inline-flex", alignItems: "center", height: 20, padding: "0 7px", borderRadius: "var(--r-sm)", background: "var(--badge-gold-dim)", border: "1px solid rgba(242,201,76,0.42)", color: "var(--badge-gold)", fontSize: 11, fontWeight: 700 }}>{t("replay.police")}</span>
                          )}
                        </div>
                        <span style={{ display: "block", fontSize: 12, color: "var(--text-muted)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{st.name}</span>
                      </div>
                    </div>
                    {st.reveal && (
                      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                        <FactionBadge faction={st.faction} roleId={st.roleId} name={st.roleName} dead={st.dead} size="sm" />
                      </div>
                    )}
                  </motion.div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* right rail */}
        <div style={{ flex: "none", width: 404, display: "flex", flexDirection: "column", borderLeft: "1px solid var(--border-1)", background: "rgba(16,20,31,0.5)" }}>
          {/* filter chips */}
          <div style={{ flex: "none", padding: "14px 16px 10px", display: "flex", flexWrap: "wrap", gap: 6, borderBottom: "1px solid var(--border-1)" }}>
            {(["all", "speech", "vote", "death", "skill", "wolf"] as ReplayFilter[]).map((f) => {
              const on = p.filter === f;
              return (
                <button
                  key={f}
                  type="button"
                  onClick={() => p.setFilter(f)}
                  style={{ height: 28, padding: "0 12px", borderRadius: "var(--r-full)", fontFamily: "var(--font-ui)", fontSize: 12, fontWeight: 700, cursor: "pointer", border: `1px solid ${on ? "rgba(84,210,228,0.4)" : "var(--border-1)"}`, background: on ? "var(--accent-soft)" : "var(--surface-card)", color: on ? "var(--moon-300)" : "var(--text-muted)", transition: "all var(--dur-fast) var(--ease-out)" }}
                >
                  {t(`replay.filter.${f}` as const)}
                </button>
              );
            })}
          </div>

          {/* event stream */}
          <div style={{ flex: "1 1 auto", minHeight: 150, display: "flex", flexDirection: "column" }}>
            <div style={{ flex: "none", padding: "12px 16px 6px", fontSize: 12, fontWeight: 700, color: "var(--text-muted)", letterSpacing: "0.08em" }}>{t("replay.eventStream")}</div>
            <div ref={streamRef} style={{ flex: 1, minHeight: 0, overflowY: "auto", overflowX: "hidden", padding: "0 14px 14px", display: "flex", flexDirection: "column", gap: 4 }}>
              {p.stream.map((ev) => {
                const glyph = ev.type === "system" ? (ev.phaseType === "day" ? "日" : "夜") : GLYPH[ev.type] ?? "報";
                const col = COLOR[ev.type] ?? COLOR.system;
                return (
                  <div key={ev.key} className="wh-streamitem" style={{ display: "flex", gap: 10, padding: "9px 10px", borderRadius: "var(--r-md)", background: ev.isCur ? "var(--surface-card)" : "transparent", border: `1px solid ${ev.isCur ? "var(--border-2)" : "transparent"}`, boxShadow: ev.isCur ? "var(--shadow-glow)" : "none", transition: "background var(--dur-base) var(--ease-out)" }}>
                    <span style={{ flex: "none", width: 26, height: 26, borderRadius: "var(--r-sm)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 13, fontWeight: 900, color: col.c, background: col.d }}>{glyph}</span>
                    <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 3 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <span style={{ fontSize: 11, fontWeight: 700, color: col.c }}>{t(`replay.label.${ev.type}` as const, { defaultValue: t("replay.label.system") })}</span>
                        {ev.actorSeat != null && <span className="mono" style={{ fontSize: 11, color: "var(--text-secondary)" }}>玩家{String(ev.actorSeat).padStart(2, "0")}</span>}
                        {ev.actorName && <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{ev.actorName}</span>}
                        <span style={{ flex: 1 }} />
                        <span className="mono" style={{ fontSize: 10, color: "var(--text-muted)" }}>{formatTime(ev.atMs)}</span>
                      </div>
                      <span style={{ fontSize: 13, lineHeight: 1.55 }}>{ev.text}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* wolf chat */}
          <div style={{ flex: "0 1 236px", minHeight: 150, display: "flex", flexDirection: "column", borderTop: "1px solid var(--border-1)", background: wolfOpen ? "rgba(36,12,16,0.55)" : "rgba(16,20,31,0.4)", transition: "background var(--dur-slow) var(--ease-out)" }}>
            <div style={{ flex: "none", display: "flex", alignItems: "center", gap: 9, padding: "11px 16px", borderBottom: `1px solid ${wolfOpen ? "rgba(240,74,94,0.28)" : "var(--border-1)"}` }}>
              <span style={{ width: 24, height: 24, borderRadius: "var(--r-sm)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 13, fontWeight: 900, color: "var(--wolf-500)", background: "var(--wolf-dim)" }}>狼</span>
              <span style={{ fontSize: 13, fontWeight: 700, color: wolfOpen ? "var(--wolf-400)" : "var(--text-secondary)" }}>{t("replay.wolfChannel")}</span>
              <span style={{ flex: 1 }} />
              <span style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 11, fontWeight: 700, color: wolfOpen ? "var(--wolf-500)" : "var(--text-muted)" }}>
                <span style={{ width: 6, height: 6, borderRadius: "50%", background: wolfOpen ? "var(--wolf-500)" : "var(--text-muted)", boxShadow: wolfOpen ? "0 0 10px var(--wolf-500)" : "none" }} />
                {wolfOpen ? t("replay.wolfOpen") : t("replay.wolfSilent")}
              </span>
            </div>
            <div ref={wolfRef} style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: "10px 14px", display: "flex", flexDirection: "column", gap: 7 }}>
              {p.wolfLines.length === 0 && <span style={{ margin: "auto", fontSize: 12, color: "var(--text-muted)" }}>{t("replay.wolfEmpty")}</span>}
              {p.wolfLines.map((wl) => (
                <div key={wl.key} style={{ display: "flex", gap: 9, alignItems: "flex-start", opacity: wl.isCur ? 1 : 0.66 }}>
                  <span style={{ flex: "none", marginTop: 1 }}>
                    <Avatar name={wl.name} avatar={wl.avatar} size="sm" />
                  </span>
                  <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 2 }}>
                    <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
                      {wl.seat != null && <span className="mono" style={{ fontSize: 11, fontWeight: 700, color: "var(--wolf-400)" }}>玩家{String(wl.seat).padStart(2, "0")}</span>}
                      <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{wl.name}</span>
                      <span style={{ flex: 1 }} />
                      <span className="mono" style={{ fontSize: 10, color: "var(--text-muted)" }}>{formatTime(wl.atMs)}</span>
                    </div>
                    <span style={{ fontSize: 13, lineHeight: 1.5, color: wl.isCur ? "var(--text-body)" : "var(--text-secondary)", background: wl.isCur ? "rgba(240,74,94,0.16)" : "rgba(240,74,94,0.07)", border: `1px solid ${wl.isCur ? "rgba(240,74,94,0.4)" : "rgba(240,74,94,0.18)"}`, borderRadius: "0 var(--r-md) var(--r-md) var(--r-md)", padding: "6px 10px" }}>{wl.text}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* transport */}
      <div style={{ position: "relative", zIndex: 3, flex: "none", display: "flex", alignItems: "center", gap: 18, height: 84, padding: "0 22px", borderTop: "1px solid var(--border-1)", background: "rgba(11,14,22,0.82)", backdropFilter: "blur(8px)" }}>
        <div style={{ flex: "none", display: "flex", alignItems: "center", gap: 8 }}>
          <TransportBtn title={t("replay.prevEvent")} onClick={p.stepBack}><SkipBack size={18} /></TransportBtn>
          <button type="button" onClick={p.togglePlay} style={{ width: 52, height: 52, borderRadius: "var(--r-full)", border: "1px solid transparent", background: "var(--accent)", color: "var(--text-on-accent)", cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", boxShadow: "var(--shadow-glow)" }}>
            {p.playing ? <Pause size={22} /> : <Play size={22} style={{ marginLeft: 2 }} />}
          </button>
          <TransportBtn title={t("replay.nextEvent")} onClick={p.stepFwd}><SkipForward size={18} /></TransportBtn>
        </div>

        <div style={{ flex: "none", display: "flex", alignItems: "center", gap: 3, padding: 3, borderRadius: "var(--r-md)", background: "var(--surface-card)", border: "1px solid var(--border-1)" }}>
          {[1, 2, 4].map((v) => (
            <button key={v} type="button" onClick={() => p.setSpeed(v)} className="mono" style={{ height: 30, minWidth: 38, padding: "0 8px", border: "none", borderRadius: "var(--r-sm)", fontSize: 13, fontWeight: 700, cursor: "pointer", background: p.speed === v ? "var(--accent)" : "transparent", color: p.speed === v ? "var(--text-on-accent)" : "var(--text-secondary)", transition: "all var(--dur-fast) var(--ease-out)" }}>{v}×</button>
          ))}
        </div>

        <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 7 }}>
          <div style={{ position: "relative", height: 14 }}>
            <div style={{ position: "absolute", inset: 0, display: "flex", borderRadius: "var(--r-full)", overflow: "hidden", border: "1px solid var(--border-1)" }}>
              {p.segments.map((sg) => {
                const active = p.cur?.phaseType === sg.phaseType && p.cur?.day === sg.day;
                const sc = SEG_COLOR[sg.phaseType] ?? SEG_COLOR.night;
                return (
                  <div key={sg.key} onClick={() => p.jumpSegment(sg)} title={segLabel(sg.phaseType, sg.day, t)} style={{ height: "100%", cursor: "pointer", width: `${(sg.dur / p.total) * 100}%`, background: active ? sc.active : sc.idle, borderRight: "1px solid rgba(7,10,17,0.6)", transition: "background var(--dur-base) var(--ease-out)" }} />
                );
              })}
            </div>
            <div style={{ position: "absolute", left: 0, top: 0, bottom: 0, width: `${p.playheadPct}%`, background: "linear-gradient(90deg, rgba(84,210,228,0), rgba(84,210,228,0.22))", borderRadius: "var(--r-full) 0 0 var(--r-full)", pointerEvents: "none" }} />
            <input type="range" className="wh-scrub" min={0} max={p.total} step={0.05} value={p.t} onChange={(e) => p.onScrub(parseFloat(e.target.value))} style={{ position: "absolute", inset: 0, zIndex: 2 }} />
          </div>
          <div style={{ display: "flex" }}>
            {p.segments.map((sg) => {
              const active = p.cur?.phaseType === sg.phaseType && p.cur?.day === sg.day;
              return (
                <button key={sg.key} type="button" onClick={() => p.jumpSegment(sg)} className="mono" style={{ width: `${(sg.dur / p.total) * 100}%`, background: "none", border: "none", cursor: "pointer", fontSize: 10, fontWeight: 700, color: active ? "var(--text-body)" : "var(--text-muted)", textAlign: "center", padding: 0, whiteSpace: "nowrap", overflow: "hidden" }}>
                  {segLabel(sg.phaseType, sg.day, t)}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* vote overlay */}
      <AnimatePresence>
        {p.vote && <VoteOverlay vote={p.vote} text={p.cur?.text ?? ""} nameOf={(s) => replay.players.find((pl) => pl.seat === s)?.name ?? null} />}
      </AnimatePresence>

      {/* results */}
      <AnimatePresence>
        {p.showResults && (
          <ResultsScreen
            winner={p.winner}
            seats={p.seats}
            causeOf={causeOf}
            onRestart={p.restart}
          />
        )}
      </AnimatePresence>
    </div>
  );
}

function segLabel(phaseType: string, day: number, t: TFunction) {
  if (phaseType === "day") return t("replay.segDay", { day });
  if (phaseType === "end") return t("replay.segEnd");
  return t("replay.segNight", { day });
}

function TransportBtn({ title, onClick, children }: { title: string; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" title={title} onClick={onClick} style={{ width: 40, height: 40, borderRadius: "var(--r-md)", border: "1px solid var(--border-2)", background: "var(--surface-card)", color: "var(--text-secondary)", cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center" }}>
      {children}
    </button>
  );
}

function SpoilerToggle({ on, onToggle, label }: { on: boolean; onToggle: () => void; label: string }) {
  return (
    <button type="button" onClick={onToggle} style={{ display: "inline-flex", alignItems: "center", gap: 9, height: 30, padding: "0 6px 0 12px", borderRadius: "var(--r-full)", border: "1px solid var(--border-1)", background: "var(--surface-card)", color: "var(--text-secondary)", fontSize: 12, fontWeight: 700, cursor: "pointer" }}>
      {label}
      <span style={{ width: 34, height: 18, borderRadius: 999, background: on ? "var(--accent)" : "var(--surface-raised)", border: "1px solid var(--border-2)", position: "relative", transition: "background var(--dur-fast) var(--ease-out)" }}>
        <span style={{ position: "absolute", top: 1, left: on ? 17 : 1, width: 14, height: 14, borderRadius: "50%", background: "#fff", transition: "left var(--dur-fast) var(--ease-out)" }} />
      </span>
    </button>
  );
}

function VoteOverlay({ vote, text, nameOf }: { vote: NonNullable<ReturnType<typeof useReplayPlayer>["vote"]>; text: string; nameOf: (seat: number) => string | null }) {
  const { t } = useTranslation();
  const police = vote.kind === "police";
  const decided = police ? vote.win : vote.out;
  const maxC = Math.max(1, ...vote.rows.map((r) => r.count));
  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} style={{ position: "absolute", inset: 0, zIndex: 20, display: "flex", alignItems: "center", justifyContent: "center", background: "var(--surface-overlay)", backdropFilter: "blur(8px)" }}>
      <motion.div initial={{ y: 10, opacity: 0, scale: 0.98 }} animate={{ y: 0, opacity: 1, scale: 1 }} exit={{ y: 10, opacity: 0 }} transition={{ duration: 0.22, ease: "easeOut" }} style={{ width: 560, maxWidth: "90vw", maxHeight: "86vh", overflow: "auto", background: "var(--surface-card)", border: "1px solid var(--border-2)", borderRadius: "var(--r-xl)", boxShadow: "var(--shadow-3)", padding: "26px 28px", display: "flex", flexDirection: "column", gap: 18 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <span style={{ width: 40, height: 40, borderRadius: "var(--r-md)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18, fontWeight: 900, color: police ? "var(--badge-gold)" : "var(--moon-400)", background: police ? "var(--badge-gold-dim)" : "var(--accent-soft)" }}>{police ? "警" : "票"}</span>
          <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 1 }}>
            <span style={{ fontSize: 18, fontWeight: 900 }}>{police ? t("replay.voteTitlePolice") : t("replay.voteTitleExile")}</span>
            <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{vote.note ?? (police ? t("replay.voteNotePolice") : t("replay.voteNoteExile"))}</span>
          </div>
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          {vote.rows.map((r) => {
            const isOut = r.seat === decided;
            const barColor = isOut && !police ? "var(--danger-500)" : police ? "var(--badge-gold)" : "var(--moon-500)";
            return (
              <div key={r.seat} style={{ display: "flex", flexDirection: "column", gap: 7, padding: "12px 14px", borderRadius: "var(--r-lg)", border: `1px solid ${isOut ? (police ? "rgba(242,201,76,0.4)" : "rgba(255,92,110,0.4)") : "var(--border-1)"}`, background: isOut ? (police ? "var(--badge-gold-dim)" : "var(--danger-dim)") : "var(--surface-app)" }}>
                <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                  <span className="mono" style={{ fontSize: 14, fontWeight: 700 }}>玩家{String(r.seat).padStart(2, "0")}</span>
                  <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{nameOf(r.seat)}</span>
                  {isOut && <span style={{ fontSize: 11, fontWeight: 700, color: police ? "var(--badge-gold)" : "var(--danger-500)", background: police ? "var(--badge-gold-dim)" : "var(--danger-dim)", border: "1px solid var(--border-1)", borderRadius: "var(--r-sm)", padding: "1px 8px" }}>{police ? t("replay.voteElected") : t("replay.voteExiled")}</span>}
                  <span style={{ flex: 1 }} />
                  <span className="mono" style={{ fontSize: 18, fontWeight: 700, color: isOut ? (police ? "var(--badge-gold)" : "var(--danger-500)") : "var(--text-body)" }}>{r.count}</span>
                  <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{t("replay.votes")}</span>
                </div>
                <div style={{ height: 6, borderRadius: "var(--r-full)", background: "var(--surface-app)", overflow: "hidden" }}>
                  <motion.div initial={{ width: 0 }} animate={{ width: `${(r.count / maxC) * 100}%` }} transition={{ duration: 0.4, ease: "easeOut" }} style={{ height: "100%", borderRadius: "var(--r-full)", background: barColor }} />
                </div>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 5 }}>
                  {r.voters.map((v) => (
                    <span key={v} className="mono" style={{ fontSize: 11, color: "var(--text-secondary)", background: "var(--surface-app)", border: "1px solid var(--border-1)", borderRadius: "var(--r-sm)", padding: "2px 7px" }}>玩家{String(v).padStart(2, "0")}</span>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
        <div style={{ fontSize: 13, lineHeight: 1.6, color: "var(--text-secondary)", paddingTop: 4, borderTop: "1px solid var(--border-1)" }}>{text}</div>
      </motion.div>
    </motion.div>
  );
}

function ResultsScreen({ winner, seats, causeOf, onRestart }: { winner: string | null; seats: ReturnType<typeof useReplayPlayer>["seats"]; causeOf: Map<number, string>; onRestart: () => void }) {
  const { t } = useTranslation();
  const good = winner !== "WOLF";
  const survivors = seats.filter((s) => !s.dead);
  const casualties = seats.filter((s) => s.dead);
  const accent = good ? "var(--vill-500)" : "var(--wolf-500)";
  const accentDim = good ? "var(--vill-dim)" : "var(--wolf-dim)";
  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} style={{ position: "absolute", inset: 0, zIndex: 25, display: "flex", alignItems: "center", justifyContent: "center", background: "var(--surface-overlay)", backdropFilter: "blur(10px)" }}>
      <motion.div initial={{ y: 12, opacity: 0, scale: 0.98 }} animate={{ y: 0, opacity: 1, scale: 1 }} exit={{ y: 12, opacity: 0 }} transition={{ duration: 0.3, ease: "easeOut" }} style={{ width: 720, maxWidth: "92vw", maxHeight: "90vh", overflow: "auto", background: "var(--surface-card)", border: `1px solid ${good ? "rgba(54,201,125,0.42)" : "rgba(240,74,94,0.42)"}`, borderRadius: "var(--r-xl)", boxShadow: "var(--shadow-3)", padding: "34px 36px", display: "flex", flexDirection: "column", gap: 24 }}>
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 10, textAlign: "center" }}>
          <span style={{ width: 64, height: 64, borderRadius: "var(--r-full)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 30, fontWeight: 900, color: accent, background: accentDim, boxShadow: `0 0 0 1px ${good ? "rgba(54,201,125,0.42)" : "rgba(240,74,94,0.42)"}, 0 0 30px ${good ? "rgba(54,201,125,0.2)" : "rgba(240,74,94,0.2)"}` }}>{good ? "神" : "狼"}</span>
          <span style={{ fontSize: 13, fontWeight: 700, letterSpacing: "0.2em", color: "var(--text-muted)" }}>{t("replay.gameOver")}</span>
          <span style={{ fontSize: 30, fontWeight: 900, color: accent }}>{good ? t("replay.winnerGood") : t("replay.winnerWolf")}</span>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20 }}>
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: "var(--success-500)", letterSpacing: "0.08em" }}>{t("replay.survivors", { count: survivors.length })}</span>
            {survivors.map((s) => (
              <div key={s.seat} style={{ display: "flex", alignItems: "center", gap: 10, padding: "9px 12px", borderRadius: "var(--r-md)", background: "var(--surface-app)", border: "1px solid var(--border-1)" }}>
                <span className="mono" style={{ fontSize: 13, fontWeight: 700 }}>玩家{String(s.seat).padStart(2, "0")}</span>
                <span style={{ fontSize: 12, color: "var(--text-muted)", flex: 1 }}>{s.name}</span>
                <FactionBadge faction={s.faction} roleId={s.roleId} name={s.roleName} size="sm" />
              </div>
            ))}
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: "var(--danger-500)", letterSpacing: "0.08em" }}>{t("replay.casualties", { count: casualties.length })}</span>
            {casualties.map((s) => (
              <div key={s.seat} style={{ display: "flex", alignItems: "center", gap: 10, padding: "9px 12px", borderRadius: "var(--r-md)", background: "var(--surface-app)", border: "1px solid var(--border-1)", opacity: 0.82 }}>
                <span className="mono" style={{ fontSize: 13, fontWeight: 700, color: "var(--text-secondary)", textDecoration: "line-through" }}>玩家{String(s.seat).padStart(2, "0")}</span>
                <span style={{ fontSize: 12, color: "var(--text-muted)", flex: 1 }}>{causeOf.get(s.seat) ?? t("replay.causeOut")}</span>
                <FactionBadge faction={s.faction} roleId={s.roleId} name={s.roleName} dead size="sm" />
              </div>
            ))}
          </div>
        </div>
        <div style={{ display: "flex", justifyContent: "center", gap: 12, paddingTop: 6 }}>
          <Button size="lg" onClick={onRestart}>{t("replay.restart")}</Button>
        </div>
      </motion.div>
    </motion.div>
  );
}
