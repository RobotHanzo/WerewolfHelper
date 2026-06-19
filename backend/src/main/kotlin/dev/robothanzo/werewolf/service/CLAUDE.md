# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`service/` — the **orchestration layer**: it wires the pure `game/` engine to persistence, Discord,
and the WebSocket. This is where side effects live; keep new rules in `game/` and call them here.

- `GameSessionService` is the **funnel**: `mutate(guildId) { session -> ... }` loads, runs the block,
  **saves, then broadcasts a full snapshot** (snapshot-as-truth). Almost every state change goes
  through it. Also owns `log(...)` (typed, localized log entries: key + params + rendered text) and
  `broadcast`. Don't persist/broadcast by hand — call `mutate`.
- `GameFlowCoordinator` owns the **coarse phase walk** (the side of the pure `game/flow/GameFlowService`):
  `start`/`advance` run the phase machine then `enterPhase` dispatches to the orchestrator that owns
  the phase (`night.startNight` / `day.enterDawn|startPoliceElection|startSpeeches|startExpelVote`).
  Crucially it drives **automatic stage progression**: when a day stage finishes its interactive work
  (`poll == null && speech == null` in a day phase) the orchestrators call `coordinator.advance`, so
  the loop walks itself `NIGHT → DAWN → [POLICE_ELECTION] → SPEECHES → EXPEL_VOTE → NIGHT(+1)` with no
  judge click; `/state/next` stays an explicit override. To avoid a bean cycle the coordinator depends
  on the orchestrators and registers itself onto them in `@PostConstruct` (`night.coordinator = this`),
  the same self-registration idiom services use with `DiscordBot`. `NightOrchestrator.resolveNight` hands off to
  `enterPhase(DAWN)` once the night resolves (this is what actually runs 天亮).
- `SnapshotService` builds the wire `GameSnapshot` (`controller/dto`) consumed by the frontend store.
- `GameActionService` — the judge's per-seat mutations (assign / kill / revenge / revive / edit /
  force + transfer police / reset). Each mutates in place inside `mutate`; nicknames are synced
  best-effort via `jda?.syncNickname` (owner/hierarchy/no-op guards folded in). `assign`/`reset` delegate the bulk Discord
  work to `DiscordOpsService`. **Kill routes through `DeathService`** (not an inline card flip), then
  `win.check`.
- `DeathService` is the **single place a death is applied** — shared by `NightOrchestrator`,
  `GameActionService`, and `DayOrchestrator`. `applyDeath`/`killSeat` mark the card dead, sync the
  nickname, and run the cross-cutting ROLES.md rules once: death-revenge arming (`DEATH_REVENGE`;
  白狼王 only via 自爆 per `REVENGE_ON_SELF_DESTRUCT_ONLY`), 殉情 cascade (lover/charm bond, with the
  騎士-duel exemption), and 隱狼 auto-death. It returns the full `List<DeathInfo>` it produced;
  **logging/announcing stays with the callers**. The `DeathCause` enum gates these rules — pass the
  right cause (NIGHT/POISON/EXPEL/KNIGHT_DUEL/SELF_DESTRUCT/JUDGE/LOVER/TEAM). Never flip
  `card.dead` by hand outside this service.
- `DayOrchestrator` is the day-side mirror of `NightOrchestrator` (a `DiscordInteractionHandler`):
  it runs dawn/last-words, the speech flow, and the police-election + expel polls over
  `GameSession.speech`/`poll`, with all sequencing/tally in the pure `SpeechService`/`PollEngine`.
  It also owns the **day-phase role actions**: 騎士 決鬥 (`DuelResolver` → `DeathService`), 自爆
  (白狼王 带人 / 血月使徒 night seal), 白癡 翻牌免疫放逐, and the 血月使徒 last-wolf expel survival.
  `*Internal` helpers operate on an already-loaded session (no nested `mutate`); the public methods
  each wrap one `mutate` and return whether the game must enter night.
- `NightOrchestrator` runs a live night end-to-end and **is** the `DiscordInteractionHandler`
  (registers itself in `@PostConstruct`): plan via `NightPlanner` → prompt actors (select menus;
  wolves get vote buttons) → collect into the persisted `nightState` → resolve via `NightResolver`
  on the `GameScheduler` deadline **or** once `isComplete`. Resolution applies deaths, announces
  investigations, re-checks win, and advances `phase` to DAWN/OVER. The interaction-rule matrix
  lives in `NightResolver`, **not** here — this class is wiring + persistence of intents/votes.
- `ServerProvisioningService` handles `/server create|delete` + bot-join provisioning (delegating the
  Discord build to `discord/GuildProvisioner`); `CommandRouter` is `DiscordBot`'s single
  `DiscordCommandHandler` and fans `/server` here and `/game detonate` to
  `DayOrchestrator.detonate` (wolf-only player 自爆, gated on the caller's current identity being a
  wolf). `DiscordOpsService` enforces **critical-before-cosmetic** (FEATURES
  §10.2): role grants + nicknames finish as one phase before any notification messages, streamed
  through `BulkOperationEngine` over the WS.
