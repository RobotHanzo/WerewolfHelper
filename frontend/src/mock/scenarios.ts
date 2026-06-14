import type { Faction, GameSnapshot, Identity, LogEntry, Seat } from "@/types/snapshot";

// Demo data mirroring the design prototype, so every screen renders without a live backend.

const FACTION: Record<string, Faction> = {
  狼人: "WOLF",
  狼王: "WOLF",
  夢魘: "GOD",
  預言家: "GOD",
  女巫: "GOD",
  獵人: "GOD",
  守衛: "GOD",
  白癡: "GOD",
  騎士: "GOD",
  平民: "VILLAGER",
};

const ROLE_ID: Record<string, string> = {
  狼人: "wolf",
  狼王: "wolf_king",
  夢魘: "nightmare",
  預言家: "seer",
  女巫: "witch",
  獵人: "hunter",
  守衛: "guard",
  白癡: "idiot",
  騎士: "knight",
  平民: "villager",
};

function id(name: string, dead = false): Identity {
  return { roleId: ROLE_ID[name] ?? name, name, faction: FACTION[name] ?? "GOD", dead };
}

interface SeatSpec {
  seat: number;
  name: string;
  ids: [string, boolean][];
  police?: boolean;
  gbaby?: boolean;
  locked?: boolean;
}

const SEATS: SeatSpec[] = [
  { seat: 1, name: "夜行者", ids: [["狼人", false], ["平民", false]] },
  { seat: 2, name: "月夜貓", ids: [["預言家", false], ["平民", false]], police: true },
  { seat: 3, name: "阿狼", ids: [["狼人", true], ["平民", true]] },
  { seat: 4, name: "小白兔", ids: [["平民", false], ["平民", false]], gbaby: true },
  { seat: 5, name: "黑森林", ids: [["女巫", false], ["平民", false]] },
  { seat: 6, name: "老王", ids: [["平民", true], ["獵人", false]] },
  { seat: 7, name: "薄霧", ids: [["守衛", false], ["平民", false]], locked: false },
  { seat: 8, name: "灰燼", ids: [["狼王", false], ["平民", false]] },
  { seat: 9, name: "晨星", ids: [["平民", false], ["平民", false]] },
  { seat: 10, name: "半夜雨", ids: [["狼人", true], ["平民", true]] },
  { seat: 11, name: "燈芯", ids: [["白癡", false], ["平民", false]], locked: false },
  { seat: 12, name: "蒼狼", ids: [["狼人", false], ["平民", false]] },
];

function buildSeat(spec: SeatSpec): Seat {
  const identities = spec.ids.map(([n, d]) => id(n, d));
  return {
    seat: spec.seat,
    label: String(spec.seat).padStart(2, "0"),
    memberId: String(1000 + spec.seat),
    displayName: spec.name,
    avatar: null,
    unassigned: false,
    alive: identities.some((i) => !i.dead),
    identities,
    police: spec.police ?? false,
    goldenBaby: spec.gbaby ?? false,
    clone: false,
    idiot: spec.ids.some(([n]) => n === "白癡"),
    orderLocked: spec.locked ?? true,
    revengePending: false,
    idiotRevealed: false,
    loverSeat: null,
    charmedSeat: null,
    learnedRoleId: null,
    knifeArmed: false,
  };
}

function meters(seats: Seat[], double: boolean) {
  const count = (f: Faction) => {
    let alive = 0;
    let total = 0;
    seats.forEach((s) => s.identities.forEach((i) => {
      if (i.faction === f) {
        total++;
        if (!i.dead) alive++;
      }
    }));
    return { alive, total };
  };
  const wolf = count("WOLF");
  const god = count("GOD");
  const third = double
    ? (() => {
        const gb = seats.filter((s) => s.goldenBaby);
        return { faction: "GBABY" as Faction, alive: gb.filter((s) => s.alive).length, total: gb.length };
      })()
    : { faction: "VILLAGER" as Faction, ...count("VILLAGER") };
  return [
    { faction: "WOLF" as Faction, ...wolf },
    { faction: "GOD" as Faction, ...god },
    third,
  ];
}

const LOG: LogEntry[] = [
  { id: "l1", timestamp: Date.now() - 60_000, severity: "alert", text: "玩家06 的「平民」死亡 — 夜晚出局" },
  { id: "l2", timestamp: Date.now() - 90_000, severity: "action", text: "進入第 3 夜，全體靜音" },
  { id: "l3", timestamp: Date.now() - 140_000, severity: "info", text: "玩家09 發言結束" },
  { id: "l4", timestamp: Date.now() - 200_000, severity: "alert", text: "玩家10 死亡（狼人）— 放逐出局" },
  { id: "l5", timestamp: Date.now() - 260_000, severity: "action", text: "警長競選：玩家02 當選（5.5 票）" },
  { id: "l6", timestamp: Date.now() - 320_000, severity: "info", text: "身分分配完成（12 位玩家・24 張板子）" },
];

export type ScenarioId = "night" | "day" | "election" | "vote" | "lobby" | "over";

