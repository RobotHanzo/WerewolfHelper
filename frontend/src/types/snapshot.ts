// Hand-written types mirroring the backend `GameSnapshot` DTO. Kept in lock-step with the
// OpenAPI schema; identities are described purely by roleId + localized name + faction, so the
// UI never hardcodes a role.

export type Faction = "WOLF" | "GOD" | "VILLAGER" | "GBABY";

export type Phase =
  | "LOBBY"
  | "ASSIGNMENT"
  | "NIGHT"
  | "DAWN"
  | "DAY"
  | "POLICE_ELECTION"
  | "SPEECHES"
  | "EXPEL_VOTE"
  | "OVER";

export type LogSeverity = "info" | "action" | "alert";

export interface Identity {
  roleId: string;
  name: string;
  faction: Faction;
  dead: boolean;
}

export interface Seat {
  seat: number;
  label: string;
  memberId: string | null;
  displayName: string | null;
  avatar: string | null;
  unassigned: boolean;
  alive: boolean;
  identities: Identity[];
  police: boolean;
  goldenBaby: boolean;
  clone: boolean;
  idiot: boolean;
  orderLocked: boolean;
  // ROLES.md behavioural state (judge action buttons / state badges)
  revengePending: boolean;
  idiotRevealed: boolean;
  loverSeat: number | null;
  charmedSeat: number | null;
  learnedRoleId: string | null;
  knifeArmed: boolean;
}

export interface FactionMeter {
  faction: Faction;
  alive: number;
  total: number;
}

export interface Winner {
  faction: Faction;
  reason: string;
}

export interface Speech {
  active: boolean;
  waiting: boolean;
  direction: "UP" | "DOWN" | null;
  fromSeat: number | null;
  speakerSeat: number | null;
  endsAt: number | null;
  order: number[];
  upcoming: number[];
  /** Seats that have cast a 下台 (step-down) vote against the current speaker. */
  interruptVoters: number[];
  /** 下台 votes needed to force the current speaker off (alive majority). */
  interruptThreshold: number;
  /** Last-words flow: no 下台 vote applies. */
  lastWords: boolean;
}

export interface PollCandidate {
  seat: number;
  withdrawn: boolean;
  weight: number;
  voters: number[];
}

export interface Poll {
  kind: "POLICE" | "EXPEL";
  stage: "ENROLL" | "CAMPAIGN" | "WITHDRAW" | "VOTING" | "RESOLVED";
  endsAt: number | null;
  candidates: PollCandidate[];
  eligibleVoters: number;
  votesCast: number;
}

export interface NightVote {
  voter: number;
  /** Target seat; null when not yet voted or when voting to skip (see `skip`). */
  target: number | null;
  /** True when this wolf voted not to kill tonight. */
  skip: boolean;
}

export interface NightAction {
  abilityId: string;
  roleId: string;
  roleName: string;
  faction: Faction;
  actorSeats: number[];
  targetSeat: number | null;
  status: "acting" | "submitted" | "skipped";
  /** Per-wolf vote breakdown for the collective knife; null for single-actor abilities. */
  votes?: NightVote[] | null;
}

export interface NightWave {
  index: number;
  actions: NightAction[];
}

/** One relayed wolf-team chat line, shown on the judge wolf-chat panel. */
export interface WolfChatMessage {
  seat: number;
  author: string;
  avatar: string | null;
  content: string;
  at: number;
}

export interface Night {
  active: boolean;
  day: number;
  /** Deadline (epoch ms) of the currently-active phase. */
  endsAt: number | null;
  /** Wave index of the phase currently being prompted (phases run sequentially). */
  currentPhase: number;
  submittedCount: number;
  totalCount: number;
  resolved: boolean;
  summary: string | null;
  waves: NightWave[];
}

export interface LogEntry {
  id: string;
  timestamp: number;
  severity: LogSeverity;
  text: string;
}

export interface GameSnapshot {
  guildId: string;
  phase: Phase;
  day: number;
  paused: boolean;
  /** Epoch-ms the game was paused at — every countdown freezes here; null while running. */
  pausedAt: number | null;
  started: boolean;
  doubleIdentity: boolean;
  muteAfterSpeech: boolean;
  witchSelfSave: boolean;
  hiddenWolfInheritsKnife: boolean;
  assigned: boolean;
  policeSeat: number | null;
  aliveCount: number;
  totalSeats: number;
  winner: Winner | null;
  timerEndsAt: number | null;
  seats: Seat[];
  meters: FactionMeter[];
  speech: Speech | null;
  poll: Poll | null;
  night: Night | null;
  /** Wolf-team chatter relayed from the seat channels, in send order — synced across every phase. */
  wolfChat: WolfChatMessage[];
  log: LogEntry[];
  pool: Record<string, number>;
}

export type DashboardRole = "JUDGE" | "SPECTATOR" | "PENDING" | "BLOCKED";

export interface AuthInfo {
  userId: string;
  username: string;
  avatar: string | null;
  role: DashboardRole;
  guildId: string | null;
}

export interface SessionSummary {
  guildId: string;
  guildName: string;
  guildIcon: string | null;
  playerCount: number;
}

export interface RoleInfo {
  id: string;
  name: string;
  faction: Faction;
}
