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

export type Role = string;

// Legacy types for reference (can be removed once migration is complete)
export type GamePhase = 'LOBBY' | 'NIGHT' | 'DAY' | 'VOTING' | 'GAME_OVER';

export interface GameState {
    phase: GamePhase;
    day: number;
    timerSeconds: number;
    doubleIdentities?: boolean;
    availableRoles?: string[];
    hasAssignedRoles?: boolean;
    players: Player[];
    logs: LogEntry[];
    speech?: SpeechState;
    police?: PoliceState;
    expel?: ExpelState;
    currentState?: string; // New State Machine State (e.g. "SETUP", "NIGHT_PHASE")
    currentStep?: string; // Display name
    stateData?: any;
    isManualStep?: boolean; // True if duration is -1 (manual advance)
}

export interface PoliceState {
    state: 'NONE' | 'ENROLLMENT' | 'SPEECH' | 'UNENROLLMENT' | 'VOTING' | 'FINISHED';
    stageEndTime?: number;
    allowEnroll: boolean;
    allowUnEnroll: boolean;
    candidates: {
        id: string;   // Player ID (internal)
        quit?: boolean;
        voters: string[]; // List of User IDs (or Player IDs depending on backend mapping)
    }[];
}

export interface ExpelState {
    voting: boolean;
    candidates: {
        id: string;
        quit?: boolean;
        voters: string[];
    }[];
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

export interface User {
    userId: string;
    username: string;
    avatar: string;
    guildId: string;
    role: 'JUDGE' | 'SPECTATOR' | 'BLOCKED' | 'PENDING';
}
