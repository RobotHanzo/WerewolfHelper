import { useState, useRef, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "framer-motion";
import { useGameStore } from "@/stores/gameStore";
import { useUiStore } from "@/stores/uiStore";
import { useGameActions } from "@/hooks/useGameActions";
import { api } from "@/api/client";
import { Modal } from "@/components/ui/Modal";
import { Button } from "@/components/ui/Button";
import { ProgressBar } from "@/components/ui/ProgressBar";

export function Overlays({ guildId, demo }: { guildId: string; demo: boolean }) {
  return (
    <>
      <KillModal guildId={guildId} demo={demo} />
      <EditModal />
      <PickerModal guildId={guildId} demo={demo} />
      <TimerModal />
      <ProgressOverlay />
      <SessionExpiredModal />
      <Toast />
    </>
  );
}

function EditModal() {
  const { t } = useTranslation();
  const editSeat = useUiStore((s) => s.editSeat);
  const close = useUiStore((s) => s.closeEdit);
  const seat = useGameStore((s) => s.snapshot?.seats.find((x) => x.seat === editSeat) ?? null);

  return (
    <Modal open={editSeat != null} onClose={close} width={420}>
      {seat && (
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <h2 style={{ margin: 0, fontSize: 17, fontWeight: 900 }}>{t("edit.title", { seat: seat.label })}</h2>
          <span style={{ fontSize: 11, fontWeight: 700, color: "var(--text-muted)", letterSpacing: "0.08em" }}>{t("edit.identitiesInOrder")}</span>
          {seat.identities.map((idn, i) => (
            <div key={i} style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 10px", borderRadius: "var(--r-md)", background: "var(--surface-app)", border: "1px solid var(--border-1)" }}>
              <span className="mono" style={{ fontSize: 11, color: "var(--text-muted)" }}>{i + 1}</span>
              <span style={{ fontSize: 13, fontWeight: 700 }}>{idn.name}</span>
              {idn.dead && <span style={{ marginLeft: "auto", fontSize: 11, color: "var(--danger-500)" }}>{t("badge.dead")}</span>}
            </div>
          ))}
          <div style={{ display: "flex", justifyContent: "flex-end" }}>
            <Button variant="primary" onClick={close}>{t("common.done")}</Button>
          </div>
        </div>
      )}
    </Modal>
  );
}

function PickerModal({ guildId, demo }: { guildId: string; demo: boolean }) {
  const { t } = useTranslation();
  const picker = useUiStore((s) => s.picker);
  const close = useUiStore((s) => s.closePicker);
  const showToast = useUiStore((s) => s.showToast);
  const seats = useGameStore((s) => s.snapshot?.seats);
  const [query, setQuery] = useState("");
  const rows = (seats ?? [])
    .filter((s) => !s.unassigned && (query === "" || s.displayName?.includes(query) || s.label.includes(query)))
    .slice(0, 25);

  return (
    <Modal open={picker != null} onClose={close} width={400}>
      {picker && (
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <h2 style={{ margin: 0, fontSize: 17, fontWeight: 900 }}>{picker.title}</h2>
          <input className="wh-input" placeholder={t("common.search")} value={query} onChange={(e) => setQuery(e.target.value)} />
          <div style={{ display: "flex", flexDirection: "column", gap: 6, maxHeight: "50vh", overflowY: "auto" }}>
            {rows.length === 0 && <span style={{ fontSize: 12, color: "var(--text-muted)", textAlign: "center", padding: 14 }}>{t("picker.empty")}</span>}
            {rows.map((s) => (
              <button
                key={s.seat}
                className="wh-card"
                style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 10px", cursor: "pointer", textAlign: "left" }}
                onClick={() => { void (demo || api.forcePolice(guildId, s.seat)); showToast(`${picker.title} · 玩家${s.label}`); close(); }}
              >
                <span className="mono" style={{ fontWeight: 700 }}>玩家{s.label}</span>
                <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{s.displayName}</span>
                <span style={{ marginLeft: "auto", color: "var(--text-muted)" }}>→</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </Modal>
  );
}

function KillModal({ guildId, demo }: { guildId: string; demo: boolean }) {
  const { t } = useTranslation();
  const target = useUiStore((s) => s.killTarget);
  const close = useUiStore((s) => s.closeKill);
  const actions = useGameActions(guildId, demo);
  const [lastWords, setLastWords] = useState(true);

  return (
    <Modal open={target != null} onClose={close} width={360}>
      {target && (
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <h2 style={{ margin: 0, fontSize: 17, fontWeight: 900 }}>
            {t("kill.title", { seat: String(target.seat).padStart(2, "0") })}
          </h2>
          <p style={{ margin: 0, fontSize: 13, color: "var(--text-secondary)", lineHeight: 1.7 }}>
            {t("kill.body", { identity: target.identityName })}
          </p>
          <label style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 14, cursor: "pointer" }}>
            <input type="checkbox" checked={lastWords} onChange={(e) => setLastWords(e.target.checked)} style={{ accentColor: "var(--moon-500)", width: 16, height: 16 }} />
            {t("kill.allowLastWords")}
          </label>
          <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <Button variant="ghost" onClick={close}>{t("common.cancel")}</Button>
            <Button variant="danger" armed armedLabel={t("kill.confirm")} onClick={() => { actions.kill(target.seat, target.identityIndex); close(); }}>
              {t("kill.confirm")}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}

function TimerModal() {
  const { t } = useTranslation();
  const open = useUiStore((s) => s.timerOpen);
  const setOpen = useUiStore((s) => s.setTimerOpen);
  const showToast = useUiStore((s) => s.showToast);
  const [min, setMin] = useState("1");
  const [sec, setSec] = useState("30");
  const presets = [30, 60, 90, 180];

  return (
    <Modal open={open} onClose={() => setOpen(false)} width={360}>
      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <h2 style={{ margin: 0, fontSize: 17, fontWeight: 900 }}>{t("timer.title")}</h2>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 6 }}>
          {presets.map((p) => (
            <button key={p} className="wh-btn wh-btn--secondary wh-btn--sm" style={{ height: 40 }} onClick={() => { setMin(String(Math.floor(p / 60))); setSec(String(p % 60)); }}>
              <span className="mono">{p}s</span>
            </button>
          ))}
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          <input className="wh-input wh-input--mono" style={{ flex: 1, minWidth: 0 }} value={min} onChange={(e) => setMin(e.target.value)} aria-label={t("timer.minutes")} />
          <span style={{ fontSize: 13, fontWeight: 700, color: "var(--text-secondary)" }}>{t("timer.minutes")}</span>
          <input className="wh-input wh-input--mono" style={{ flex: 1, minWidth: 0 }} value={sec} onChange={(e) => setSec(e.target.value)} aria-label={t("timer.seconds")} />
          <span style={{ fontSize: 13, fontWeight: 700, color: "var(--text-secondary)" }}>{t("timer.seconds")}</span>
        </div>
        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
          <Button variant="ghost" onClick={() => setOpen(false)}>{t("common.cancel")}</Button>
          <Button variant="primary" onClick={() => { setOpen(false); showToast(t("timer.start")); }}>{t("timer.start")}</Button>
        </div>
      </div>
    </Modal>
  );
}

function ProgressOverlay() {
  const { t } = useTranslation();
  const progress = useGameStore((s) => s.progress);
  const close = useGameStore((s) => s.closeProgress);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [progress?.lines.length]);

  return (
    <AnimatePresence>
      {progress && (
        <motion.div className="wh-modal-backdrop" style={{ zIndex: 60 }} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <motion.div className="wh-card" style={{ width: 520, maxWidth: "100%", padding: 28, borderRadius: "var(--r-xl)", boxShadow: "var(--shadow-3)", display: "flex", flexDirection: "column", gap: 16 }} initial={{ scale: 0.96 }} animate={{ scale: 1 }}>
            <header style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <h2 style={{ margin: 0, fontSize: 17, fontWeight: 900 }}>{progress.title}</h2>
              <span className="mono" style={{ marginLeft: "auto", fontWeight: 700, color: "var(--moon-300)" }}>{progress.percent}%</span>
            </header>
            <ProgressBar percent={progress.percent} state={progress.state} />
            <div ref={scrollRef} className="mono" style={{ display: "flex", flexDirection: "column", gap: 4, maxHeight: 220, overflowY: "auto", padding: 12, borderRadius: "var(--r-md)", background: "var(--surface-app)", border: "1px solid var(--border-1)", fontSize: 12, lineHeight: 1.8 }}>
              {progress.lines.map((l, i) => (
                <span key={i} style={{ color: l.severity === "alert" ? "var(--danger-500)" : "var(--text-secondary)" }}>{l.line}</span>
              ))}
            </div>
            {progress.state === "running" && <span style={{ fontSize: 12, color: "var(--text-muted)" }}>{t("longOp.running")}</span>}
            {progress.state === "error" && (
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ fontSize: 12.5, fontWeight: 700, color: "var(--danger-500)" }}>⚠ {t("longOp.error")}</span>
                <Button variant="secondary" size="sm" style={{ marginLeft: "auto" }} onClick={close}>{t("common.ack")}</Button>
              </div>
            )}
            {progress.state === "success" && (
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ fontSize: 12.5, fontWeight: 700, color: "var(--success-500)" }}>✓ {t("longOp.success")}</span>
                <Button variant="secondary" size="sm" style={{ marginLeft: "auto" }} onClick={close}>{t("common.ack")}</Button>
              </div>
            )}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function SessionExpiredModal() {
  const { t } = useTranslation();
  const expired = useGameStore((s) => s.sessionExpired);

  return (
    <AnimatePresence>
      {expired && (
        <motion.div className="wh-modal-backdrop" style={{ zIndex: 80 }} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <div className="wh-card" style={{ width: 360, maxWidth: "100%", padding: "32px 28px", borderRadius: "var(--r-xl)", boxShadow: "var(--shadow-3)", display: "flex", flexDirection: "column", alignItems: "center", gap: 12, textAlign: "center" }}>
            <h2 style={{ margin: 0, fontSize: 18, fontWeight: 900 }}>{t("sessionExpired.title")}</h2>
            <p style={{ margin: 0, fontSize: 13, color: "var(--text-secondary)", lineHeight: 1.7 }}>{t("sessionExpired.body")}</p>
            <button onClick={() => (window.location.href = api.loginUrl())} style={{ marginTop: 8, width: "100%", height: 44, border: "none", borderRadius: "var(--r-md)", background: "#5865F2", color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer" }}>
              {t("login.reloginWithDiscord")}
            </button>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function Toast() {
  const toast = useUiStore((s) => s.toast);
  return (
    <AnimatePresence>
      {toast && (
        <motion.div
          className="wh-toast"
          style={{ x: "-50%" }}
          initial={{ opacity: 0, y: 16, x: "-50%" }}
          animate={{ opacity: 1, y: 0, x: "-50%" }}
          exit={{ opacity: 0, y: 16, x: "-50%" }}
        >
          <span style={{ width: 8, height: 8, borderRadius: "50%", background: toast.isError ? "var(--danger-500)" : "var(--moon-400)" }} />
          {toast.text}
        </motion.div>
      )}
    </AnimatePresence>
  );
}
