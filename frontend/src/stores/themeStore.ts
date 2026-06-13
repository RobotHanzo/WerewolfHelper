import { create } from "zustand";

export type Theme = "dark" | "light";

const STORAGE_KEY = "wh-theme";

function systemPreference(): Theme {
  if (typeof window !== "undefined" && window.matchMedia) {
    return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
  }
  return "dark";
}

function initialTheme(): Theme {
  if (typeof localStorage !== "undefined") {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "dark" || stored === "light") return stored;
  }
  return systemPreference();
}

function applyTheme(theme: Theme) {
  if (typeof document !== "undefined") {
    document.documentElement.setAttribute("data-theme", theme);
  }
}

interface ThemeState {
  theme: Theme;
  toggle: () => void;
  set: (theme: Theme) => void;
}

/**
 * Theme is user-switchable, persisted, and defaults to the system preference.
 * Night ("dark") is the primary experience.
 */
export const useThemeStore = create<ThemeState>((set, get) => {
  const theme = initialTheme();
  applyTheme(theme);
  return {
    theme,
    toggle: () => get().set(get().theme === "dark" ? "light" : "dark"),
    set: (theme: Theme) => {
      applyTheme(theme);
      if (typeof localStorage !== "undefined") localStorage.setItem(STORAGE_KEY, theme);
      set({ theme });
    },
  };
});
