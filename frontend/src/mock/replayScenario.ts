import type { Replay, ReplayChat, ReplayEvent, ReplaySeat } from "@/types/replay";

/**
 * A self-contained sample recording (12-player, 3-day standard game) so the replay selector + player
 * render standalone in demo mode, when no backend is reachable. Mirrors the handoff mockup's story.
 */

const ROSTER: Array<[number, string, string, ReplaySeat["identities"][number]["faction"]]> = [
  [1, "阿哲", "villager", "VILLAGER"],
  [2, "小柔", "seer", "GOD"],
  [3, "大鵬", "wolf", "WOLF"],
  [4, "Niko", "witch", "GOD"],
  [5, "阿凱", "villager", "VILLAGER"],
  [6, "莉莉", "wolf", "WOLF"],
  [7, "月夜貓", "hunter", "GOD"],
  [8, "阿狼", "wolf", "WOLF"],
  [9, "子瑜", "villager", "VILLAGER"],
  [10, "阿KEN", "idiot", "GOD"],
  [11, "樂樂", "wolf", "WOLF"],
  [12, "海哥", "villager", "VILLAGER"],
];

const ROLE_NAME: Record<string, string> = {
  villager: "平民",
  seer: "預言家",
  witch: "女巫",
  hunter: "獵人",
  idiot: "白癡",
  wolf: "狼人",
};

const players: ReplaySeat[] = ROSTER.map(([seat, name, roleId, faction]) => ({
  seat,
  memberId: String(1000 + seat),
  name,
  avatar: null,
  police: seat === 2,
  identities: [{ roleId, name: ROLE_NAME[roleId], faction }],
}));

