import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath, URL } from "node:url";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // `xfwd` adds X-Forwarded-Host/Proto so the backend can resolve the browser-facing origin
      // (e.g. a LAN/Tailscale host) instead of the proxy target — required for OAuth redirects.
      "/api": { target: "http://localhost:8080", changeOrigin: true, xfwd: true },
      "/ws": { target: "ws://localhost:8080", ws: true, xfwd: true },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
  },
});
