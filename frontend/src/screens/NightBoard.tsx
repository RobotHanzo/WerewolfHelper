import { useTranslation } from "react-i18next";
import { motion } from "framer-motion";
import { Moon, Check, ArrowRight, Skull, Sparkles } from "lucide-react";
import type { Night, NightAction, NightVote } from "@/types/snapshot";
import { Avatar } from "@/components/ui/Avatar";
import { Countdown } from "@/components/ui/Countdown";
import { ProgressBar } from "@/components/ui/ProgressBar";
import { FactionBadge, WolfSvg } from "@/components/ui/Badge";

const seatLabel = (n: number) => `玩家${String(n).padStart(2, "0")}`;
/** The collective wolf knife — emitted by SnapshotService with this synthetic roleId. */
const isWolfKill = (a: NightAction) => a.roleId === "wolf";
/** Only abilities that actually have an actor in play are worth showing. */
const isLive = (a: NightAction) => a.actorSeats.length > 0;
/** "Done" = the actor(s) have acted (submitted or deliberately skipped), not still pending. */
const isDone = (a: NightAction) => a.status !== "acting";

function StatusChip({ status }: { status: NightAction["status"] }) {
  const { t } = useTranslation();
  const map = {
    acting: { color: "var(--warning-500)", bg: "rgba(245,176,65,0.12)", text: t("night.waiting") },
    submitted: { color: "var(--success-500)", bg: "var(--success-dim)", text: t("night.submitted") },
    skipped: { color: "var(--text-muted)", bg: "var(--surface-raised)", text: t("night.skip") },
  } as const;
  const s = map[status];
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 11, fontWeight: 700, color: s.color, background: s.bg, borderRadius: "var(--r-full)", padding: "2px 9px" }}>
      {status === "submitted" && <Check size={11} />}
      {s.text}
    </span>
  );
}

/** A seat token: small avatar over its padded label. */
function SeatToken({ seat, tone }: { seat: number; tone?: "target" }) {
  return (
    <span style={{ display: "inline-flex", flexDirection: "column", alignItems: "center", gap: 3 }}>
      <Avatar size="sm" name={`#${seat}`} />
      <span className="mono" style={{ fontSize: 10, fontWeight: tone === "target" ? 700 : 400, color: tone === "target" ? "var(--text-body)" : "var(--text-muted)" }}>
        {seatLabel(seat)}
      </span>
    </span>
  );
}

/** A standard single-role night action: actor(s) → target, with status. */
function ActionCard({ action }: { action: NightAction }) {
  const { t } = useTranslation();
  return (
    <motion.div layout className="wh-card" style={{ padding: 12, display: "flex", flexDirection: "column", gap: 10 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, justifyContent: "space-between" }}>
        <FactionBadge faction={action.faction} roleId={action.roleId} name={action.roleName} size="sm" />
        <StatusChip status={action.status} />
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <SeatToken seat={action.actorSeats[0] ?? 0} />
        <ArrowRight size={16} style={{ color: "var(--text-disabled)", flexShrink: 0 }} />
        <span style={{ flex: 1 }}>
          {action.targetSeat != null ? (
            <SeatToken seat={action.targetSeat} tone="target" />
          ) : action.status === "acting" ? (
            <span style={{ color: "var(--warning-500)", fontStyle: "italic", fontSize: 12 }}>{t("night.deliberating")}</span>
          ) : (
            <span style={{ color: "var(--text-muted)", fontStyle: "italic", fontSize: 12 }}>{t("night.noTarget")}</span>
          )}
        </span>
      </div>
    </motion.div>
  );
}

/** One wolf's row in the vote roster: voter → their knife choice (target / 不刀 / pending). */
function WolfVoteRow({ vote }: { vote: NightVote }) {
  const { t } = useTranslation();
  const pending = vote.target == null && !vote.skip;
  const dot = pending ? "var(--warning-500)" : vote.skip ? "var(--text-muted)" : "var(--success-500)";
  return (
    <span style={{ display: "flex", alignItems: "center", gap: 8, padding: "5px 8px", borderRadius: "var(--r-sm)", background: "var(--surface-card)", border: "1px solid var(--border-1)", opacity: pending ? 0.7 : 1 }}>
      <span style={{ width: 6, height: 6, borderRadius: "var(--r-full)", background: dot, flexShrink: 0 }} />
      <span className="mono" style={{ fontSize: 11, fontWeight: 700 }}>{seatLabel(vote.voter)}</span>
      <ArrowRight size={12} style={{ color: "var(--text-disabled)", flexShrink: 0 }} />
      <span className="mono" style={{ fontSize: 11, fontWeight: vote.target != null ? 700 : 400, color: vote.target != null ? "var(--wolf-400)" : "var(--text-muted)", fontStyle: vote.target != null ? "normal" : "italic" }}>
        {vote.target != null ? seatLabel(vote.target) : vote.skip ? t("night.noKill") : t("night.waiting")}
      </span>
    </span>
  );
}

