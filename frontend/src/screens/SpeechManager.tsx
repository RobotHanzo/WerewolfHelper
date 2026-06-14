import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "framer-motion";
import { useGameStore } from "@/stores/gameStore";
import { useGuild } from "@/hooks/useGuild";
import { useGameActions } from "@/hooks/useGameActions";
import { Avatar } from "@/components/ui/Avatar";
import { Countdown } from "@/components/ui/Countdown";
import { Button } from "@/components/ui/Button";

export function SpeechManager() {
  const { t } = useTranslation();
  const { guildId, demo, readOnly } = useGuild();
  const actions = useGameActions(guildId, demo);
  const snapshot = useGameStore((s) => s.snapshot);
  if (!snapshot) return null;

  const { speech, poll } = snapshot;
  const idle = !speech?.active && !speech?.waiting && !poll;
  const seatBySeat = (n: number) => snapshot.seats.find((s) => s.seat === n);
  const speaker = speech?.speakerSeat != null ? seatBySeat(speech.speakerSeat) : undefined;

  return (
    <div style={{ maxWidth: 980, margin: "0 auto", display: "flex", flexDirection: "column", gap: 16 }}>
      <header style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 900 }}>{t("speech.title")}</h1>
      </header>

      {idle && (
        <section className="wh-card" style={{ padding: "56px 32px", display: "flex", flexDirection: "column", alignItems: "center", gap: 14, textAlign: "center", borderRadius: "var(--r-xl)" }}>
          <span style={{ fontSize: 16, fontWeight: 700, color: "var(--text-secondary)" }}>{t("speech.idle")}</span>
          <span style={{ fontSize: 13, color: "var(--text-muted)" }}>{readOnly ? t("speech.idleHintSpectator") : t("speech.idleHintJudge")}</span>
          {!readOnly && (
            <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "center", gap: 10, marginTop: 8 }}>
              <Button variant="primary" onClick={actions.startSpeech}>{t("speech.startSpeech")}</Button>
              <Button variant="secondary" onClick={actions.startElection}>{t("speech.startElection")}</Button>
              <Button variant="secondary" onClick={actions.startExpel}>{t("speech.startExpel")}</Button>
            </div>
          )}
        </section>
      )}

      {speech?.waiting && (
        <section className="wh-card" style={{ padding: "40px 32px", display: "flex", flexDirection: "column", alignItems: "center", gap: 12, textAlign: "center", borderRadius: "var(--r-xl)" }}>
          <span style={{ fontSize: 15, fontWeight: 800 }}>{t("speech.waitingDirection")}</span>
          <span style={{ fontSize: 13, color: "var(--text-muted)" }}>{t("speech.directionPrompt", { seat: speech.fromSeat ?? "" })}</span>
          {!readOnly && (
            <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "center", gap: 10, marginTop: 6 }}>
              <Button variant="primary" onClick={() => actions.setSpeechDirection("UP")}>{t("speech.dirUp")}</Button>
              <Button variant="primary" onClick={() => actions.setSpeechDirection("DOWN")}>{t("speech.dirDown")}</Button>
            </div>
          )}
        </section>
      )}

      <AnimatePresence mode="wait">
        {speech?.active && speech.speakerSeat != null && (
          <motion.section
            key={speech.speakerSeat}
            className="wh-card"
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -16 }}
            transition={{ duration: 0.32, ease: [0.22, 1, 0.36, 1] }}
            style={{ padding: "44px 32px 36px", display: "flex", flexDirection: "column", alignItems: "center", gap: 18, position: "relative", overflow: "hidden", borderRadius: "var(--r-xl)" }}
          >
            <div style={{ position: "absolute", inset: 0, background: "radial-gradient(ellipse 60% 45% at 50% 0%, rgba(84,210,228,0.08), transparent)", pointerEvents: "none" }} />
            <span style={{ display: "inline-flex", alignItems: "center", gap: 8, fontSize: 12, fontWeight: 700, letterSpacing: "0.12em", color: "var(--moon-300)", background: "var(--accent-soft)", border: "1px solid rgba(84,210,228,0.3)", borderRadius: "var(--r-full)", padding: "4px 14px" }}>
              {t("speech.speakingDir", { direction: speech.direction === "UP" ? t("speech.directionUp") : t("speech.directionDown") })}
            </span>
            <Avatar size="xl" speaking name={speaker?.displayName ?? `#${speech.speakerSeat}`} avatar={speaker?.avatar} />
            <div style={{ textAlign: "center" }}>
              <span className="mono" style={{ fontSize: 30, fontWeight: 900 }}>玩家{String(speech.speakerSeat).padStart(2, "0")}</span>
            </div>
            {speech.endsAt && <Countdown endsAt={speech.endsAt} size="stage" />}
            {!readOnly && (
              <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "center", gap: 10, marginTop: 6 }}>
                <Button variant="secondary" onClick={actions.skipSpeech}>{t("speech.skip")}</Button>
                <Button variant="danger" onClick={actions.terminateSpeech}>{t("speech.terminate")}</Button>
              </div>
            )}
          </motion.section>
        )}
      </AnimatePresence>

      {speech?.active && speech.upcoming.length > 0 && (
        <section style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          <span style={{ fontSize: 13, fontWeight: 700, color: "var(--text-secondary)", letterSpacing: "0.08em" }}>{t("speech.upcoming")}</span>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))", gap: 8 }}>
            {speech.upcoming.map((seat, i) => {
              const s = seatBySeat(seat);
              return (
                <div key={seat} className="wh-card" style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 12px" }}>
                  <span className="mono" style={{ fontSize: 11, color: "var(--text-muted)" }}>{i + 1}</span>
                  <Avatar size="sm" name={s?.displayName ?? `#${seat}`} avatar={s?.avatar} />
                  <span className="mono" style={{ fontSize: 13, fontWeight: 700 }}>玩家{String(seat).padStart(2, "0")}</span>
                </div>
              );
            })}
          </div>
        </section>
      )}

      {poll && <VotePanel />}
    </div>
  );
}

