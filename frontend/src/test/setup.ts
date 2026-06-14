// Vitest global setup. jsdom environment is configured in vite.config.ts.
import { afterEach, vi } from "vitest";

// jsdom has no matchMedia; useMediaQuery (responsive roster) needs it.
if (!window.matchMedia) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

afterEach(() => {
  // Keep tests isolated; individual suites manage their own fixtures.
});