/** The collective wolf kill, elevated: the running knife consensus plus the per-wolf vote roster. */
function WolfConsensusCard({ action }: { action: NightAction }) {
  const { t } = useTranslation();
  const locked = action.status === "submitted";
  const votes = action.votes ?? [];
  const cast = votes.filter((v) => v.target != null || v.skip).length;
  return (
    <motion.div
      layout
      className="wh-card"
      style={{ gridColumn: "1 / -1", padding: 14, display: "flex", flexDirection: "column", gap: 14, border: "1px solid var(--wolf-500)", background: "linear-gradient(110deg, var(--wolf-dim), transparent 70%)" }}
    >
      {/* headline: title + consensus */}
      <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 16 }}>
        <span style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          <span style={{ display: "inline-flex", alignItems: "center", gap: 7, fontSize: 13, fontWeight: 900, color: "var(--wolf-400)" }}>
            <WolfSvg size={16} />
            {t("night.wolfKill")}
          </span>
          <span style={{ display: "inline-flex", alignItems: "center", gap: 4, fontSize: 11, fontWeight: 700, color: locked ? "var(--success-500)" : "var(--warning-500)" }}>
            {locked ? <Check size={11} /> : null}
            {locked ? t("night.locked") : t("night.deliberating")}
            {votes.length > 0 && <span style={{ color: "var(--text-muted)", fontWeight: 400 }}>· {t("night.voted", { voted: cast, total: votes.length })}</span>}
          </span>
        </span>

        <span style={{ display: "flex", flexDirection: "column", gap: 4, marginLeft: "auto", alignItems: "flex-end" }}>
          <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: "0.06em", color: "var(--text-muted)" }}>{t("night.wolfConsensus")}</span>
          {action.targetSeat != null ? (
            <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
              <Avatar size="md" name={`#${action.targetSeat}`} />
              <span className="mono" style={{ fontSize: 15, fontWeight: 900, color: "var(--wolf-400)" }}>{seatLabel(action.targetSeat)}</span>
            </span>
          ) : (
            <span style={{ fontSize: 13, fontWeight: 700, color: locked ? "var(--text-muted)" : "var(--warning-500)", fontStyle: "italic" }}>
              {locked ? t("night.noKill") : t("night.deliberating")}
            </span>
          )}
        </span>
      </div>

      {/* per-wolf vote roster */}
      {votes.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: "0.06em", color: "var(--text-muted)" }}>{t("night.wolfVote")}</span>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(170px, 1fr))", gap: 6 }}>
            {votes.map((v) => <WolfVoteRow key={v.voter} vote={v} />)}
          </div>
        </div>
      )}
    </motion.div>
  );
}