const POLL_STAGE_KEY: Record<string, string> = {
  ENROLL: "enroll",
  CAMPAIGN: "campaign",
  WITHDRAW: "withdraw",
  VOTING: "voting",
  RESOLVED: "resolution",
};

/** The turnout bar + per-candidate weight bars — the poll body, reused bare inside the dashboard
 *  `SpeechBoard` (which supplies its own header) and wrapped in a card by `VotePanel` below. */
export function PollResults() {
  const { t } = useTranslation();
  const poll = useGameStore((s) => s.snapshot?.poll);
  const seats = useGameStore((s) => s.snapshot?.seats);
  if (!poll) return null;
  const notVoted = poll.eligibleVoters - poll.votesCast;
  const maxWeight = Math.max(1, ...poll.candidates.map((x) => x.weight));

  return (
    <>
      <div style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 12, color: "var(--text-muted)" }} className="mono">
        <span>{t("expel.progress")}</span>
        <span style={{ flex: 1, height: 6, borderRadius: "var(--r-full)", background: "var(--surface-app)", overflow: "hidden" }}>
          <span style={{ display: "block", height: "100%", width: `${(poll.votesCast / Math.max(1, poll.eligibleVoters)) * 100}%`, background: "var(--moon-500)" }} />
        </span>
        <span>{t("expel.turnoutShort", { cast: poll.votesCast, eligible: poll.eligibleVoters, remaining: notVoted })}</span>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        {poll.candidates.map((c) => {
          const s = seats?.find((x) => x.seat === c.seat);
          return (
          <motion.div layout key={c.seat} style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 12px", borderRadius: "var(--r-md)", background: "var(--surface-app)", border: "1px solid var(--border-1)", opacity: c.withdrawn ? 0.5 : 1 }}>
            <Avatar size="sm" name={s?.displayName ?? `#${c.seat}`} avatar={s?.avatar} />
            <span className="mono" style={{ fontSize: 13, fontWeight: 700, width: 64, textDecoration: c.withdrawn ? "line-through" : "none" }}>玩家{String(c.seat).padStart(2, "0")}</span>
            <span style={{ flex: 1, height: 8, borderRadius: "var(--r-full)", background: "var(--surface-card)", overflow: "hidden" }}>
              <motion.span layout style={{ display: "block", height: "100%", width: `${(c.weight / maxWeight) * 100}%`, background: "var(--moon-500)" }} />
            </span>
            <span className="mono" style={{ fontSize: 16, fontWeight: 700, color: "var(--moon-300)", width: 44, textAlign: "right" }}>{c.weight % 1 ? c.weight.toFixed(1) : c.weight}</span>
          </motion.div>
          );
        })}
      </div>
    </>
  );
}

export function VotePanel({ controls = true }: { controls?: boolean } = {}) {
  const { t } = useTranslation();
  const { guildId, demo, readOnly } = useGuild();
  const actions = useGameActions(guildId, demo);
  const poll = useGameStore((s) => s.snapshot?.poll);
  if (!poll) return null;
  const isElection = poll.kind === "POLICE";

  return (
    <section className="wh-card" style={{ padding: 28, display: "flex", flexDirection: "column", gap: 16, borderRadius: "var(--r-xl)" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <span style={{ fontSize: 17, fontWeight: 900 }}>{isElection ? t("election.title") : t("expel.title")}</span>
        <span style={{ fontSize: 11, fontWeight: 700, letterSpacing: "0.08em", color: "var(--moon-300)", background: "var(--accent-soft)", border: "1px solid rgba(84,210,228,0.3)", borderRadius: "var(--r-full)", padding: "3px 12px" }}>
          {t(`election.stage.${POLL_STAGE_KEY[poll.stage] ?? "voting"}`)}
        </span>
        <span style={{ marginLeft: "auto" }} />
        {poll.endsAt && <Countdown endsAt={poll.endsAt} size="md" label={isElection ? t("election.stageHint") : t("expel.deadline")} />}
      </div>
      <PollResults />
      {controls && !readOnly && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 10, justifyContent: "flex-end" }}>
          {isElection && <Button variant="secondary" onClick={actions.advancePoll}>{t("election.advanceStage")}</Button>}
          <Button variant="danger" onClick={actions.resolvePoll}>{t("election.resolveNow")}</Button>
        </div>
      )}
    </section>
  );
}
