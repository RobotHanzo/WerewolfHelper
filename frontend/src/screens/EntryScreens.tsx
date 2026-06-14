import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { AnimatePresence, motion } from "framer-motion";
import { Eye } from "lucide-react";
import { api } from "@/api/client";
import { useAuthStore } from "@/stores/authStore";
import { Avatar } from "@/components/ui/Avatar";
import { Button } from "@/components/ui/Button";
import type { SessionSummary } from "@/types/snapshot";

const centered: React.CSSProperties = {
  minHeight: "100vh",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  gap: 14,
  padding: 24,
  boxSizing: "border-box",
};

const card: React.CSSProperties = {
  background: "var(--surface-card)",
  border: "1px solid var(--border-1)",
  borderRadius: "var(--r-xl)",
  boxShadow: "var(--shadow-3)",
  padding: "44px 36px 32px",
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  gap: 8,
  textAlign: "center",
};

const DiscordMark = () => (
  <svg width="22" height="17" viewBox="0 0 71 55" fill="#fff" aria-hidden>
    <path d="M60.1 4.9A58.5 58.5 0 0 0 45.4.4a.2.2 0 0 0-.2.1c-.6 1.1-1.3 2.6-1.8 3.7a54 54 0 0 0-16.2 0C26.6 3 25.9 1.6 25.2.5a.2.2 0 0 0-.2-.1 58.4 58.4 0 0 0-14.7 4.6.2.2 0 0 0-.1 0A60 60 0 0 0 .3 45.4a.2.2 0 0 0 .1.2 58.9 58.9 0 0 0 17.7 9 .2.2 0 0 0 .3-.1c1.4-1.9 2.6-3.9 3.6-6a.2.2 0 0 0-.1-.3c-2-.7-3.8-1.6-5.6-2.6a.2.2 0 0 1 0-.4l1.1-.9a.2.2 0 0 1 .2 0 41.9 41.9 0 0 0 35.6 0 .2.2 0 0 1 .2 0l1.1.9a.2.2 0 0 1 0 .4c-1.8 1-3.7 1.9-5.6 2.6a.2.2 0 0 0-.1.3c1 2.1 2.3 4.1 3.6 6a.2.2 0 0 0 .3.1 58.7 58.7 0 0 0 17.8-9 .2.2 0 0 0 .1-.2c1.6-16.4-2.7-30.6-10.7-40.4a.2.2 0 0 0-.1-.1ZM23.7 37.3c-3.5 0-6.4-3.2-6.4-7.2s2.8-7.2 6.4-7.2c3.6 0 6.5 3.3 6.4 7.2 0 4-2.8 7.2-6.4 7.2Zm23.6 0c-3.5 0-6.4-3.2-6.4-7.2s2.8-7.2 6.4-7.2c3.6 0 6.5 3.3 6.4 7.2 0 4-2.8 7.2-6.4 7.2Z" />
  </svg>
);

