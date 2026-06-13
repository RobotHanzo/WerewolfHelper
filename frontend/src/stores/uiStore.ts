import { create } from "zustand";

export type Density = "comfort" | "compact" | "list";
export type Screen = "dashboard" | "speech" | "spectator" | "settings";

interface KillTarget {
  seat: number;
  identityIndex: number;
  identityName: string;
}

interface UiState {
  screen: Screen;
  density: Density;
  spectatorPreview: boolean;

  // overlays
  killTarget: KillTarget | null;
  editSeat: number | null;
  timerOpen: boolean;
  picker: { kind: "promote" | "demote" | "force-police"; title: string } | null;
  toast: string | null;

  setScreen: (screen: Screen) => void;
  setDensity: (density: Density) => void;
  toggleSpectatorPreview: () => void;
  openKill: (target: KillTarget) => void;
  closeKill: () => void;
  openEdit: (seat: number) => void;
  closeEdit: () => void;
  setTimerOpen: (open: boolean) => void;
  openPicker: (kind: "promote" | "demote" | "force-police", title: string) => void;
  closePicker: () => void;
  showToast: (text: string) => void;
}

export const useUiStore = create<UiState>((set) => ({
  screen: "dashboard",
  density: "comfort",
  spectatorPreview: false,
  killTarget: null,
  editSeat: null,
  timerOpen: false,
  picker: null,
  toast: null,

  setScreen: (screen) => set({ screen }),
  setDensity: (density) => set({ density }),
  toggleSpectatorPreview: () => set((s) => ({ spectatorPreview: !s.spectatorPreview })),
  openKill: (killTarget) => set({ killTarget }),
  closeKill: () => set({ killTarget: null }),
  openEdit: (editSeat) => set({ editSeat }),
  closeEdit: () => set({ editSeat: null }),
  setTimerOpen: (timerOpen) => set({ timerOpen }),
  openPicker: (kind, title) => set({ picker: { kind, title } }),
  closePicker: () => set({ picker: null }),
  showToast: (toast) => {
    set({ toast });
    setTimeout(() => set((s) => (s.toast === toast ? { toast: null } : s)), 2600);
  },
}));
