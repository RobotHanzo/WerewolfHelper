import { useTranslation } from "react-i18next";
import { NavLink, Outlet, useNavigate, useLocation } from "react-router-dom";
import { Moon, Sun, LayoutDashboard, Mic, Eye, Settings as SettingsIcon, Server, LogOut, WifiOff } from "lucide-react";
import { motion } from "framer-motion";
import { api } from "@/api/client";
import { useAuthStore } from "@/stores/authStore";
import { useGameStore } from "@/stores/gameStore";
import { useUiStore } from "@/stores/uiStore";
import { useThemeStore } from "@/stores/themeStore";
import { useGuild } from "@/hooks/useGuild";
import { LiveIndicator } from "@/components/ui/LiveIndicator";
import { Avatar } from "@/components/ui/Avatar";
import { Overlays } from "./Overlays";

export function AppShell() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { guildId, demo, isJudge } = useGuild();
  const auth = useAuthStore((s) => s.auth);
  const connected = useGameStore((s) => s.connected);
  const unread = useGameStore((s) => s.unreadLogs);
  const spectatorPreview = useUiStore((s) => s.spectatorPreview);
  const togglePreview = useUiStore((s) => s.toggleSpectatorPreview);
  const theme = useThemeStore((s) => s.theme);
  const toggleTheme = useThemeStore((s) => s.toggle);

  const base = `/server/${guildId}`;
  const nav = isJudge
    ? [
        { to: `${base}/dashboard`, label: t("nav.dashboard"), icon: <LayoutDashboard size={16} />, badge: unread || undefined },
        { to: `${base}/speech`, label: t("nav.speech"), icon: <Mic size={16} /> },
        { to: `${base}/spectator`, label: t("nav.spectator"), icon: <Eye size={16} /> },
        { to: `${base}/settings`, label: t("nav.settings"), icon: <SettingsIcon size={16} /> },
      ]
    : [
        { to: `${base}/spectator`, label: t("nav.spectator"), icon: <Eye size={16} /> },
        { to: `${base}/speech`, label: t("nav.speech"), icon: <Mic size={16} /> },
      ];

  const signOut = () => {
    if (!demo) void api.logout();
    useAuthStore.setState({ auth: null, demo: false });
    navigate("/login");
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.18, ease: "easeOut" }}
      style={{ display: "flex", minHeight: "100vh", background: "var(--surface-app)" }}
    >
      <motion.aside
        initial={{ x: -16, opacity: 0 }}
        animate={{ x: 0, opacity: 1 }}
        exit={{ x: -16, opacity: 0 }}
        transition={{ duration: 0.22, ease: "easeOut" }}
        style={{ width: 224, flex: "none", display: "flex", flexDirection: "column", gap: 8, padding: 16, borderRight: "1px solid var(--border-1)", background: "var(--surface-card)", position: "sticky", top: 0, height: "100vh", boxSizing: "border-box" }}
      >
        <div style={{ padding: "4px 4px 16px", display: "flex", alignItems: "center", gap: 10 }}>
          <img src="/logo.svg" alt="" width={34} height={34} />
          <span style={{ display: "flex", flexDirection: "column", lineHeight: 1.2 }}>
            <strong style={{ fontSize: 15, fontWeight: 900, letterSpacing: "0.04em" }}>{t("app.name")}</strong>
            <span className="mono" style={{ fontSize: 8.5, color: "var(--moon-400)", letterSpacing: "0.2em" }}>{t("app.wordmark")}</span>
          </span>
        </div>

        {nav.map((n) => (
          <NavLink
            key={n.to}
            to={n.to}
            style={({ isActive }) => ({
              display: "flex", alignItems: "center", gap: 10, height: 40, padding: "0 12px", borderRadius: "var(--r-md)",
              textDecoration: "none", fontSize: 14, fontWeight: isActive ? 700 : 500,
              background: isActive ? "var(--accent-soft)" : "transparent",
              color: isActive ? "var(--moon-300)" : "var(--text-secondary)",
            })}
          >
            {n.icon}
            <span style={{ flex: 1, textAlign: "left" }}>{n.label}</span>
            {n.badge && (
              <span className="mono" style={{ fontSize: 10, fontWeight: 700, color: "var(--text-on-accent)", background: "var(--moon-500)", borderRadius: "var(--r-full)", padding: "1px 7px" }}>{n.badge}</span>
            )}
          </NavLink>
        ))}

        <div style={{ marginTop: "auto", display: "flex", flexDirection: "column", gap: 12 }}>
          <LiveIndicator connected={connected} />
          <div style={{ display: "flex", alignItems: "center", gap: 10, padding: 10, borderRadius: "var(--r-md)", background: "var(--surface-app)", border: "1px solid var(--border-1)" }}>
            <Avatar size="sm" name={auth?.username ?? "?"} avatar={auth?.avatar} />
            <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
              <span style={{ fontSize: 13, fontWeight: 700 }}>{auth?.username ?? "—"}</span>
              <span style={{ fontSize: 11, color: isJudge ? "var(--moon-400)" : "var(--text-muted)" }}>
                {spectatorPreview ? t("role.judgePreviewing") : isJudge ? t("role.judge") : t("role.spectator")}
              </span>
            </div>
          </div>
          {isJudge && (
            <button onClick={togglePreview} style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 6, height: 32, border: "1px solid var(--border-1)", borderRadius: "var(--r-md)", background: spectatorPreview ? "var(--accent-soft)" : "transparent", color: "var(--text-secondary)", fontSize: 12, cursor: "pointer" }}>
              <Eye size={14} /> {spectatorPreview ? t("nav.previewOn") : t("nav.previewOff")}
            </button>
          )}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6 }}>
            <button onClick={toggleTheme} style={iconBtn}>
              {theme === "dark" ? <Sun size={13} /> : <Moon size={13} />}
              {theme === "dark" ? t("nav.themeLight") : t("nav.themeDark")}
            </button>
            <button onClick={() => navigate("/servers")} style={iconBtn}>
              <Server size={13} /> {t("nav.switchServer")}
            </button>
          </div>
          <button onClick={signOut} style={{ ...iconBtn, border: "none", color: "var(--text-muted)" }}>
            <LogOut size={13} /> {t("common.signOut")}
          </button>
        </div>
      </motion.aside>

      <motion.main
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: 8 }}
        transition={{ duration: 0.22, ease: "easeOut" }}
        style={{ flex: 1, minWidth: 0, padding: "20px 24px 80px", boxSizing: "border-box" }}
      >
        {!connected && (
          <div className="wh-urgent" style={{ display: "flex", alignItems: "center", gap: 10, padding: "10px 16px", margin: "0 auto 14px", maxWidth: 1180, borderRadius: "var(--r-md)", background: "var(--danger-dim)", border: "1px solid rgba(255,92,110,0.4)" }}>
            <WifiOff size={16} />
            <span style={{ fontSize: 13, fontWeight: 700, color: "var(--danger-500)" }}>{t("connection.disconnectedBanner")}</span>
          </div>
        )}
        <motion.div
          key={location.pathname}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.22, ease: "easeOut" }}
          style={{ width: "100%" }}
        >
          <Outlet />
        </motion.div>
      </motion.main>

      <Overlays guildId={guildId} demo={demo} />
    </motion.div>
  );
}

const iconBtn: React.CSSProperties = {
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  gap: 6,
  height: 32,
  border: "1px solid var(--border-1)",
  borderRadius: "var(--r-md)",
  background: "transparent",
  color: "var(--text-secondary)",
  fontSize: 12,
  fontFamily: "var(--font-ui)",
  cursor: "pointer",
};
