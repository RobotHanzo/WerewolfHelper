import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "framer-motion";
import { Megaphone, Vote, ArrowUp, ArrowDown } from "lucide-react";
import type { Seat, Speech } from "@/types/snapshot";
import { useGameStore } from "@/stores/gameStore";
import { useGuild } from "@/hooks/useGuild";
import { useGameActions } from "@/hooks/useGameActions";
import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import { Countdown } from "@/components/ui/Countdown";
import { PollResults } from "./SpeechManager";

const seatLabel = (n: number) => `玩家${String(n).padStart(2, "0")}`;

/** Per-stage accent: police election borrows the gold reserved for the badge; everything else is moon. */
type Accent = { border: string; glow: string; soft: string; fg: string };
const MOON: Accent = { border: "var(--moon-500)", glow: "rgba(84,210,228,0.14)", soft: "var(--accent-soft)", fg: "var(--moon-300)" };
const GOLD: Accent = { border: "var(--badge-gold)", glow: "rgba(242,201,76,0.14)", soft: "var(--badge-gold-dim)", fg: "var(--badge-gold)" };

/** The live speaker, surfaced compactly for the dashboard: avatar + direction + countdown. */
function SpeakingRow({ speech, seat }: { speech: Speech; seat?: Seat }) {
  const { t } = useTranslation();
  const Dir = speech.direction === "UP" ? ArrowUp : ArrowDown;
  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={speech.speakerSeat}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -10 }}
        transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
        style={{ display: "flex", alignItems: "center", gap: 14, flexWrap: "wrap" }}
      >
        <Avatar size="lg" speaking name={seat?.displayName ?? `#${speech.speakerSeat}`} avatar={seat?.avatar} />
        <span style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          <span className="mono" style={{ fontSize: 22, fontWeight: 900 }}>{seatLabel(speech.speakerSeat ?? 0)}</span>
          {speech.direction && (
            <span style={{ display: "inline-flex", alignItems: "center", gap: 5, fontSize: 11, fontWeight: 700, color: "var(--moon-300)" }}>
              <Dir size={13} />
              {t("speech.speakingDir", { direction: speech.direction === "UP" ? t("speech.directionUp") : t("speech.directionDown") })}
            </span>
          )}
        </span>
        {speech.endsAt && <span style={{ marginLeft: "auto" }}><Countdown endsAt={speech.endsAt} size="md" /></span>}
      </motion.div>
    </AnimatePresence>
  );
}

/**
 * The speech-manager stages (發言 / 警長競選 / 放逐投票) surfaced on the dashboard the same way the
 * night engine is — a bordered status card that appears the moment a stage is reached, so the judge
 * never has to leave the main board to watch it. Read-only mirror; the stage controls live on the
 * dedicated `/speech` screen (`SpeechManager`).
 */
