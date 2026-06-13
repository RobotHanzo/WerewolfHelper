import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { OctagonX, Eye } from "lucide-react";
import { api } from "@/api/client";
import { GameSocket } from "@/api/ws";
import { useAuthStore } from "@/stores/authStore";
import { useGameStore } from "@/stores/gameStore";
import { buildScenario } from "@/mock/scenarios";
import type { SessionSummary } from "@/types/snapshot";
import { LoadingScreen, LoginScreen, MessageScreen, ServerSelectScreen } from "@/screens/EntryScreens";
import { AppShell } from "@/screens/AppShell";
import { Button } from "@/components/ui/Button";

export function App() {
  const { t } = useTranslation();
  const status = useAuthStore((s) => s.status);
  const guildId = useAuthStore((s) => s.guildId);
  const demo = useAuthStore((s) => s.demo);

  const [servers, setServers] = useState<SessionSummary[]>([]);
  const [serversState, setServersState] = useState<"loading" | "error" | "ok">("loading");

  // --- bootstrap: try the live backend; fall back to a seeded demo so the design is always viewable.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const me = await api.me();
        if (cancelled) return;
        useAuthStore.getState().setAuth(me);
        if (me.role === "BLOCKED") return useAuthStore.getState().setStatus("blocked");
        useAuthStore.getState().setStatus("servers");
      } catch {
        if (cancelled) return;
        enterDemo();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const loadServers = () => {
    setServersState("loading");
    api
      .listSessions()
      .then((list) => {
        setServers(list);
        setServersState("ok");
      })
      .catch(() => setServersState("error"));
  };

  useEffect(() => {
    if (status === "servers" && !demo) loadServers();
  }, [status, demo]);

  if (status === "loading") return <LoadingScreen />;
  if (status === "login") return <LoginScreen onLogin={() => (window.location.href = api.loginUrl())} />;
  if (status === "blocked") return <MessageScreen icon={<OctagonX size={40} />} title={t("blocked.title")} body={t("blocked.body")} />;
  if (status === "lockout")
    return (
      <MessageScreen icon={<Eye size={40} />} title={t("lockout.title")} body={t("lockout.body")}>
        <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
          <Button size="sm" onClick={() => window.location.reload()}>{t("common.refresh")}</Button>
        </div>
      </MessageScreen>
    );
  if (status === "servers")
    return (
      <ServerSelectScreen
        servers={servers}
        loading={serversState === "loading"}
        error={serversState === "error"}
        onPick={(g) => useAuthStore.getState().selectGuild(g)}
        onRetry={loadServers}
        onBack={() => useAuthStore.getState().setStatus("login")}
      />
    );

  // ready
  return <GameSurface guildId={guildId!} demo={demo} />;
}

/** Seed the store with demo data and enter the dashboard as a judge. */
function enterDemo() {
  useGameStore.getState().applySnapshot(buildScenario("night"));
  useGameStore.getState().setConnected(true);
  useAuthStore.getState().setAuth({ userId: "0", username: "示範法官", avatar: null, role: "JUDGE", guildId: "demo" });
  useAuthStore.getState().setDemo(true);
  useAuthStore.getState().selectGuild("demo");
}

/** Connects the live WebSocket (or relies on seeded demo data) and renders the shell. */
function GameSurface({ guildId, demo }: { guildId: string; demo: boolean }) {
  const socketRef = useRef<GameSocket | null>(null);

  useEffect(() => {
    if (demo) return;
    api.state(guildId).then(useGameStore.getState().applySnapshot).catch(() => {});
    const socket = new GameSocket(guildId, {
      onSnapshot: useGameStore.getState().applySnapshot,
      onProgress: useGameStore.getState().pushProgress,
      onConnected: useGameStore.getState().setConnected,
      onExpired: () => useGameStore.getState().setExpired(true),
    });
    socket.connect();
    socketRef.current = socket;
    return () => socket.close();
  }, [guildId, demo]);

  return <AppShell guildId={guildId} demo={demo} />;
}
