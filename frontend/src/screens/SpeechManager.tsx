import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "framer-motion";
import { useGameStore } from "@/stores/gameStore";
import { Avatar } from "@/components/ui/Avatar";
import { Countdown } from "@/components/ui/Countdown";
import { Button } from "@/components/ui/Button";

export function SpeechManager({ readOnly }: { readOnly: boolean }) {
  const { t } = useTranslation();
  const snapshot = useGameStore((s) => s.snapshot);
  if (!snapshot) return null;

  const { speech, poll } = snapshot;
  const idle = !speech?.active && !speech?.waiting && !poll;

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
            <div style={{ display: "flex", gap: 10, marginTop: 8 }}>
              <Button variant="primary">{t("speech.startSpeech")}</Button>
              <Button variant="secondary">{t("speech.startElection")}</Button>
              <Button variant="secondary">{t("speech.startExpel")}</Button>
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
            <Avatar size="xl" speaking name={`#${speech.speakerSeat}`} />
            <div style={{ textAlign: "center" }}>
              <span className="mono" style={{ fontSize: 30, fontWeight: 900 }}>玩家{String(speech.speakerSeat).padStart(2, "0")}</span>
            </div>
            {speech.endsAt && <Countdown endsAt={speech.endsAt} size="stage" />}
            {!readOnly && (
              <div style={{ display: "flex", gap: 10, marginTop: 6 }}>
                <Button variant="secondary">{t("speech.skip")}</Button>
                <Button variant="danger">{t("speech.terminate")}</Button>
              </div>
            )}
          </motion.section>
        )}
      </AnimatePresence>

      {speech?.active && speech.upcoming.length > 0 && (
        <section style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          <span style={{ fontSize: 13, fontWeight: 700, color: "var(--text-secondary)", letterSpacing: "0.08em" }}>{t("speech.upcoming")}</span>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8 }}>
            {speech.upcoming.map((seat, i) => (
              <div key={seat} className="wh-card" style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 12px" }}>
                <span className="mono" style={{ fontSize: 11, color: "var(--text-muted)" }}>{i + 1}</span>
                <Avatar size="sm" name={`#${seat}`} />
                <span className="mono" style={{ fontSize: 13, fontWeight: 700 }}>玩家{String(seat).padStart(2, "0")}</span>
              </div>
            ))}
          </div>
        </section>
      )}

      {poll && <VotePanel />}
    </div>
  );
}

function VotePanel() {
  const { t } = useTranslation();
  const poll = useGameStore((s) => s.snapshot?.poll);
  if (!poll) return null;
  const notVoted = poll.eligibleVoters - poll.votesCast;
  const isElection = poll.kind === "POLICE";

  return (
    <section className="wh-card" style={{ padding: 28, display: "flex", flexDirection: "column", gap: 16, borderRadius: "var(--r-xl)" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <span style={{ fontSize: 17, fontWeight: 900 }}>{isElection ? t("election.title") : t("expel.title")}</span>
        <span style={{ marginLeft: "auto" }} />
        {poll.endsAt && <Countdown endsAt={poll.endsAt} size="md" label={isElection ? t("election.stageHint") : t("expel.deadline")} />}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 12, color: "var(--text-muted)" }} className="mono">
        <span>{t("expel.progress")}</span>
        <span style={{ flex: 1, height: 6, borderRadius: "var(--r-full)", background: "var(--surface-app)", overflow: "hidden" }}>
          <span style={{ display: "block", height: "100%", width: `${(poll.votesCast / Math.max(1, poll.eligibleVoters)) * 100}%`, background: "var(--moon-500)" }} />
        </span>
        <span>{t("expel.turnoutShort", { cast: poll.votesCast, eligible: poll.eligibleVoters, remaining: notVoted })}</span>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        {poll.candidates.map((c) => {
          const maxWeight = Math.max(1, ...poll.candidates.map((x) => x.weight));
          return (
            <motion.div layout key={c.seat} style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 12px", borderRadius: "var(--r-md)", background: "var(--surface-app)", border: "1px solid var(--border-1)", opacity: c.withdrawn ? 0.5 : 1 }}>
              <Avatar size="sm" name={`#${c.seat}`} />
              <span className="mono" style={{ fontSize: 13, fontWeight: 700, width: 64, textDecoration: c.withdrawn ? "line-through" : "none" }}>玩家{String(c.seat).padStart(2, "0")}</span>
              <span style={{ flex: 1, height: 8, borderRadius: "var(--r-full)", background: "var(--surface-card)", overflow: "hidden" }}>
                <motion.span layout style={{ display: "block", height: "100%", width: `${(c.weight / maxWeight) * 100}%`, background: "var(--moon-500)" }} />
              </span>
              <span className="mono" style={{ fontSize: 16, fontWeight: 700, color: "var(--moon-300)", width: 44, textAlign: "right" }}>{c.weight % 1 ? c.weight.toFixed(1) : c.weight}</span>
            </motion.div>
          );
        })}
      </div>
    </section>
  );
}
