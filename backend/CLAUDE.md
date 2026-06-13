# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Backend for WerewolfHelper. See the repo-root `CLAUDE.md` for cross-cutting context; this file is
the backend deep-dive. Kotlin · Spring Boot 4.0.2 · Kotlin 2.3 · Java 25 · MongoDB · JDA 6 · Gradle
(wrapper 9.3.1). Package root: `dev.robothanzo.werewolf`.

## Commands

- `./gradlew build` — compile + tests + `bootJar`.
- `bootJar` **builds the frontend** (`yarn install && yarn build` in `../frontend`) and packs its
  `dist/` into the jar at `BOOT-INF/classes/static`, so production is one self-contained jar that
  serves the SPA from `classpath:/static`. `test`/`compileKotlin` stay node-free; pass
  `-PskipFrontend` for a backend-only jar.
- `./gradlew test` — all tests, including `ApplicationContextTest` (boots the full context against
  embedded MongoDB, no Discord token).
- `./gradlew test --tests "*.PollEngineTest"` / `--tests "*.NightResolverTest.魔術師*"` — one class /
  one method.
- `./gradlew bootRun` — needs MongoDB on `localhost:27017`; `DISCORD_TOKEN` optional (no token →
  no-op gateway, REST/WS still serve). Scalar UI at `/scalar`, OpenAPI JSON at `/v3/api-docs`.
- Env vars (all optional): `MONGODB_URI`, `DISCORD_TOKEN`, `DISCORD_CLIENT_ID`,
  `DISCORD_CLIENT_SECRET`, `DISCORD_REDIRECT_URI`, `DISCORD_SERVER_CREATORS` (comma-separated user
  ids allowed to run `/server create`), `DASHBOARD_BASE_URL`.

## Package map (where things live)

- `domain/` — Mongo documents. `GameSession` (id = guildId) holds settings, the identity `pool`,
  `seats`, `phase`/`day`, `policeSeat`, `discordIds`, and `nightState`. `Seat` tracks **soft death
  per `IdentityCard`** (a seat is alive while any card lives) plus flags + provisioned `roleId`/
  `channelId`. `GameLogEntry` persists key+params **and** rendered zh-TW text.
- `i18n/` — `Msg` over a zh-TW `MessageSource`. Every player-facing string is a key; log entries
  store the key + rendered text.
- `game/roles/` — `Role` interface, `RoleRegistry` (collects all `Role` `@Component`s), the 27
  identities in `impl/`, `RoleIds` constants. Faction is explicit data; `Faction.fromName` is the
  fallback rule.
- `game/night/` — the dependency-graph night engine. `Effect` (channels), `NightAbility`
  (`reads`/`writes`), `NightPlanner` (DAG → waves, cycle detection), `NightResolver` (interaction
  matrix → deaths), `NightDeclarationsBuilder` (persisted `NightState` → `NightDeclarations`),
  ability beans in `abilities/`.
- `game/win/`, `game/assign/`, `game/vote/`, `game/speech/`, `game/flow/` — pure, deterministic
  logic: win-condition tally (金寶寶, parity +0.5), assignment (金寶寶 cap, clone copy, wolf-second
  ordering), `PollEngine` (shared by police election + expel), `SpeechService` (wrap-around order +
  interrupt majority), `GameFlowService` (phase machine) + the cancellable `GameScheduler`.
- `discord/` — the `DiscordGateway` seam: `NoOpDiscordGateway` + `JdaDiscordGateway`, `DiscordConfig`
  (bean selection), `NicknameService` (`[死人] 玩家NN [警長]`), interaction/command handler
  interfaces, `SoundCue`.
- `ops/` — `BulkOperationEngine` (barrier + per-item isolation + percent + timeout).
- `service/` — orchestration: `GameSessionService` (load/log/save/**broadcast**/`mutate`),
  `SnapshotService` (builds the wire `GameSnapshot`), `GameActionService` (kill/revive/edit/police/
  reset), `NightOrchestrator`, `ServerProvisioningService`, `DiscordOpsService`.
- `controller/` + `controller/dto/` — REST controllers (auto-branch OpenAPI style: `@Tag` /
  `@Operation` / `@ApiResponses`) + the `ApiResponse` envelope and DTOs. `security/` — OAuth/session,
  `identityUtils`, role derivation, `@CanManageGuild`/`@CanViewGuild`. `websocket/` — the per-guild
  hub (serialized sends, snapshot + progress events, handshake auth) + config.

## How a turn flows (read these together)

1. A controller action (or a Discord interaction) calls `GameSessionService.mutate(guildId) { ... }`.
2. The block mutates the `GameSession`; `mutate` then **saves and broadcasts a full snapshot** built
   by `SnapshotService` over the per-guild WebSocket.
3. Long Discord operations (assignment/reset, provisioning) run through `BulkOperationEngine` on a
   coroutine and stream percent + log lines via `GameWebSocketHandler.broadcastProgress`.
4. Night: entering `Phase.NIGHT` (in `GameController.start`/`next`) triggers
   `NightOrchestrator.startNight` → plan → prompt via gateway → collect into `nightState` → resolve.

## Testing approach

Game logic is pure and tested without Spring (`support/TestFixtures` builds a real `RoleRegistry`
over the canonical beans + the actual message bundle; inject a seeded `Random` for assignment). The
**night resolver test is the interaction-rule matrix** — extend it when adding interaction rules.
Repository/wiring is covered only by `ApplicationContextTest` (embedded Mongo). There are no
live-Discord tests; the gateway is exercised via the `NoOp` impl.

## Gotchas

- Spring Boot 4 → **Jackson 3** (`tools.jackson.databind.ObjectMapper`, `tools.jackson.module:
  jackson-module-kotlin`). Don't import `com.fasterxml...ObjectMapper` for beans.
- **JDA 6 components** are under `net.dv8tion.jda.api.components.*`, not `interactions.components.*`.
- Resource bundle file is `messages_zh_TW.properties` (underscore). Test `application.properties`
  shadows the main one — re-declare custom `@Value` keys there.
- `GameScheduler` jobs must be cancellable; every interrupt path calls `cancel`/`cancelAll`
  (FEATURES §6 — past deadlocks lived here).
