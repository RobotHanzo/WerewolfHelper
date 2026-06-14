import { create } from "zustand";

export type Density = "comfort" | "compact" | "list";

interface KillTarget {
  seat: number;
  identityIndex: number;
  identityName: string;
}

interface UiState {
  density: Density;
  spectatorPreview: boolean;
  sidebarOpen: boolean;

  // overlays
  killTarget: KillTarget | null;
  editSeat: number | null;
  timerOpen: boolean;
  picker: { kind: "promote" | "demote" | "force-police"; title: string } | null;
  toast: { text: string; isError?: boolean } | null;

  setDensity: (density: Density) => void;
  toggleSpectatorPreview: () => void;
  setSidebarOpen: (open: boolean) => void;
  toggleSidebar: () => void;
  openKill: (target: KillTarget) => void;
  closeKill: () => void;
  openEdit: (seat: number) => void;
  closeEdit: () => void;
  setTimerOpen: (open: boolean) => void;
  openPicker: (kind: "promote" | "demote" | "force-police", title: string) => void;
  closePicker: () => void;
  showToast: (text: string, isError?: boolean) => void;
}

export const useUiStore = create<UiState>((set) => ({
  density: "comfort",
  spectatorPreview: false,
  sidebarOpen: false,
  killTarget: null,
  editSeat: null,
  timerOpen: false,
  picker: null,
  toast: null,

  setDensity: (density) => set({ density }),
  toggleSpectatorPreview: () => set((s) => ({ spectatorPreview: !s.spectatorPreview })),
  setSidebarOpen: (sidebarOpen) => set({ sidebarOpen }),
  toggleSidebar: () => set((s) => ({ sidebarOpen: !s.sidebarOpen })),
  openKill: (killTarget) => set({ killTarget }),
  closeKill: () => set({ killTarget: null }),
  openEdit: (editSeat) => set({ editSeat }),
  closeEdit: () => set({ editSeat: null }),
  setTimerOpen: (timerOpen) => set({ timerOpen }),
  openPicker: (kind, title) => set({ picker: { kind, title } }),
  closePicker: () => set({ picker: null }),
  showToast: (text, isError) => {
    const toastObj = { text, isError };
    set({ toast: toastObj });
    setTimeout(() => set((s) => (s.toast === toastObj ? { toast: null } : s)), 2600);
  },
}));
