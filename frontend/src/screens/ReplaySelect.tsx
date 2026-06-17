import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { motion } from "framer-motion";
import type { TFunction } from "i18next";
import { History, Play, Trophy, Users } from "lucide-react";
import { api, ApiError } from "@/api/client";
import { useAuthStore } from "@/stores/authStore";
import { Button } from "@/components/ui/Button";
import { buildReplaySummaries } from "@/mock/replayScenario";
import type { ReplaySummary } from "@/types/replay";

type Sort = "newest" | "oldest";
type WinnerFilter = "all" | "WOLF" | "GOOD";

const wrap: React.CSSProperties = {
  minHeight: "100vh",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  padding: "40px 24px",
  boxSizing: "border-box",
};

function chip(active: boolean): React.CSSProperties {
  return {
    height: 30,
    padding: "0 13px",
    borderRadius: "var(--r-full)",
    fontSize: 12,
    fontWeight: 700,
    cursor: "pointer",
    fontFamily: "var(--font-ui)",
    border: `1px solid ${active ? "rgba(84,210,228,0.4)" : "var(--border-1)"}`,
    background: active ? "var(--accent-soft)" : "var(--surface-card)",
    color: active ? "var(--moon-300)" : "var(--text-muted)",
    transition: "all var(--dur-fast) var(--ease-out)",
  };
}

function winnerBadge(faction: string | null, t: TFunction) {
  if (faction === "WOLF") return { label: t("replay.winnerWolf"), color: "var(--wolf-500)", dim: "var(--wolf-dim)" };
  if (faction === "GOOD") return { label: t("replay.winnerGood"), color: "var(--vill-500)", dim: "var(--vill-dim)" };
  return { label: t("replay.winnerUnknown"), color: "var(--text-muted)", dim: "var(--surface-raised)" };
}

