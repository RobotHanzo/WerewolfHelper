import { create } from "zustand";
import type { AuthInfo } from "@/types/snapshot";

export type AppStatus =
  | "loading"
  | "login"
  | "servers"
  | "lockout"
  | "blocked"
  | "ready";

interface AuthState {
  status: AppStatus;
  auth: AuthInfo | null;
  guildId: string | null;
  /** True when running against seeded mock data (no live backend reachable). */
  demo: boolean;
  setStatus: (status: AppStatus) => void;
  setAuth: (auth: AuthInfo | null) => void;
  setDemo: (demo: boolean) => void;
  selectGuild: (guildId: string) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  status: "loading",
  auth: null,
  guildId: null,
  demo: false,
  setStatus: (status) => set({ status }),
  setAuth: (auth) => set({ auth }),
  setDemo: (demo) => set({ demo }),
  selectGuild: (guildId) => set({ guildId, status: "ready" }),
}));