export function LoginScreen({ onLogin, onViewDemo }: { onLogin: () => void; onViewDemo?: () => void }) {
  const { t } = useTranslation();
  const auth = useAuthStore((s) => s.auth);
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.18, ease: "easeOut" }}
      style={{ ...centered, background: "radial-gradient(ellipse 70% 50% at 50% 0%, rgba(84,210,228,0.07), transparent)" }}
    >
      <motion.div
        initial={{ scale: 0.98, opacity: 0, y: 8 }}
        animate={{ scale: 1, opacity: 1, y: 0 }}
        exit={{ scale: 0.98, opacity: 0, y: -8 }}
        transition={{ duration: 0.22, ease: "easeOut" }}
        style={{ ...card, width: "100%", maxWidth: 380 }}
      >
        <img src="/logo.svg" alt="" width={72} height={72} />
        <h1 style={{ margin: "8px 0 0", fontSize: 26, fontWeight: 900, letterSpacing: "0.04em" }}>{t("app.name")}</h1>
        <span className="mono" style={{ fontSize: 11, color: "var(--moon-400)", letterSpacing: "0.24em" }}>{t("app.wordmark")}</span>
        
        <div style={{ width: "100%", display: "flex", flexDirection: "column", position: "relative" }}>
          <AnimatePresence mode="wait">
            {auth ? (
              <motion.div
                key="profile-card"
                initial={{ opacity: 0, y: 6, scale: 0.99 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -6, scale: 0.99 }}
                transition={{ duration: 0.18, ease: "easeOut" }}
                style={{ width: "100%", display: "flex", flexDirection: "column", alignItems: "center" }}
              >
                <div style={{
                  marginTop: 24,
                  width: "100%",
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  gap: 16,
                  padding: "20px 16px",
                  borderRadius: "var(--r-lg)",
                  background: "linear-gradient(135deg, rgba(84,210,228,0.06), rgba(84,210,228,0.01))",
                  border: "1px solid rgba(84,210,228,0.15)",
                  boxShadow: "inset 0 0 12px rgba(84,210,228,0.05)",
                  position: "relative",
                  overflow: "hidden",
                  boxSizing: "border-box"
                }}>
                  {/* Subtle glow effect in the card */}
                  <div style={{
                    position: "absolute",
                    top: -20,
                    left: -20,
                    width: 80,
                    height: 80,
                    background: "rgba(84,210,228,0.15)",
                    filter: "blur(20px)",
                    borderRadius: "50%",
                    pointerEvents: "none"
                  }} />

                  {/* Avatar with circular container and glowing effect */}
                  <div style={{
                    position: "relative",
                    display: "inline-flex",
                    padding: 4,
                    borderRadius: "50%",
                    background: "linear-gradient(135deg, var(--moon-400), rgba(84,210,228,0.3))",
                    boxShadow: "0 0 16px rgba(84,210,228,0.2)"
                  }}>
                    <Avatar name={auth.username} avatar={auth.avatar} size="lg" />
                  </div>

                  <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                    <span style={{ fontSize: 18, fontWeight: 800, color: "var(--text-body)" }}>
                      {auth.username}
                    </span>
                    <span style={{ fontSize: 12, color: "var(--text-muted)", letterSpacing: "0.05em" }}>
                      {t("login.loggedInAs", { name: `@${auth.username}` })}
                    </span>
                  </div>
                </div>

                {/* Action buttons */}
                <button
                  type="button"
                  onClick={() => navigate("/servers")}
                  className="wh-btn wh-btn--lg wh-btn--primary"
                  style={{
                    marginTop: 20,
                    width: "100%",
                    height: 48,
                    border: "none",
                    borderRadius: "var(--r-md)",
                    fontSize: 15,
                    fontWeight: 700,
                    cursor: "pointer",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: 10,
                    transition: "transform 0.2s, box-shadow 0.2s"
                  }}
                >
                  {t("login.selectServer")}
                </button>

                <button
                  type="button"
                  disabled={loggingOut}
                  onClick={async () => {
                    setLoggingOut(true);
                    try {
                      if (!useAuthStore.getState().demo) {
                        await api.logout();
                      }
                    } catch (err) {
                      console.error("Logout failed:", err);
                    } finally {
                      useAuthStore.setState({ auth: null, demo: false });
                      setLoggingOut(false);
                    }
                  }}
                  className="wh-btn wh-btn--ghost"
                  style={{
                    marginTop: 8,
                    width: "100%",
                    height: 36,
                    fontSize: 13,
                    color: "var(--text-muted)",
                    fontWeight: 500,
                    cursor: "pointer",
                    background: "transparent",
                    border: "none",
                    borderRadius: "var(--r-md)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: 6
                  }}
                >
                  {t("login.notYou")}
                </button>
              </motion.div>
            ) : (
              <motion.div
                key="login-form"
                initial={{ opacity: 0, y: 6, scale: 0.99 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: -6, scale: 0.99 }}
                transition={{ duration: 0.18, ease: "easeOut" }}
                style={{ width: "100%", display: "flex", flexDirection: "column" }}
              >
                <button
                  type="button"
                  onClick={onLogin}
                  style={{ marginTop: 28, width: "100%", height: 48, border: "none", borderRadius: "var(--r-md)", background: "#5865F2", color: "#fff", fontSize: 15, fontWeight: 700, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 10 }}
                >
                  <DiscordMark />
                  {t("login.withDiscord")}
                </button>
                {onViewDemo && (
                  <button
                    type="button"
                    onClick={onViewDemo}
                    className="wh-btn wh-btn--ghost"
                    style={{
                      marginTop: 10,
                      width: "100%",
                      height: 40,
                      fontSize: 13,
                      color: "var(--text-muted)",
                      fontWeight: 600,
                      cursor: "pointer",
                      background: "transparent",
                      border: "1px solid var(--border-2)",
                      borderRadius: "var(--r-md)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      gap: 8,
                    }}
                  >
                    <Eye size={15} />
                    {t("login.viewDemo")}
                  </button>
                )}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
        <p style={{ margin: "18px 0 0", fontSize: 12, color: "var(--text-muted)", lineHeight: 1.7 }}>{t("login.note")}</p>
      </motion.div>
    </motion.div>
  );
}

export function LoadingScreen() {
  const { t } = useTranslation();
  return (
    <div style={centered}>
      <img src="/logo.svg" alt="" width={56} height={56} />
      <span style={{ width: 26, height: 26, borderRadius: "50%", border: "3px solid var(--border-2)", borderTopColor: "var(--moon-400)", animation: "wh-spin 0.9s linear infinite" }} />
      <span style={{ fontSize: 13, color: "var(--text-muted)" }}>{t("connection.connecting")}</span>
    </div>
  );
}