export function ReplaySelect() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const demo = useAuthStore((s) => s.demo);
  const [items, setItems] = useState<ReplaySummary[]>([]);
  const [state, setState] = useState<"loading" | "error" | "ok">("loading");

  const [sort, setSort] = useState<Sort>("newest");
  const [winner, setWinner] = useState<WinnerFilter>("all");
  const [guild, setGuild] = useState<string>("all");
  const [doubleOnly, setDoubleOnly] = useState<"all" | "double" | "standard">("all");

  useEffect(() => {
    if (demo) {
      setItems(buildReplaySummaries());
      setState("ok");
      return;
    }
    api.listReplays()
      .then((l) => {
        setItems(l);
        setState("ok");
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) navigate("/login", { replace: true });
        else setState("error");
      });
  }, [demo, navigate]);

  const guilds = useMemo(() => {
    const seen = new Map<string, string>();
    items.forEach((r) => seen.set(r.guildId, r.guildName ?? r.guildId));
    return Array.from(seen.entries());
  }, [items]);

  const filtered = useMemo(() => {
    let list = items.slice();
    if (winner !== "all") list = list.filter((r) => r.winnerFaction === winner);
    if (guild !== "all") list = list.filter((r) => r.guildId === guild);
    if (doubleOnly !== "all") list = list.filter((r) => r.doubleIdentity === (doubleOnly === "double"));
    list.sort((a, b) => (sort === "newest" ? b.endedAt - a.endedAt : a.endedAt - b.endedAt));
    return list;
  }, [items, winner, guild, doubleOnly, sort]);

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} style={wrap}>
      <div style={{ width: "100%", maxWidth: 720, display: "flex", flexDirection: "column", gap: 16 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <span style={{ width: 40, height: 40, borderRadius: "var(--r-md)", display: "flex", alignItems: "center", justifyContent: "center", background: "var(--accent-soft)", color: "var(--moon-300)" }}>
            <History size={20} />
          </span>
          <div style={{ flex: 1 }}>
            <h1 style={{ margin: 0, fontSize: 22, fontWeight: 900 }}>{t("replay.selectTitle")}</h1>
            <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{t("replay.selectHint")}</span>
          </div>
          <Button variant="ghost" size="sm" onClick={() => navigate("/servers")}>{t("replay.back")}</Button>
        </div>

        {/* filters + sort */}
        {state === "ok" && items.length > 0 && (
          <div style={{ display: "flex", flexWrap: "wrap", gap: 6, alignItems: "center" }}>
            <button type="button" style={chip(sort === "newest")} onClick={() => setSort("newest")}>{t("replay.sortNewest")}</button>
            <button type="button" style={chip(sort === "oldest")} onClick={() => setSort("oldest")}>{t("replay.sortOldest")}</button>
            <span style={{ width: 1, height: 18, background: "var(--border-1)", margin: "0 4px" }} />
            <button type="button" style={chip(winner === "all")} onClick={() => setWinner("all")}>{t("replay.filterAll")}</button>
            <button type="button" style={chip(winner === "WOLF")} onClick={() => setWinner("WOLF")}>{t("replay.winnerWolf")}</button>
            <button type="button" style={chip(winner === "GOOD")} onClick={() => setWinner("GOOD")}>{t("replay.winnerGood")}</button>
            <span style={{ width: 1, height: 18, background: "var(--border-1)", margin: "0 4px" }} />
            <button type="button" style={chip(doubleOnly === "all")} onClick={() => setDoubleOnly("all")}>{t("replay.filterAll")}</button>
            <button type="button" style={chip(doubleOnly === "double")} onClick={() => setDoubleOnly("double")}>{t("replay.doubleOn")}</button>
            <button type="button" style={chip(doubleOnly === "standard")} onClick={() => setDoubleOnly("standard")}>{t("replay.doubleOff")}</button>
            {guilds.length > 1 && (
              <>
                <span style={{ width: 1, height: 18, background: "var(--border-1)", margin: "0 4px" }} />
                <button type="button" style={chip(guild === "all")} onClick={() => setGuild("all")}>{t("replay.filterAll")}</button>
                {guilds.map(([id, name]) => (
                  <button key={id} type="button" style={chip(guild === id)} onClick={() => setGuild(id)}>{name}</button>
                ))}
              </>
            )}
          </div>
        )}

        {state === "loading" && <span style={{ fontSize: 12, color: "var(--text-muted)", textAlign: "center" }}>{t("replay.loadingList")}</span>}
        {state === "error" && (
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12, padding: 28, borderRadius: "var(--r-lg)", background: "var(--danger-dim)", border: "1px solid rgba(255,92,110,0.35)" }}>
            <span style={{ fontWeight: 700, color: "var(--danger-500)" }}>⚠ {t("replay.errorTitle")}</span>
            <span style={{ fontSize: 12, color: "var(--text-secondary)" }}>{t("replay.errorHint")}</span>
          </div>
        )}
        {state === "ok" && items.length === 0 && (
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 10, padding: 36, borderRadius: "var(--r-lg)", background: "var(--surface-card)", border: "1px dashed var(--border-2)" }}>
            <span style={{ fontWeight: 700 }}>{t("replay.emptyTitle")}</span>
            <span style={{ fontSize: 12, color: "var(--text-muted)", textAlign: "center", lineHeight: 1.7 }}>{t("replay.emptyHint")}</span>
          </div>
        )}

        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))", gap: 12 }}>
          {filtered.map((r) => {
            const wb = winnerBadge(r.winnerFaction, t);
            const date = new Date(r.endedAt);
            return (
              <motion.button
                key={r.id}
                type="button"
                onClick={() => navigate(`/replays/${r.id}`)}
                className="wh-card"
                style={{ display: "flex", flexDirection: "column", gap: 12, padding: 16, cursor: "pointer", textAlign: "left", fontFamily: "var(--font-ui)" }}
                whileHover={{ scale: 1.015, boxShadow: "var(--shadow-2)", borderColor: "var(--border-2)" }}
                whileTap={{ scale: 0.99 }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 11 }}>
                  <span style={{ width: 40, height: 40, flex: "none", borderRadius: "var(--r-md)", background: "var(--surface-raised)", border: "1px solid var(--border-2)", display: "flex", alignItems: "center", justifyContent: "center", overflow: "hidden", fontWeight: 700, color: "var(--text-secondary)" }}>
                    {r.guildIcon ? <img src={r.guildIcon} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} /> : (r.guildName ?? "?").trim()[0] ?? "?"}
                  </span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <span style={{ display: "block", fontSize: 14, fontWeight: 800, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>{r.title}</span>
                    <span style={{ display: "block", fontSize: 12, color: "var(--text-muted)" }}>{r.guildName}</span>
                  </div>
                  <span style={{ fontSize: 11, fontWeight: 700, color: wb.color, background: wb.dim, border: "1px solid var(--border-1)", borderRadius: "var(--r-sm)", padding: "3px 9px", whiteSpace: "nowrap", display: "inline-flex", alignItems: "center", gap: 4 }}>
                    <Trophy size={11} /> {wb.label}
                  </span>
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 14, fontSize: 12, color: "var(--text-muted)" }}>
                  <span className="mono">{date.toLocaleDateString()} {String(date.getHours()).padStart(2, "0")}:{String(date.getMinutes()).padStart(2, "0")}</span>
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 4 }}><Users size={12} /> {t("replay.players", { count: r.playerCount })}</span>
                  <span className="mono">{t("replay.duration", { mins: Math.max(1, Math.round(r.durationMs / 60000)) })}</span>
                  {r.doubleIdentity && <span style={{ color: "var(--gbaby-amber)" }}>{t("replay.doubleOn")}</span>}
                  <span style={{ flex: 1 }} />
                  <span style={{ display: "inline-flex", alignItems: "center", gap: 5, color: "var(--moon-300)", fontWeight: 700 }}><Play size={12} /> {t("replay.open")}</span>
                </div>
              </motion.button>
            );
          })}
        </div>
      </div>
    </motion.div>
  );
}
