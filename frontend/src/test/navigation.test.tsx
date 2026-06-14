import { describe, expect, it, vi, beforeEach } from "vitest";
import { useEffect } from "react";
import { createRoot } from "react-dom/client";
import { act } from "react";
import { MemoryRouter, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/stores/authStore";
import { useGameStore } from "@/stores/gameStore";
import { GameSocket } from "@/api/ws";
import { api } from "@/api/client";

// Mock API calls
vi.mock("@/api/client", () => ({
  api: {
    me: vi.fn(),
    state: vi.fn(),
    listSessions: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    constructor(message: string, public status: number) {
      super(message);
    }
  },
}));

// Mock Lucide icons to avoid importing issues in test environment
vi.mock("lucide-react", () => ({
  Moon: () => null,
  Sun: () => null,
  LayoutDashboard: () => null,
  Mic: () => null,
  Eye: () => null,
  Settings: () => null,
  Server: () => null,
  LogOut: () => null,
  WifiOff: () => null,
  ArrowLeft: () => null,
  Shield: () => null,
  LogOutIcon: () => null,
  WifiOffIcon: () => null,
  Menu: () => null,
  X: () => null,
}));

// Spy on GameSocket
const connectSpy = vi.spyOn(GameSocket.prototype, "connect").mockImplementation(() => {});
const closeSpy = vi.spyOn(GameSocket.prototype, "close").mockImplementation(() => {});

// We import App
import { App } from "@/App";

let testNavigate: any = null;
function NavigationHelper() {
  const navigate = useNavigate();
  useEffect(() => {
    testNavigate = navigate;
  }, [navigate]);
  return null;
}

describe("Navigation and WebSocket Lifecycle", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    testNavigate = null;
    useAuthStore.getState().setAuth({ userId: "1", username: "TestUser", role: "JUDGE", guildId: "123", avatar: null });
    useAuthStore.getState().setDemo(false);
    useGameStore.getState().setConnected(true);
  });

  it("should not close and recreate websocket when navigating between subpages of the same guild", async () => {
    vi.mocked(api.me).mockResolvedValue({ userId: "1", username: "TestUser", role: "JUDGE", guildId: "123", avatar: null });
    vi.mocked(api.state).mockResolvedValue({
      assigned: false,
      seats: [],
      stage: "NIGHT",
      log: [],
    } as any);

    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    // Render App wrapped in MemoryRouter starting at dashboard
    await act(async () => {
      root.render(
        <MemoryRouter initialEntries={["/server/123/dashboard"]}>
          <App />
          <NavigationHelper />
        </MemoryRouter>
      );
    });

    // Check that websocket connect was called
    expect(connectSpy).toHaveBeenCalledTimes(1);
    expect(closeSpy).not.toHaveBeenCalled();

    // Now navigate to /server/123/speech
    await act(async () => {
      testNavigate("/server/123/speech");
    });

    // Check if websocket is closed
    expect(closeSpy).not.toHaveBeenCalled();

    // Cleanup
    await act(async () => {
      root.unmount();
    });
    document.body.removeChild(container);
  });
});
