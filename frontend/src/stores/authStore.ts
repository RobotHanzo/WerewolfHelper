import { create } from "zustand";
import type { AuthInfo } from "@/types/snapshot";

interface AuthState {
  auth: AuthInfo | null;
  /** True when running against seeded mock data (no live backend reachable). */
  demo: boolean;
  setAuth: (auth: AuthInfo | null) => void;
  setDemo: (demo: boolean) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  auth: null,
  demo: false,
  setAuth: (auth) => set({ auth }),
  setDemo: (demo) => set({ demo }),
}));