/** One topological wave from NightPlanner, surfaced as a numbered, ordered phase. */
function PhaseSection({ displayNumber, actions, isLast }: { displayNumber: number; actions: NightAction[]; isLast: boolean }) {
  const { t } = useTranslation();
  const done = actions.filter(isDone).length;
  const complete = done === actions.length;
  const wolf = actions.find(isWolfKill);
  const rest = actions.filter((a) => !isWolfKill(a));

  return (
    <div style={{ display: "flex", gap: 12 }}>
      {/* timeline rail */}
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", flexShrink: 0 }}>
        <span
          style={{
            width: 28, height: 28, borderRadius: "var(--r-full)", display: "flex", alignItems: "center", justifyContent: "center",
            fontSize: 12, fontWeight: 900,
            color: complete ? "var(--text-on-accent)" : "var(--moon-300)",
            background: complete ? "var(--success-500)" : "var(--accent-soft)",
            border: `1px solid ${complete ? "var(--success-500)" : "var(--moon-500)"}`,
            boxShadow: complete ? "none" : "0 0 8px var(--moon-500)",
          }}
        >
          {complete ? <Check size={15} /> : displayNumber}
        </span>
        {!isLast && <span style={{ flex: 1, width: 2, marginTop: 4, background: "var(--border-1)" }} />}
      </div>

      {/* phase body */}
      <div style={{ flex: 1, paddingBottom: isLast ? 0 : 16, minWidth: 0 }}>
        <header style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10, flexWrap: "wrap" }}>
          <span style={{ fontSize: 13, fontWeight: 800 }}>{t("night.phase", { n: displayNumber })}</span>
          <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{t("night.simultaneous")}</span>
          <span className="mono" style={{ marginLeft: "auto", fontSize: 11, fontWeight: 700, color: complete ? "var(--success-500)" : "var(--text-secondary)" }}>
            {complete ? t("night.phaseDone") : t("night.phaseProgress", { done, total: actions.length })}
          </span>
        </header>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(232px, 1fr))", gap: 12 }}>
          {wolf && <WolfConsensusCard action={wolf} />}
          {rest.map((a) => <ActionCard key={a.abilityId} action={a} />)}
        </div>
      </div>
    </div>
  );
}

/**
 * The automated night engine surfaced for the judge: the NightPlanner's topological waves are shown
 * as ordered, numbered phases (independent abilities inside a phase run simultaneously). The
 * collective wolf knife is elevated into its own consensus panel. Judge-only — no spectator surface
 * requests this, and it leaks no information the roster doesn't already show.
 */
export function NightBoard({ night }: { night: Night }) {
  const { t } = useTranslation();

  // Drop abilities with no actor in play, then drop emptied waves and renumber the survivors so the
  // phase labels stay contiguous (NightPlanner wave indices can have gaps).
  const phases = night.waves
    .map((w) => w.actions.filter(isLive))
    .filter((actions) => actions.length > 0);

  return (
    <motion.div
      layout
      className="wh-card"
      style={{ marginBottom: 16, border: "1px solid var(--moon-500)", boxShadow: "0 0 24px rgba(84,210,228,0.14)", overflow: "hidden" }}
    >
      <header style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 18px", borderBottom: "1px solid var(--border-1)", flexWrap: "wrap" }}>
        <span style={{ width: 38, height: 38, borderRadius: "var(--r-md)", display: "flex", alignItems: "center", justifyContent: "center", background: "var(--accent-soft)", border: "1px solid var(--moon-500)", color: "var(--moon-300)" }}>
          <Moon size={20} />
        </span>
        <span style={{ display: "flex", flexDirection: "column" }}>
          <span style={{ fontSize: 15, fontWeight: 900 }}>{t("night.title", { day: night.day })}</span>
          <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{t("night.subtitle")}</span>
        </span>
        <span style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 18, flexWrap: "wrap" }}>
          <span style={{ display: "flex", flexDirection: "column", gap: 4, minWidth: 150 }}>
            <span className="mono" style={{ fontSize: 11, color: "var(--text-secondary)", display: "flex", justifyContent: "space-between" }}>
              <span>{t("night.overall")}</span>
              <span>{night.submittedCount} / {night.totalCount}</span>
            </span>
            <ProgressBar percent={night.totalCount ? (night.submittedCount / night.totalCount) * 100 : 0} state={night.submittedCount >= night.totalCount ? "success" : "running"} />
          </span>
          {night.endsAt && <Countdown endsAt={night.endsAt} size="sm" label={t("night.remaining")} />}
        </span>
      </header>

      <div style={{ padding: "16px 18px" }}>
        {phases.map((actions, i) => (
          <PhaseSection key={i} displayNumber={i + 1} actions={actions} isLast={i === phases.length - 1} />
        ))}
      </div>

      {night.resolved && night.summary && (
        <footer style={{ display: "flex", alignItems: "center", gap: 10, padding: "12px 18px", borderTop: "1px solid var(--border-1)", background: "var(--surface-raised)" }}>
          {night.summary === t("night.peaceful") ? (
            <Sparkles size={16} style={{ color: "var(--success-500)" }} />
          ) : (
            <Skull size={16} style={{ color: "var(--wolf-400)" }} />
          )}
          <span style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.06em", color: "var(--text-muted)" }}>{t("night.resultTitle")}</span>
          <span style={{ fontSize: 13, fontWeight: 800 }}>{night.summary}</span>
        </footer>
      )}
    </motion.div>
  );
}