export function buildReplayScenario(): Replay {
  let idx = 0;
  let clock = Date.parse("2026-06-13T21:00:00+08:00");
  const events: ReplayEvent[] = [];
  const wolfChat: ReplayChat[] = [];

  const push = (
    e: Pick<ReplayEvent, "phaseType" | "day" | "type" | "kind" | "text"> & Partial<ReplayEvent>,
  ) => {
    clock += 4000;
    events.push({
      idx: idx++,
      actorSeat: null,
      author: null,
      avatar: null,
      effect: null,
      vote: null,
      atMs: clock,
      ...e,
    });
  };
  const wolf = (seat: number, text: string) => {
    wolfChat.push({ seat, userId: String(1000 + seat), author: ROSTER[seat - 1][1], avatar: null, content: text, atMs: clock + 500 });
  };

  // ---- 夜1 ----
  push({ phaseType: "night", day: 1, type: "system", kind: "event", text: "天黑請閉眼 — 第一夜開始，所有玩家閉眼。" });
  wolf(8, "第一晚先打信息，挑個強勢的民殺。");
  wolf(6, "玩家05 開場話最多，今晚刀 05？");
  wolf(11, "同意，刀 05。");
  wolf(3, "統一意見 — 今晚擊殺 玩家05。");
  push({ phaseType: "night", day: 1, type: "seer", kind: "skill", actorSeat: 2, text: "預言家查驗 玩家08 → 結果：狼人。" });
  push({ phaseType: "night", day: 1, type: "witch", kind: "skill", actorSeat: 4, text: "女巫保留藥水，今晚未行動。" });

  // ---- 日1 ----
  push({ phaseType: "day", day: 1, type: "system", kind: "event", text: "天亮了 — 第一天，請所有玩家睜眼。" });
  push({ phaseType: "day", day: 1, type: "death", kind: "death", actorSeat: 5, text: "玩家05 昨夜倒牌出局。", effect: { kill: [5], police: null, winner: null } });
  push({ phaseType: "day", day: 1, type: "speech", kind: "speech", actorSeat: 2, text: "我是預言家，昨晚查驗 玩家08，查殺！08 是狼。" });
  push({ phaseType: "day", day: 1, type: "speech", kind: "speech", actorSeat: 6, text: "我才是預言家，02 是悍跳狼，08 在我這是金水。" });
  push({
    phaseType: "day", day: 1, type: "vote", kind: "vote", text: "警長競選 — 玩家02 以 6 票當選警長。",
    effect: { kill: [], police: 2, winner: null },
    vote: { kind: "police", win: 2, out: null, note: null, rows: [{ seat: 2, count: 6, voters: [1, 4, 7, 9, 10, 12] }, { seat: 6, count: 3, voters: [3, 8, 11] }] },
  });
  push({ phaseType: "day", day: 1, type: "speech", kind: "speech", actorSeat: 8, text: "02 警徽歪了別亂帶節奏，我是好人，查殺不一定準。" });
  push({
    phaseType: "day", day: 1, type: "vote", kind: "vote", text: "放逐投票 — 玩家06 得 7.5 票，遭到放逐。",
    vote: { kind: "exile", out: 6, win: null, note: "警長 02 票數 ×1.5", rows: [{ seat: 6, count: 7.5, voters: [1, 2, 4, 7, 9, 10, 12] }, { seat: 2, count: 3, voters: [3, 8, 11] }] },
  });
  push({ phaseType: "day", day: 1, type: "death", kind: "death", actorSeat: 6, text: "玩家06 被放逐出局 — 身分揭曉：狼人。", effect: { kill: [6], police: null, winner: null } });

  // ---- 夜2 ----
  push({ phaseType: "night", day: 2, type: "system", kind: "event", text: "天黑請閉眼 — 第二夜。" });
  wolf(8, "02 是真預言家又當上警長，今晚必須處理掉他。");
  wolf(3, "刀 02，順便把警徽帶走。");
  wolf(11, "統一 — 擊殺 玩家02。");
  push({ phaseType: "night", day: 2, type: "seer", kind: "skill", actorSeat: 2, text: "預言家查驗 玩家11 → 結果：狼人。" });
  push({ phaseType: "night", day: 2, type: "witch", kind: "skill", actorSeat: 4, text: "女巫使用解藥，救起被刀的 玩家02！" });

  // ---- 日2 ----
  push({ phaseType: "day", day: 2, type: "system", kind: "event", text: "天亮了 — 第二天，昨晚是平安夜。" });
  push({ phaseType: "day", day: 2, type: "speech", kind: "speech", actorSeat: 2, text: "昨晚平安。我第二晚驗了 玩家11 → 查殺，11 是狼！" });
  push({ phaseType: "day", day: 2, type: "speech", kind: "speech", actorSeat: 11, text: "02 雙驗都報查殺也太假，我才是好人！" });
  push({
    phaseType: "day", day: 2, type: "vote", kind: "vote", text: "放逐投票 — 玩家11 遭到放逐。",
    vote: { kind: "exile", out: 11, win: null, note: "警長 02 票數 ×1.5", rows: [{ seat: 11, count: 7.5, voters: [1, 2, 4, 7, 9, 10, 12] }, { seat: 2, count: 2, voters: [3, 8] }] },
  });
  push({ phaseType: "day", day: 2, type: "death", kind: "death", actorSeat: 11, text: "玩家11 被放逐出局 — 身分揭曉：狼人。", effect: { kill: [11], police: null, winner: null } });

  // ---- 夜3 ----
  push({ phaseType: "night", day: 3, type: "system", kind: "event", text: "天黑請閉眼 — 第三夜。" });
  wolf(3, "場上只剩我們兩個了，得拆掉神職。");
  wolf(8, "賭一把，今晚刀 獵人 07，逼他空槍。");
  push({ phaseType: "night", day: 3, type: "witch", kind: "skill", actorSeat: 4, text: "女巫保留藥水，今晚未行動。" });

  // ---- 日3 ----
  push({ phaseType: "day", day: 3, type: "system", kind: "event", text: "天亮了 — 第三天。" });
  push({ phaseType: "day", day: 3, type: "death", kind: "death", actorSeat: 7, text: "玩家07 昨夜出局 — 身分揭曉：獵人（神）。", effect: { kill: [7], police: null, winner: null } });
  push({ phaseType: "day", day: 3, type: "hunter", kind: "death", actorSeat: 7, text: "玩家07 發動獵人技能 — 開槍帶走 玩家08！", effect: { kill: [8], police: null, winner: null } });
  push({ phaseType: "day", day: 3, type: "speech", kind: "speech", actorSeat: 2, text: "只剩最後一隻狼，玩家03 一路跟著狼隊節奏，就是他。" });
  push({
    phaseType: "day", day: 3, type: "vote", kind: "vote", text: "放逐投票 — 玩家03 遭到放逐。",
    vote: { kind: "exile", out: 3, win: null, note: "警長 02 票數 ×1.5", rows: [{ seat: 3, count: 5.5, voters: [1, 2, 4, 9, 10, 12] }, { seat: 2, count: 1, voters: [3] }] },
  });
  push({ phaseType: "day", day: 3, type: "death", kind: "death", actorSeat: 3, text: "玩家03 被放逐出局 — 身分揭曉：狼人。", effect: { kill: [3], police: null, winner: null } });

  // ---- 結束 ----
  push({ phaseType: "end", day: 3, type: "result", kind: "result", text: "所有狼人皆已出局，好人陣營取得勝利！", effect: { kill: [], police: null, winner: "GOOD" } });

  return {
    id: "demo",
    guildId: "demo",
    guildName: "示範伺服器",
    guildIcon: null,
    title: "週五晚場 · 12 人標準局",
    startedAt: events[0]?.atMs ?? clock,
    endedAt: clock,
    playerCount: 12,
    doubleIdentity: false,
    winnerFaction: "GOOD",
    winnerReasonKey: null,
    players,
    events,
    wolfChat,
  };
}

/** A couple of summaries for the demo selector. */
export function buildReplaySummaries() {
  const r = buildReplayScenario();
  return [
    {
      id: r.id, guildId: r.guildId, guildName: r.guildName, guildIcon: r.guildIcon, title: r.title,
      startedAt: r.startedAt, endedAt: r.endedAt, durationMs: r.endedAt - r.startedAt,
      playerCount: r.playerCount, doubleIdentity: r.doubleIdentity, winnerFaction: r.winnerFaction,
    },
  ];
}
