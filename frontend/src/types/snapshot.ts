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

export interface NightAction {
  abilityId: string;
  roleId: string;
  roleName: string;
  faction: Faction;
  actorSeats: number[];
  targetSeat: number | null;
  status: "acting" | "submitted" | "skipped";
}

export interface NightWave {
  index: number;
  actions: NightAction[];
}

export interface Night {
  active: boolean;
  day: number;
  endsAt: number | null;
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
  started: boolean;
  doubleIdentity: boolean;
  muteAfterSpeech: boolean;
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
  log: LogEntry[];
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
