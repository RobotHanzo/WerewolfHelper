// Game state types matching backend Session structure
export interface Session {
  guildId: string;
  guildName?: string;
  guildIcon?: string;
  doubleIdentities: boolean;
  muteAfterSpeech: boolean;
  hasAssignedRoles: boolean;
  roles: string[];
  players: SessionPlayer[];
}

export interface SessionPlayer {
  id: string;
  roleId: string;
  channelId: string;
  userId?: string;
  name: string;
  avatar: string;
  roles: string[];
  deadRoles: string[];
  isAlive: boolean;
  jinBaoBao: boolean;
  police: boolean;
  idiot: boolean;
  duplicated: boolean;
  rolePositionLocked: boolean;
}

// Legacy types for reference (can be removed once migration is complete)
export type GamePhase = 'LOBBY' | 'NIGHT' | 'DAY' | 'VOTING' | 'GAME_OVER';

export interface GameState {
  phase: GamePhase;
  dayCount: number;
  timerSeconds: number;
  doubleIdentities?: boolean;
  availableRoles?: string[];
  players: Player[];
  logs: LogEntry[];
  speech?: SpeechState;
  police?: PoliceState;
}

export interface PoliceState {
  allowEnroll: boolean;
  allowUnEnroll: boolean;
  candidates: string[]; // List of Player IDs (internal IDs)
}

export interface SpeechState {
  order: string[]; // List of Player IDs (internal IDs)
  currentSpeakerId?: string;
  endTime: number;
  totalTime: number;
  isPaused?: boolean;
  interruptVotes?: string[];
}

export interface Player {
  id: string;
  name: string;
  userId?: string; // Discord User ID
  username?: string; // Discord username
  roles: string[]; // Array to support double identities
  deadRoles: string[]; // Track which roles are dead
  avatar: string;
  isAlive: boolean;
  isSheriff: boolean;
  isJinBaoBao: boolean;
  isProtected: boolean;
  isPoisoned: boolean;
  isSilenced: boolean;
  isDuplicated?: boolean;
  isJudge?: boolean;
  rolePositionLocked?: boolean;
}

export interface LogEntry {
  id: string;
  timestamp: string;
  message: string;
  type: 'info' | 'action' | 'alert';
}
