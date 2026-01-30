
export type Role = 'WEREWOLF' | 'VILLAGER' | 'SEER' | 'WITCH' | 'HUNTER' | 'GUARD' | 'IDIOT' | 'WOLF_KING';
export type GamePhase = 'LOBBY' | 'NIGHT' | 'DAY' | 'VOTING' | 'GAME_OVER';

export interface Player {
  id: string;
  discordId: string;
  name: string;
  avatar: string;
  role: Role;
  isAlive: boolean;
  isSheriff: boolean;
  isJinBaoBao: boolean;
  isProtected: boolean;
  isPoisoned: boolean;
  isSilenced: boolean;
  hasVoted: boolean;
}

export interface LogEntry {
  id: string;
  timestamp: string;
  message: string;
  type: 'info' | 'action' | 'alert' | 'chat';
}

export interface GameState {
  phase: GamePhase;
  dayCount: number;
  timerSeconds: number;
  players: Player[];
  logs: LogEntry[];
  winner?: 'WEREWOLVES' | 'VILLAGERS' | null;
}
