import { useEffect, useMemo, useRef, useState } from "react";
import type { Faction } from "@/types/snapshot";
import type { Replay, ReplayEvent, ReplaySeat, ReplayVote } from "@/types/replay";

export type ReplayFilter = "all" | "speech" | "vote" | "death" | "skill" | "wolf";

/** A timeline event with the derived event-paced window (start/end seconds). */
interface TimedEvent extends ReplayEvent {
  start: number;
  end: number;
}

export interface ReplaySegment {
  key: string;
  phaseType: string;
  day: number;
  start: number;
  end: number;
  dur: number;
  firstIdx: number;
}

export interface ReplaySeatView {
  seat: number;
  name: string;
  avatar: string | null;
  dead: boolean;
  police: boolean;
  reveal: boolean;
  changed: boolean;
  roleId: string;
  roleName: string;
  faction: Faction;
}

export interface StreamItem {
  key: string;
  type: string;
  phaseType: string;
  actorSeat: number | null;
  actorName: string | null;
  text: string;
  atMs: number;
  isCur: boolean;
}

export interface WolfLineView {
  key: string;
  seat: number | null;
  name: string;
  avatar: string | null;
  text: string;
  atMs: number;
  isCur: boolean;
}

const TICK_MS = 50;

/** Derive a watchable per-event dwell from its text length (event-paced playback, not wall clock). */
function dwellOf(e: ReplayEvent): number {
  const base = e.kind === "speech" ? 2.4 : e.type === "vote" || e.type === "result" ? 3.4 : 2.2;
  return Math.min(6, Math.max(1.6, base + e.text.length * 0.03));
}

export function formatClock(sec: number): string {
  const v = Math.max(0, Math.floor(sec));
  const m = String(Math.floor(v / 60)).padStart(2, "0");
  const s = String(v % 60).padStart(2, "0");
  return `${m}:${s}`;
}