export function ServerSelectScreen({
  servers,
  loading,
  error,
  onPick,
  onRetry,
  onBack,
}: {
  servers: SessionSummary[];
  loading: boolean;
  error: boolean;
  onPick: (guildId: string) => void;
  onRetry: () => void;
  onBack: () => void;
}) {
  const { t } = useTranslation();

  const containerVariants = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: {
        staggerChildren: 0.02,
        delayChildren: 0.02
      }
    },
    exit: {
      opacity: 0,
      transition: { duration: 0.15, ease: "easeInOut" }
    }
  };

  const itemVariants = {
    hidden: { y: 8, opacity: 0 },
    show: { y: 0, opacity: 1, transition: { duration: 0.18, ease: "easeOut" } }
  };

  return (
    <motion.div
      variants={containerVariants}
      initial="hidden"
      animate="show"
      exit="exit"
      style={centered}
    >
      <div style={{ width: "100%", maxWidth: 460, display: "flex", flexDirection: "column", gap: 12 }}>
        <motion.h1
          variants={itemVariants}
          style={{ margin: "0 0 4px", fontSize: 22, fontWeight: 900 }}
        >
          {t("servers.title")}
        </motion.h1>

        {loading && (
          <motion.span
            variants={itemVariants}
            style={{ fontSize: 12, color: "var(--text-muted)", textAlign: "center" }}
          >
            {t("servers.loadingList")}
          </motion.span>
        )}

        {error && (
          <motion.div
            variants={itemVariants}
            style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12, padding: 28, borderRadius: "var(--r-lg)", background: "var(--danger-dim)", border: "1px solid rgba(255,92,110,0.35)" }}
          >
            <span style={{ fontWeight: 700, color: "var(--danger-500)" }}>⚠ {t("servers.errorTitle")}</span>
            <span style={{ fontSize: 12, color: "var(--text-secondary)" }}>{t("servers.errorHint")}</span>
            <Button size="sm" onClick={onRetry}>{t("common.retry")}</Button>
          </motion.div>
        )}

        {!loading && !error && servers.length === 0 && (
          <motion.div
            variants={itemVariants}
            style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 10, padding: 32, borderRadius: "var(--r-lg)", background: "var(--surface-card)", border: "1px dashed var(--border-2)" }}
          >
            <span style={{ fontWeight: 700 }}>{t("servers.emptyTitle")}</span>
            <span style={{ fontSize: 12, color: "var(--text-muted)", textAlign: "center", lineHeight: 1.7 }}>{t("servers.emptyHint")}</span>
          </motion.div>
        )}

        {!loading && !error &&
          servers.map((sv) => (
            <motion.button
              variants={itemVariants}
              key={sv.guildId}
              type="button"
              onClick={() => onPick(sv.guildId)}
              className="wh-card"
              style={{ display: "flex", alignItems: "center", gap: 16, width: "100%", padding: 18, cursor: "pointer", textAlign: "left", fontFamily: "var(--font-ui)", transition: "transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out), border-color var(--dur-fast) var(--ease-out)" }}
              whileHover={{ scale: 1.02, boxShadow: "var(--shadow-2)", borderColor: "var(--border-2)" }}
              whileTap={{ scale: 0.99 }}
            >
              <span style={{ width: 52, height: 52, flex: "none", borderRadius: "var(--r-full)", background: "var(--surface-raised)", border: "1px solid var(--border-2)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 20, fontWeight: 700, color: "var(--text-secondary)", overflow: "hidden" }}>
                {sv.guildIcon ? (
                  <img src={sv.guildIcon} alt="" style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                ) : (
                  sv.guildName.trim()[0] ?? "?"
                )}
              </span>
              <span style={{ flex: 1, minWidth: 0 }}>
                <span style={{ display: "block", fontSize: 16, fontWeight: 700 }}>{sv.guildName}</span>
                <span className="mono" style={{ display: "block", fontSize: 13, color: "var(--text-muted)", marginTop: 2 }}>
                  {t("servers.players", { count: sv.playerCount })}
                </span>
              </span>
              <span style={{ color: "var(--text-muted)", fontSize: 18 }}>→</span>
            </motion.button>
          ))}

        <motion.div variants={itemVariants}>
          <Button variant="ghost" size="sm" onClick={onBack} style={{ alignSelf: "flex-start", marginTop: 6 }}>
            {t("servers.backToLogin")}
          </Button>
        </motion.div>
      </div>
    </motion.div>
  );
}

export function MessageScreen({ icon, title, body, children }: { icon: React.ReactNode; title: string; body: string; children?: React.ReactNode }) {
  return (
    <div style={centered}>
      <div style={{ ...card, width: "100%", maxWidth: 440, boxShadow: "var(--shadow-2)" }}>
        <span style={{ color: "var(--warning-500)" }}>{icon}</span>
        <h1 style={{ margin: 0, fontSize: 20, fontWeight: 900 }}>{title}</h1>
        <p style={{ margin: 0, fontSize: 13, color: "var(--text-secondary)", lineHeight: 1.8 }}>{body}</p>
        {children}
      </div>
    </div>
  );
}