export function SpeechBoard() {
  const { t } = useTranslation();
  const { guildId, demo, readOnly } = useGuild();
  const actions = useGameActions(guildId, demo);
  const snapshot = useGameStore((s) => s.snapshot);
  const speech = snapshot?.speech;
  const poll = snapshot?.poll;
  const seatBySeat = (n: number): Seat | undefined => snapshot?.seats.find((s) => s.seat === n);

  const speechLive = !!(speech?.active || speech?.waiting);
  if (!speechLive && !poll) return null;

  const election = poll?.kind === "POLICE";
  const accent = election ? GOLD : MOON;

  // Title/subtitle track whichever stage is primary; a poll outranks a bare speaking turn (the police
  // campaign runs a speech *inside* the election, so the poll framing is the more useful headline).
  const title = poll ? (election ? t("election.title") : t("expel.title")) : t("speech.title");
  const subtitle = poll
    ? t(`election.stage.${POLL_STAGE_KEY[poll.stage] ?? "voting"}`)
    : speech?.active
      ? t("speech.speaking")
      : t("speech.waitingDirection");

  return (
    <motion.div
      layout
      className="wh-card"
      style={{ marginBottom: 16, border: `1px solid ${accent.border}`, boxShadow: `0 0 24px ${accent.glow}`, overflow: "hidden" }}
    >
      <header style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 18px", borderBottom: "1px solid var(--border-1)", flexWrap: "wrap" }}>
        <span style={{ width: 38, height: 38, borderRadius: "var(--r-md)", display: "flex", alignItems: "center", justifyContent: "center", background: accent.soft, border: `1px solid ${accent.border}`, color: accent.fg }}>
          {poll ? <Vote size={20} /> : <Megaphone size={20} />}
        </span>
        <span style={{ display: "flex", flexDirection: "column" }}>
          <span style={{ fontSize: 15, fontWeight: 900 }}>{title}</span>
          <span style={{ fontSize: 11, color: "var(--text-muted)" }}>{subtitle}</span>
        </span>
        {poll?.endsAt && (
          <span style={{ marginLeft: "auto" }}>
            <Countdown endsAt={poll.endsAt} size="md" label={election ? t("election.stageHint") : t("expel.deadline")} />
          </span>
        )}
      </header>

      <div style={{ padding: 18, display: "flex", flexDirection: "column", gap: 16 }}>
        {speech?.active && speech.speakerSeat != null && <SpeakingRow speech={speech} seat={seatBySeat(speech.speakerSeat)} />}

        {speech?.active && !speech.lastWords && (
          <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <span style={{ display: "inline-flex", alignItems: "center", gap: 6, fontSize: 12, fontWeight: 700, color: "var(--text-secondary)", background: "var(--surface-card)", border: "1px solid var(--border-1)", borderRadius: "var(--r-full)", padding: "4px 12px" }} className="mono">
              {t("speech.stepDownVotes", { count: speech.interruptVoters.length, threshold: speech.interruptThreshold })}
            </span>
            {!readOnly && (
              <span style={{ marginLeft: "auto", display: "flex", gap: 8 }}>
                <Button variant="secondary" size="sm" onClick={actions.skipSpeech}>{t("speech.skip")}</Button>
                {/* 下台: judge override that forces the speaker off — backend-identical to skip (advance). */}
                <Button variant="danger" size="sm" onClick={actions.skipSpeech}>{t("speech.stepDown")}</Button>
              </span>
            )}
          </div>
        )}

        {speech?.waiting && (
          <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            <span style={{ fontSize: 14, fontWeight: 800 }}>{t("speech.waitingDirection")}</span>
            <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{t("speech.directionPrompt", { seat: speech.fromSeat ?? "" })}</span>
          </div>
        )}

        {speech?.active && speech.upcoming.length > 0 && (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            <span style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.08em", color: "var(--text-muted)" }}>{t("speech.upcoming")}</span>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
              {speech.upcoming.map((seat, i) => {
                const s = seatBySeat(seat);
                return (
                  <span key={seat} style={{ display: "inline-flex", alignItems: "center", gap: 7, padding: "5px 10px", borderRadius: "var(--r-full)", background: "var(--surface-card)", border: "1px solid var(--border-1)" }}>
                    <span className="mono" style={{ fontSize: 10, color: "var(--text-muted)" }}>{i + 1}</span>
                    <Avatar size="sm" name={s?.displayName ?? `#${seat}`} avatar={s?.avatar} />
                    <span className="mono" style={{ fontSize: 12, fontWeight: 700 }}>{seatLabel(seat)}</span>
                  </span>
                );
              })}
            </div>
          </div>
        )}

        {poll && <PollResults />}
      </div>
    </motion.div>
  );
}

const POLL_STAGE_KEY: Record<string, string> = {
  ENROLL: "enroll",
  CAMPAIGN: "campaign",
  WITHDRAW: "withdraw",
  VOTING: "voting",
  RESOLVED: "resolution",
};
