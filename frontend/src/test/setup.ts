// Vitest global setup. jsdom environment is configured in vite.config.ts.
import { afterEach } from "vitest";

afterEach(() => {
  // Keep tests isolated; individual suites manage their own fixtures.
});
