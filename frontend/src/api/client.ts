import type { AuthInfo, GameSnapshot, RoleInfo, SessionSummary } from "@/types/snapshot";

/** The backend's response envelope. Typed responses additionally carry `data`. */
interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  error?: string;
  data?: T;
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
    ...init,
  });
  let body: ApiEnvelope<T> | null = null;
  try {
    body = (await res.json()) as ApiEnvelope<T>;
  } catch {
    // empty body
  }
  if (!res.ok || (body && body.success === false)) {
    throw new ApiError(body?.error ?? `HTTP ${res.status}`, res.status);
  }
  return (body?.data as T) ?? (undefined as T);
}

const post = <T = void>(path: string, body?: unknown) =>
  request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined });

/** Typed API surface, mirroring the backend controllers. */
export const api = {
  // auth
  me: (guildId?: string) => request<AuthInfo>(`/auth/me${guildId ? `?guildId=${guildId}` : ""}`),
  logout: () => post("/auth/logout"),
  loginUrl: () => "/api/auth/login",

  // sessions
  listSessions: () => request<SessionSummary[]>("/sessions"),
  state: (guildId: string) => request<GameSnapshot>(`/sessions/${guildId}/state`),

  // roles
  roles: () => request<RoleInfo[]>("/roles"),

  // members
  members: (guildId: string, query = "") =>
    request<{ id: string; name: string; displayName: string; avatar: string | null }[]>(
      `/sessions/${guildId}/members?query=${encodeURIComponent(query)}`,
    ),

  // game actions (each mutates then the server broadcasts a fresh snapshot)
  assign: (g: string) => post(`/sessions/${g}/assign`),
  reset: (g: string) => post(`/sessions/${g}/reset`),
  startGame: (g: string) => post(`/sessions/${g}/state/start`),
  nextPhase: (g: string) => post(`/sessions/${g}/state/next`),
  pause: (g: string) => post(`/sessions/${g}/state/pause`),
  kill: (g: string, seat: number, identityIndex: number | null, allowLastWords: boolean) =>
    post(`/sessions/${g}/seats/${seat}/kill`, { identityIndex, allowLastWords }),
  revive: (g: string, seat: number, identityIndex: number | null) =>
    post(`/sessions/${g}/seats/${seat}/revive`, { identityIndex }),
  edit: (g: string, seat: number, roleIds: string[], orderLocked: boolean | null) =>
    post(`/sessions/${g}/seats/${seat}/edit`, { roleIds, orderLocked }),
  forcePolice: (g: string, seat: number) => post(`/sessions/${g}/police/force`, { seat }),
  transferPolice: (g: string, fromSeat: number, toSeat: number) =>
    post(`/sessions/${g}/police/transfer`, { fromSeat, toSeat }),
  muteAll: (g: string) => post(`/sessions/${g}/voice/mute`),
  unmuteAll: (g: string) => post(`/sessions/${g}/voice/unmute`),
  startTimer: (g: string, seconds: number) => post(`/sessions/${g}/timer/start`, { seconds }),
  stopTimer: (g: string) => post(`/sessions/${g}/timer/stop`),

  // settings
  setPlayerCount: (g: string, count: number) => post(`/sessions/${g}/settings/player-count`, { count }),
  setPool: (g: string, pool: Record<string, number>) => post(`/sessions/${g}/settings/pool`, { pool }),
  setDoubleIdentity: (g: string, value: boolean) => post(`/sessions/${g}/settings/double-identity`, { value }),
  setMuteAfterSpeech: (g: string, value: boolean) => post(`/sessions/${g}/settings/mute-after-speech`, { value }),
};
