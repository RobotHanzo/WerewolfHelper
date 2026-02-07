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
    id: number;
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
    currentNightStatus?: NightStatus;
}

export interface PoliceState {
    state: 'NONE' | 'ENROLLMENT' | 'SPEECH' | 'UNENROLLMENT' | 'VOTING' | 'FINISHED';
    stageEndTime?: number;
    allowEnroll: boolean;
    allowUnEnroll: boolean;
    candidates: {
        id: number;   // Player ID (internal)
        quit?: boolean;
        voters: string[]; // List of User IDs (strings)
    }[];
}

export interface ExpelState {
    voting: boolean;
    endTime?: number;
    candidates: {
        id: number;
        quit?: boolean;
        voters: string[]; // List of User IDs (strings)
    }[];
}

export interface WerewolfMessage {
    senderId: number;
    senderName?: string;
    avatarUrl?: string | null;
    content: string;
    timestamp: number;
}

export interface WerewolfVote {
    voterId: number;
    voterName?: string;
    targetId: number | string | null;
    targetName?: string | null;
}

export interface ActionSubmissionStatus {
    playerId: number;
    playerName?: string;
    role: string;
    status: 'PENDING' | 'SUBMITTED' | 'SKIPPED' | 'ACTING' | 'PROCESSED';
    actionType: string | null;
    targetId: number | string | null;
    targetName?: string | null;
    submittedAt?: number | null;
}

export interface NightStatus {
    day: number;
    phaseType: 'WEREWOLF_VOTING' | 'ROLE_ACTIONS';
    startTime: number;
    endTime: number;
    werewolfMessages: WerewolfMessage[];
    werewolfVotes: WerewolfVote[];
    actionStatuses: ActionSubmissionStatus[];
}

export interface SpeechState {
    order: number[]; // List of Player IDs (internal IDs)
    currentSpeakerId?: number;
    endTime: number;
    totalTime: number;
    isPaused?: boolean;
    interruptVotes?: number[];
}

export interface Player {
    id: number;
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
