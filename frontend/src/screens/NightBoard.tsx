import { useTranslation } from "react-i18next";
import { motion } from "framer-motion";
import type { Night, NightAction } from "@/types/snapshot";
import { Avatar } from "@/components/ui/Avatar";
import { Countdown } from "@/components/ui/Countdown";

const FACTION_COLOR: Record<string, string> = { WOLF: "var(--wolf-400)", GOD: "var(--god-400)", VILLAGER: "var(--vill-400)", GBABY: "var(--gbaby-amber)" };
const GLYPH: Record<string, string> = { WOLF: "狼", GOD: "神", VILLAGER: "民", GBABY: "寶" };

function StatusChip({ status }: { status: NightAction["status"] }) {
  const { t } = useTranslation();
  const map = {
    acting: { color: "var(--warning-500)", text: t("night.waiting") },
    submitted: { color: "var(--success-500)", text: t("night.submitted") },
    skipped: { color: "var(--text-muted)", text: t("night.noTarget") },
  } as const;
  const s = map[status];
  return <span style={{ fontSize: 11, fontWeight: 700, color: s.color }}>{s.text}</span>;
}

function ActionCard({ action }: { action: NightAction }) {
  const isWolf = action.actorSeats.length > 1;
  return (
    <div className="wh-card" style={{ padding: 12, display: "flex", flexDirection: "column", gap: 8 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, justifyContent: "space-between" }}>
        <span style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 12, fontWeight: 700, color: FACTION_COLOR[action.faction] }}>
          <span style={{ fontWeight: 900 }}>{GLYPH[action.faction]}</span>
          {action.roleName}
          {isWolf && <span style={{ color: "var(--text-muted)", fontWeight: 400 }}>×{action.actorSeats.length}</span>}
        </span>
        <StatusChip status={action.status} />
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <span style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 3 }}>
          <Avatar size="sm" name={`#${action.actorSeats[0] ?? "?"}`} />
          <span className="mono" style={{ fontSize: 10, color: "var(--text-muted)" }}>
            玩家{String(action.actorSeats[0] ?? 0).padStart(2, "0")}
          </span>
        </span>
        <span style={{ color: "var(--text-disabled)" }}>→</span>
        <span style={{ flex: 1 }}>
          {action.targetSeat != null ? (
            <span className="mono" style={{ fontWeight: 700 }}>玩家{String(action.targetSeat).padStart(2, "0")}</span>
          ) : action.status === "acting" ? (
            <span style={{ color: "var(--warning-500)", fontStyle: "italic" }}>商議中…</span>
          ) : (
            <span style={{ color: "var(--text-muted)", fontStyle: "italic" }}>無目標</span>
          )}
        </span>
      </div>
    </div>
  );
}

/**
 * The automated night engine surfaced for the judge: the wave plan with each ability's
 * pending/submitted state, running simultaneously. (Not in the static prototype — built in the
 * same design language; it leaks no secrets to spectator surfaces.)
 */
export function NightBoard({ night }: { night: Night }) {
  const { t } = useTranslation();
  return (
    <motion.div
      layout
      className="wh-card"
      style={{ marginBottom: 16, border: "1px solid var(--moon-500)", boxShadow: "0 0 24px rgba(84,210,228,0.14)", overflow: "hidden" }}
    >
      <header style={{ display: "flex", alignItems: "center", gap: 12, padding: "14px 18px", borderBottom: "1px solid var(--border-1)", flexWrap: "wrap" }}>
        <span style={{ display: "flex", flexDirection: "column" }}>
          <span style={{ fontSize: 15, fontWeight: 900 }}>{t("night.title", { day: night.day })}</span>
          <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{t("night.subtitle")}</span>
        </span>
        <span style={{ display: "inline-flex", alignItems: "center", gap: 6, padding: "3px 10px", borderRadius: "var(--r-full)", background: "var(--accent-soft)", border: "1px solid var(--moon-500)" }}>
          <span style={{ width: 6, height: 6, borderRadius: "var(--r-full)", background: "var(--moon-400)", boxShadow: "0 0 8px var(--moon-400)" }} />
          <span style={{ fontSize: 11, fontWeight: 700, color: "var(--moon-300)" }}>{t("night.simultaneous")}</span>
        </span>
        <span style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 18 }}>
          <span className="mono" style={{ fontSize: 12, color: "var(--text-secondary)" }}>
            {t("night.submitted")} {night.submittedCount} / {night.totalCount}
          </span>
          {night.endsAt && <Countdown endsAt={night.endsAt} size="sm" label={t("night.remaining")} />}
        </span>
      </header>
      <div style={{ padding: "16px 18px", display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(256px, 1fr))", gap: 12 }}>
        {night.waves.flatMap((w) => w.actions).filter((a) => a.status !== "skipped" || a.targetSeat != null).map((a) => (
          <ActionCard key={a.abilityId} action={a} />
        ))}
      </div>
    </motion.div>
  );
}
