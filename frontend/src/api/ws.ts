import type { GameSnapshot, LogSeverity } from "@/types/snapshot";

export const WS_HEARTBEAT_MS = 15_000;
export const WS_BACKOFF_START_MS = 1_000;
export const WS_BACKOFF_FACTOR = 1.5;
export const WS_BACKOFF_CAP_MS = 10_000;

/** Close code the server uses to signal an expired/rejected session (vs a network blip). */
export const WS_CLOSE_SESSION_EXPIRED = 4001;

/** Pure backoff step: 1s → ×1.5 → capped at 10s. Exported for unit testing. */
export function nextBackoff(current: number): number {
  if (current <= 0) return WS_BACKOFF_START_MS;
  return Math.min(Math.round(current * WS_BACKOFF_FACTOR), WS_BACKOFF_CAP_MS);
}

export interface ProgressEvent {
  percent: number;
  line: string;
  severity: LogSeverity;
}

export interface GameSocketHandlers {
  onSnapshot: (snapshot: GameSnapshot) => void;
  onProgress?: (event: ProgressEvent) => void;
  onConnected: (connected: boolean) => void;
  /** Session expired / rejected — pop the re-login modal instead of reconnect-looping. */
  onExpired: () => void;
}

/**
 * Per-guild WebSocket client. Heartbeats every 15 s (idle proxies kill quiet sockets), reconnects
 * with exponential backoff, and distinguishes an expired session (→ re-login) from a network blip
 * (→ reconnect). Snapshot messages replace the store wholesale (snapshot-as-truth).
 */
export class GameSocket {
  private ws: WebSocket | null = null;
  private heartbeat: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private backoff = 0;
  private closedByUser = false;

  constructor(
    private readonly guildId: string,
    private readonly handlers: GameSocketHandlers,
  ) {}

  connect(): void {
    this.closedByUser = false;
    const proto = location.protocol === "https:" ? "wss" : "ws";
    const ws = new WebSocket(`${proto}://${location.host}/ws?guildId=${this.guildId}`);
    this.ws = ws;

    ws.onopen = () => {
      this.backoff = 0;
      this.handlers.onConnected(true);
      this.startHeartbeat();
    };

    ws.onmessage = (event) => {
      let msg: { type?: string; snapshot?: GameSnapshot; percent?: number; line?: string; severity?: LogSeverity };
      try {
        msg = JSON.parse(event.data as string);
      } catch {
        return;
      }
      if (msg.type === "snapshot" && msg.snapshot) {
        this.handlers.onSnapshot(msg.snapshot);
      } else if (msg.type === "progress" && msg.percent != null && msg.line != null) {
        this.handlers.onProgress?.({ percent: msg.percent, line: msg.line, severity: msg.severity ?? "info" });
      }
    };

    ws.onclose = (event) => {
      this.stopHeartbeat();
      this.handlers.onConnected(false);
      if (this.closedByUser) return;
      if (event.code === WS_CLOSE_SESSION_EXPIRED) {
        this.handlers.onExpired();
        return;
      }
      this.scheduleReconnect();
    };

    ws.onerror = () => {
      // Browser will automatically trigger onclose when the connection fails.
      // Do NOT call ws.close() here, as doing so during the connection/handshake
      // phase triggers "WebSocket is closed before the connection is established" in the console.
    };
  }

  close(): void {
    this.closedByUser = true;
    this.stopHeartbeat();
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.ws?.close();
    this.ws = null;
  }

  private scheduleReconnect(): void {
    this.backoff = nextBackoff(this.backoff);
    this.reconnectTimer = setTimeout(() => this.connect(), this.backoff);
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeat = setInterval(() => {
      if (this.ws?.readyState === WebSocket.OPEN) this.ws.send(JSON.stringify({ type: "ping" }));
    }, WS_HEARTBEAT_MS);
  }

  private stopHeartbeat(): void {
    if (this.heartbeat) {
      clearInterval(this.heartbeat);
      this.heartbeat = null;
    }
  }
}