export function formatTime(ms: number): string {
  const d = new Date(ms);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export function useReplayPlayer(replay: Replay, startSpeed = 1) {
  const [t, setT] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(startSpeed);
  const [spoiler, setSpoiler] = useState(false);
  const [filter, setFilter] = useState<ReplayFilter>("all");
  const scrubbing = useRef(false);

  const playersBySeat = useMemo(() => {
    const m = new Map<number, ReplaySeat>();
    replay.players.forEach((p) => m.set(p.seat, p));
    return m;
  }, [replay]);

  const { timeline, total, segments } = useMemo(() => {
    let acc = 0;
    const tl: TimedEvent[] = replay.events.map((e) => {
      const start = acc;
      acc += dwellOf(e);
      return { ...e, start, end: acc };
    });
    const segs: ReplaySegment[] = [];
    tl.forEach((e) => {
      const last = segs[segs.length - 1];
      if (!last || last.phaseType !== e.phaseType || last.day !== e.day) {
        segs.push({ key: `${e.phaseType}-${e.day}-${e.idx}`, phaseType: e.phaseType, day: e.day, start: e.start, end: e.end, dur: e.end - e.start, firstIdx: e.idx });
      } else {
        last.end = e.end;
        last.dur = last.end - last.start;
      }
    });
    return { timeline: tl, total: acc, segments: segs };
  }, [replay]);

  // event-paced clock
  useEffect(() => {
    const iv = setInterval(() => {
      if (!playing || scrubbing.current) return;
      setT((prev) => {
        const next = prev + (TICK_MS / 1000) * speed;
        if (next >= total) {
          setPlaying(false);
          return total;
        }
        return next;
      });
    }, TICK_MS);
    return () => clearInterval(iv);
  }, [playing, speed, total]);

  const curIndex = useMemo(() => {
    if (t >= total) return timeline.length - 1;
    let idx = 0;
    for (let i = 0; i < timeline.length; i++) {
      if (timeline[i].start <= t) idx = i;
      else break;
    }
    return idx;
  }, [t, total, timeline]);

  const cur: TimedEvent | undefined = timeline[curIndex];
  const atEnd = t >= total;

  const view = useMemo(() => {
    const dead = new Set<number>();
    let police: number | null = null;
    let winner: string | null = null;
    for (let i = 0; i <= curIndex; i++) {
      const ef = timeline[i]?.effect;
      if (!ef) continue;
      ef.kill.forEach((s) => dead.add(s));
      if (ef.police != null) police = ef.police;
      if (ef.winner) winner = ef.winner;
    }
    const changed = new Set<number>();
    if (cur?.effect) {
      cur.effect.kill.forEach((s) => changed.add(s));
      if (cur.effect.police != null) changed.add(cur.effect.police);
    }
    const revealAll = spoiler || atEnd;

    const seats: ReplaySeatView[] = replay.players.map((p) => {
      const id = p.identities[0];
      const isDead = dead.has(p.seat);
      return {
        seat: p.seat,
        name: p.name ?? `玩家${String(p.seat).padStart(2, "0")}`,
        avatar: p.avatar,
        dead: isDead,
        police: police === p.seat && !isDead,
        reveal: isDead || revealAll,
        changed: changed.has(p.seat),
        roleId: id?.roleId ?? "villager",
        roleName: id?.name ?? "",
        faction: id?.faction ?? "VILLAGER",
      };
    });

    const factionCount = (f: Faction) => {
      let alive = 0;
      let totalF = 0;
      replay.players.forEach((p) => {
        if (p.identities[0]?.faction === f) {
          totalF++;
          if (!dead.has(p.seat)) alive++;
        }
      });
      return { alive, total: totalF };
    };
    const wolfM = factionCount("WOLF");
    const godM = factionCount("GOD");
    const thirdM = replay.doubleIdentity ? factionCount("GBABY") : factionCount("VILLAGER");
    const factions = [
      { faction: "WOLF" as Faction, ...wolfM },
      { faction: "GOD" as Faction, ...godM },
      { faction: (replay.doubleIdentity ? "GBABY" : "VILLAGER") as Faction, ...thirdM },
    ];

    const nameOf = (seat: number | null) => (seat != null ? playersBySeat.get(seat)?.name ?? null : null);

    // event stream (or wolf chat when the 狼人 filter is active)
    let stream: StreamItem[];
    if (filter === "wolf") {
      stream = replay.wolfChat
        .filter((w) => !cur || w.atMs <= cur.atMs)
        .map((w, i) => ({ key: `w${i}`, type: "wolf", phaseType: "night", actorSeat: w.seat, actorName: w.author, text: w.content, atMs: w.atMs, isCur: false }));
    } else {
      stream = timeline
        .filter((e) => e.start <= t && (filter === "all" || e.kind === filter))
        .map((e) => ({
          key: `e${e.idx}`,
          type: e.type,
          phaseType: e.phaseType,
          actorSeat: e.actorSeat,
          actorName: e.author ?? nameOf(e.actorSeat),
          text: e.text,
          atMs: e.atMs,
          isCur: e.idx === curIndex,
        }));
    }

    const wolfLines: WolfLineView[] = replay.wolfChat
      .filter((w) => !cur || w.atMs <= cur.atMs)
      .map((w, i, arr) => ({
        key: `wl${i}`,
        seat: w.seat,
        name: w.author,
        avatar: w.avatar,
        text: w.content,
        atMs: w.atMs,
        isCur: i === arr.length - 1,
      }));

    const vote: ReplayVote | null = cur?.kind === "vote" ? cur.vote : null;
    const showResults = cur?.type === "result";

    return { dead, police, winner, revealAll, seats, factions, stream, wolfLines, vote, showResults };
  }, [curIndex, cur, t, spoiler, atEnd, filter, replay, timeline, playersBySeat]);

  const seg = segments.find((s) => s.phaseType === (cur?.phaseType ?? "night") && s.day === (cur?.day ?? 1)) ?? segments[0];

  // ---- controls ----
  const togglePlay = () => {
    if (t >= total) {
      setT(0);
      setPlaying(true);
    } else {
      setPlaying((p) => !p);
    }
  };
  const stepBack = () => {
    const ni = Math.max(0, curIndex - 1);
    setT(timeline[ni]?.start ?? 0);
    setPlaying(false);
  };
  const stepFwd = () => {
    const ni = Math.min(timeline.length - 1, curIndex + 1);
    setT(timeline[ni]?.start ?? total);
    setPlaying(false);
  };
  const jumpSegment = (s: ReplaySegment) => {
    setT(timeline[s.firstIdx]?.start ?? 0);
    setPlaying(false);
  };
  const onScrub = (value: number) => {
    scrubbing.current = true;
    setT(value);
    setPlaying(false);
    window.setTimeout(() => (scrubbing.current = false), 80);
  };
  const restart = () => {
    setT(0);
    setPlaying(true);
  };

  return {
    t,
    total,
    playing,
    speed,
    spoiler,
    filter,
    curIndex,
    cur,
    atEnd,
    segment: seg,
    segments,
    timeline,
    playheadPct: total > 0 ? (t / total) * 100 : 0,
    eventNo: curIndex + 1,
    eventTotal: timeline.length,
    ...view,
    setSpeed,
    setFilter,
    toggleSpoiler: () => setSpoiler((s) => !s),
    togglePlay,
    stepBack,
    stepFwd,
    jumpSegment,
    onScrub,
    restart,
  };
}