export function buildScenario(scenario: ScenarioId): GameSnapshot {
  const seats = SEATS.map(buildSeat);
  const now = Date.now();
  const base: GameSnapshot = {
    guildId: "demo",
    phase: "NIGHT",
    day: 3,
    paused: false,
    pausedAt: null,
    started: true,
    doubleIdentity: true,
    muteAfterSpeech: true,
    witchSelfSave: false,
    hiddenWolfInheritsKnife: true,
    assigned: true,
    policeSeat: 2,
    aliveCount: seats.filter((s) => s.alive).length,
    totalSeats: 12,
    winner: null,
    winRevealed: false,
    timerEndsAt: null,
    orderLockEndsAt: null,
    seats,
    meters: meters(seats, true),
    speech: null,
    poll: null,
    night: null,
    wolfChat: [],
    log: LOG,
    pool: {
      villager: 4,
      seer: 1,
      witch: 1,
      hunter: 1,
      guard: 1,
      wolf: 3,
      wolf_king: 1,
    },
  };

  switch (scenario) {
    case "night":
      return {
        ...base,
        night: {
          active: true,
          day: 3,
          endsAt: now + 45_000,
          currentPhase: 2,
          submittedCount: 3,
          totalCount: 5,
          resolved: false,
          summary: null,
          waves: [
            { index: 0, actions: [{ abilityId: "magician.swap", roleId: "magician", roleName: "魔術師", faction: "GOD", actorSeats: [4], targetSeat: 9, status: "submitted" }] },
            {
              index: 2,
              actions: [
                { abilityId: "wolf.kill", roleId: "wolf", roleName: "狼人", faction: "WOLF", actorSeats: [1, 3, 8, 12], targetSeat: 9, status: "acting", votes: [{ voter: 1, target: 9, skip: false }, { voter: 3, target: 9, skip: false }, { voter: 8, target: null, skip: true }, { voter: 12, target: null, skip: false }] },
                { abilityId: "guard.protect", roleId: "guard", roleName: "守衛", faction: "GOD", actorSeats: [7], targetSeat: 2, status: "submitted" },
                { abilityId: "seer.investigate", roleId: "seer", roleName: "預言家", faction: "GOD", actorSeats: [2], targetSeat: 12, status: "submitted" },
              ],
            },
            { index: 3, actions: [{ abilityId: "witch.potion", roleId: "witch", roleName: "女巫", faction: "GOD", actorSeats: [5], targetSeat: null, status: "acting" }] },
          ],
        },
        wolfChat: [
          { seat: 1, author: "Alex（01）", avatar: null, content: "今晚刀誰？9 號一直跳預言家", at: now - 38_000 },
          { seat: 3, author: "Mia（03）", avatar: null, content: "同意，9 號刀掉，他帶節奏太兇", at: now - 30_000 },
          { seat: 8, author: "Leo（08）", avatar: null, content: "我覺得留 9 號，先處理真預言家 2 號", at: now - 18_000 },
          { seat: 1, author: "Alex（01）", avatar: null, content: "那這夜先 9，明天再看 2 號", at: now - 6_000 },
        ],
      };
    case "day":
      return {
        ...base,
        phase: "SPEECHES",
        speech: { active: true, waiting: false, direction: "DOWN", fromSeat: 7, speakerSeat: 7, endsAt: now + 88_000, order: [7, 8, 9, 11, 12, 1, 2, 4, 5], upcoming: [8, 9, 11, 12], interruptVoters: [9, 11], interruptThreshold: 5, lastWords: false },
      };
    case "election":
      return {
        ...base,
        phase: "POLICE_ELECTION",
        day: 1,
        seats: seats.map((s) => ({ ...s, police: false })),
        policeSeat: null,
        poll: {
          kind: "POLICE",
          stage: "VOTING",
          endsAt: now + 22_000,
          eligibleVoters: 10,
          votesCast: 4,
          candidates: [
            { seat: 2, withdrawn: false, weight: 3, voters: [1, 4, 9] },
            { seat: 5, withdrawn: false, weight: 1, voters: [8] },
            { seat: 9, withdrawn: true, weight: 0, voters: [] },
          ],
        },
      };
    case "vote":
      return {
        ...base,
        phase: "EXPEL_VOTE",
        poll: {
          kind: "EXPEL",
          stage: "VOTING",
          endsAt: now + 30_000,
          eligibleVoters: 9,
          votesCast: 5,
          candidates: [
            { seat: 3, withdrawn: false, weight: 3.5, voters: [1, 2, 5] },
            { seat: 8, withdrawn: false, weight: 2, voters: [4, 9] },
            { seat: 12, withdrawn: false, weight: 0, voters: [] },
          ],
        },
      };
    case "lobby":
      return {
        ...base,
        phase: "LOBBY",
        day: 0,
        started: false,
        assigned: false,
        seats: seats.map((s, i) => ({
          ...s,
          identities: [],
          police: false,
          goldenBaby: false,
          unassigned: i >= 8,
          alive: false,
          memberId: i < 8 ? s.memberId : null,
          displayName: i < 8 ? s.displayName : null,
        })),
      };
    case "over":
      return {
        ...base,
        phase: "OVER",
        winner: { faction: "GOD", reason: "所有狼人已出局" },
        seats: seats.map((s) => ({ ...s, identities: s.identities.map((i) => (i.faction === "WOLF" ? { ...i, dead: true } : i)) })),
      };
  }
}
