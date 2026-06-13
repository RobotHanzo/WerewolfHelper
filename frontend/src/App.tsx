import { useEffect, useRef, useState } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { OctagonX, Eye } from "lucide-react";

import { api, ApiError } from "@/api/client";
import { GameSocket } from "@/api/ws";
import { useAuthStore } from "@/stores/authStore";
import { useGameStore } from "@/stores/gameStore";
import { buildScenario } from "@/mock/scenarios";
import type { SessionSummary } from "@/types/snapshot";
import { LoadingScreen, LoginScreen, MessageScreen, ServerSelectScreen } from "@/screens/EntryScreens";
import { AppShell } from "@/screens/AppShell";
import { Dashboard } from "@/screens/Dashboard";
import { SpeechManager } from "@/screens/SpeechManager";
import { Spectator } from "@/screens/Spectator";
import { Settings } from "@/screens/Settings";
import { Button } from "@/components/ui/Button";

/** Seed demo data and mark demo mode (no live backend reachable). */
function enterDemo() {
  useGameStore.getState().applySnapshot(buildScenario("night"));
  useGameStore.getState().setConnected(true);
  useAuthStore.getState().setAuth({ userId: "0", username: "示範法官", avatar: null, role: "JUDGE", guildId: "demo" });
  useAuthStore.getState().setDemo(true);
}

export function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const booted = useRef(false);

  // One-time bootstrap: establish the session, then route. Deep links keep their path.
  useEffect(() => {
    if (booted.current) return;
    booted.current = true;
    (async () => {
      try {
        const me = await api.me();
        useAuthStore.getState().setAuth(me);
        if (me.role === "BLOCKED") return navigate("/blocked", { replace: true });
        if (location.pathname === "/") navigate("/servers", { replace: true });
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) {
          return navigate("/login", { replace: true });
        }
        enterDemo();
        if (!location.pathname.startsWith("/server/")) navigate("/server/demo/dashboard", { replace: true });
      }
    })();
  }, [navigate, location.pathname]);

  return (
    <Routes>
      <Route path="/" element={<LoadingScreen />} />
      <Route path="/login" element={<LoginScreen onLogin={() => (window.location.href = api.loginUrl())} />} />
      <Route path="/servers" element={<ServersRoute />} />
      <Route path="/lockout" element={<LockoutRoute />} />
      <Route path="/blocked" element={<BlockedRoute />} />
      <Route path="/server/:guildId" element={<ServerSurface />}>
        <Route index element={<Navigate to="dashboard" replace />} />
        <Route path="dashboard" element={<JudgeRoute><Dashboard /></JudgeRoute>} />
        <Route path="speech" element={<SpeechManager />} />
        <Route path="spectator" element={<Spectator />} />
        <Route path="settings" element={<JudgeRoute><Settings /></JudgeRoute>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

/** Connects the live WebSocket for the routed guild (or relies on seeded demo data), then the shell. */
function ServerSurface() {
  const { guildId } = useParams();
  const demo = useAuthStore((s) => s.demo);
  const navigate = useNavigate();
  const snapshot = useGameStore((s) => s.snapshot);
  const userId = useAuthStore((s) => s.auth?.userId);

  useEffect(() => {
    if (demo || !guildId) return;

    // Set role to PENDING for this guild while we fetch to avoid flash of previous server's role/screens
    useAuthStore.setState((s) => {
      if (s.auth && (s.auth.guildId !== guildId || s.auth.role !== "PENDING")) {
        return {
          auth: {
            ...s.auth,
            role: "PENDING",
            guildId,
          }
        };
      }
      return {};
    });

    api.me(guildId)
      .then((me) => {
        useAuthStore.getState().setAuth(me);
        if (me.role === "BLOCKED") {
          navigate("/blocked", { replace: true });
        }
      })
      .catch(() => {
        // If query fails, we keep the current state or handle it appropriately.
      });
  }, [guildId, demo]);

  useEffect(() => {
    if (demo || !snapshot || !userId) return;
    if (snapshot.assigned && snapshot.seats.some((seat) => seat.memberId === userId)) {
      navigate("/lockout", { replace: true });
    }
  }, [snapshot, userId, demo]);

  useEffect(() => {
    if (!guildId) return;
    if (demo) {
      if (!useGameStore.getState().snapshot) useGameStore.getState().applySnapshot(buildScenario("night"));
      useGameStore.getState().setConnected(true);
      return;
    }
    api.state(guildId)
      .then(useGameStore.getState().applySnapshot)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 403) {
          if (err.message === "入座玩家無法進入主控台") {
            navigate("/lockout", { replace: true });
          } else {
            navigate("/blocked", { replace: true });
          }
        }
      });
    const socket = new GameSocket(guildId, {
      onSnapshot: useGameStore.getState().applySnapshot,
      onProgress: useGameStore.getState().pushProgress,
      onConnected: useGameStore.getState().setConnected,
      onExpired: () => useGameStore.getState().setExpired(true),
    });
    socket.connect();
    return () => {
      socket.close();
    };
  }, [guildId, demo]);

  return <AppShell />;
}

/** Redirect spectators away from judge-only screens to the God's view. */
function JudgeRoute({ children }: { children: React.ReactNode }) {
  const { guildId } = useParams();
  const role = useAuthStore((s) => s.auth?.role);
  if (!role || role === "PENDING") {
    return <LoadingScreen />;
  }
  const isJudge = role === "JUDGE";
  if (!isJudge) return <Navigate to={`/server/${guildId}/spectator`} replace />;
  return <>{children}</>;
}

function ServersRoute() {
  const navigate = useNavigate();
  const [servers, setServers] = useState<SessionSummary[]>([]);
  const [state, setState] = useState<"loading" | "error" | "ok">("loading");
  const load = () => {
    setState("loading");
    api.listSessions()
      .then((l) => {
        setServers(l);
        setState("ok");
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 401) {
          navigate("/login", { replace: true });
        } else {
          setState("error");
        }
      });
  };
  useEffect(load, []);
  return (
    <ServerSelectScreen
      servers={servers}
      loading={state === "loading"}
      error={state === "error"}
      onPick={(g) => navigate(`/server/${g}/dashboard`)}
      onRetry={load}
      onBack={() => navigate("/login")}
    />
  );
}

function LockoutRoute() {
  const { t } = useTranslation();
  return (
    <MessageScreen icon={<Eye size={40} />} title={t("lockout.title")} body={t("lockout.body")}>
      <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
        <Button size="sm" onClick={() => window.location.reload()}>{t("common.refresh")}</Button>
      </div>
    </MessageScreen>
  );
}

function BlockedRoute() {
  const { t } = useTranslation();
  return <MessageScreen icon={<OctagonX size={40} />} title={t("blocked.title")} body={t("blocked.body")} />;
}
