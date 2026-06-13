# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`service/` — the **orchestration layer**: it wires the pure `game/` engine to persistence, Discord,
and the WebSocket. This is where side effects live; keep new rules in `game/` and call them here.

- `GameSessionService` is the **funnel**: `mutate(guildId) { session -> ... }` loads, runs the block,
  **saves, then broadcasts a full snapshot** (snapshot-as-truth). Almost every state change goes
  through it. Also owns `log(...)` (typed, localized log entries: key + params + rendered text) and
  `broadcast`. Don't persist/broadcast by hand — call `mutate`.
- `SnapshotService` builds the wire `GameSnapshot` (`controller/dto`) consumed by the frontend store.
- `GameActionService` — the judge's per-seat mutations (assign / kill / revive / edit / force +
  transfer police / reset). Each mutates in place inside `mutate`; nicknames are synced best-effort
  via the gateway (`canInteract` preflight). `assign`/`reset` delegate the bulk Discord work to
  `DiscordOpsService`. **Kill is soft death of one `IdentityCard`**, then `win.check`.
- `NightOrchestrator` runs a live night end-to-end and **is** the `DiscordInteractionHandler`
  (registers itself in `@PostConstruct`): plan via `NightPlanner` → prompt actors (select menus;
  wolves get vote buttons) → collect into the persisted `nightState` → resolve via `NightResolver`
  on the `GameScheduler` deadline **or** once `isComplete`. Resolution applies deaths, announces
  investigations, re-checks win, and advances `phase` to DAWN/OVER. The interaction-rule matrix
  lives in `NightResolver`, **not** here — this class is wiring + persistence of intents/votes.
- `ServerProvisioningService` handles `/server create|delete` + bot-join provisioning (the
  `DiscordCommandHandler`). `DiscordOpsService` enforces **critical-before-cosmetic** (FEATURES
  §10.2): role grants + nicknames finish as one phase before any notification messages, streamed
  through `BulkOperationEngine` over the WS.
