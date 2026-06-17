// Hand-written types mirroring the backend replay DTOs (controller/dto/ReplayDtos.kt). Ids are
// strings (Discord snowflakes exceed JS safe ints); timestamps are epoch millis.

import type { Faction } from "@/types/snapshot";

export interface ReplaySummary {
  id: string;
  guildId: string;
  guildName: string | null;
  guildIcon: string | null;
  title: string;
  startedAt: number;
  endedAt: number;
  durationMs: number;
  playerCount: number;
  doubleIdentity: boolean;
  /** "WOLF" | "GOOD" | null. */
  winnerFaction: string | null;
}

export interface ReplayIdentity {
  roleId: string;
  name: string;
  faction: Faction;
}

export interface ReplaySeat {
  seat: number;
  memberId: string | null;
  name: string | null;
  avatar: string | null;
  police: boolean;
  identities: ReplayIdentity[];
}

export interface ReplayEffect {
  kill: number[];
  police: number | null;
  /** "WOLF" | "GOOD" on the final result event. */
  winner: string | null;
}

export interface ReplayVoteRow {
  seat: number;
  count: number;
  voters: number[];
}

export interface ReplayVote {
  /** "police" | "exile". */
  kind: string;
  out: number | null;
  win: number | null;
  note: string | null;
  rows: ReplayVoteRow[];
}

/** One point on the replay timeline. `type` drives the glyph/colour, `kind` the filter chips. */
export interface ReplayEvent {
  idx: number;
  day: number;
  /** "night" | "day" | "end". */
  phaseType: string;
  /** system | seer | witch | hunter | speech | police | vote | death | wolf | result. */
  type: string;
  /** event | skill | speech | vote | death | chat | result. */
  kind: string;
  actorSeat: number | null;
  /** For court 發言 events whose author isn't a live seat (judge/spectator). */
  author: string | null;
  avatar: string | null;
  text: string;
  atMs: number;
  effect: ReplayEffect | null;
  vote: ReplayVote | null;
}

export interface ReplayChat {
  seat: number | null;
  userId: string;
  author: string;
  avatar: string | null;
  content: string;
  atMs: number;
}

export interface Replay {
  id: string;
  guildId: string;
  guildName: string | null;
  guildIcon: string | null;
  title: string;
  startedAt: number;
  endedAt: number;
  playerCount: number;
  doubleIdentity: boolean;
  winnerFaction: string | null;
  winnerReasonKey: string | null;
  players: ReplaySeat[];
  events: ReplayEvent[];
  wolfChat: ReplayChat[];
}
