# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

WerewolfHelper hosts Chinese Werewolf (狼人殺) games on Discord — **one Discord guild = one game
session** — with a real-time web dashboard for the judge (法官) and a read-only God's view for
spectators. The Discord bot and the dashboard are two thin interfaces over a single game engine.
The authoritative behaviour spec lives in [`instructions/`](instructions/) (`FEATURES.md`,
`GAMEPLAY.md`, `ROLES.md`, `UI_SPEC.md`) — treat those as the source of truth for game rules and
operational requirements (the §-numbers referenced in code comments point there). `ROLES.md` is the
per-role spec the death/day-action engine is verified against.

Monorepo: `backend/` (Kotlin · Spring Boot 4 · MongoDB · JDA · Gradle Kotlin DSL) and
`frontend/` (React 19 · Vite · TypeScript · Yarn · Lucide · framer-motion · zustand). The entire
UI/bot is Traditional Chinese; **zh-TW is the only locale**, but everything player-facing is keyed
for i18n (`backend/.../i18n/messages_zh_TW.properties`, `frontend/src/i18n/zh-TW.json`).

## Commands

### Backend (`cd backend`)
- `./gradlew build` — compile + all unit tests + bootJar.
- `./gradlew test` — run all tests. The suite includes `ApplicationContextTest`, which boots the
  **entire** Spring context against an **embedded MongoDB** (flapdoodle) and no Discord token, so
  it catches wiring breakage without external services.
- `./gradlew test --tests "dev.robothanzo.werewolf.game.night.NightResolverTest"` — a single class;
  `--tests "*.NightResolverTest.同守同救*"` for a single method.
- `./gradlew bootRun` — needs a local MongoDB on `localhost:27017`. **Without a `DISCORD_TOKEN` the
  bot degrades to a no-op gateway and the REST API + WebSocket hub still serve.** OpenAPI (Scalar)
  is at `/scalar`.
- The Gradle daemon's JDK and toolchain are Java 25; the wrapper is Gradle 9.3.1.

### Frontend (`cd frontend`)
- `yarn install`, then `yarn dev` (http://localhost:5173; proxies `/api` and `/ws` to `:8080`).
- `yarn typecheck` (`tsc --noEmit`), `yarn test` (vitest), `yarn build` (`tsc --noEmit && vite build`).
- The dashboard has a **demo mode**: if `/api/auth/me` fails (no backend), it seeds mock scenarios
  (`src/mock/scenarios.ts`) so every screen renders standalone. Real mode connects the WebSocket.

## Architecture — the load-bearing ideas

**Snapshot-as-truth.** After *every* state mutation the backend persists, then broadcasts a full
`GameSnapshot` (`controller/dto/GameSnapshot.kt`) to that guild's WebSocket clients. The funnel is
`GameSessionService.mutate { session -> ... }` — controllers/services call it; it saves and
broadcasts. The frontend store (`stores/gameStore.ts`) replaces its state wholesale from each
snapshot and diffs per-seat to drive the "this just changed" cue. Don't invent per-field patch
APIs; mutate the session and let the snapshot flow.

**The night phase is a dependency graph, not a priority list** (`game/night/`). This was the entire
reason for the rebuild — never reintroduce a flat `priority: Int`. Each `NightAbility` declares the
`Effect` channels it `reads`/`writes`; `NightPlanner` topologically sorts abilities into
**waves** (independent abilities run simultaneously; cycles fail fast). `NightResolver` applies the
GAMEPLAY.md interaction matrix (同守同救, 魔術師 swap redirection, 夢魘 fear-void, 狼美人 殉情,
poison-blocks-revenge, 獵魔人 hunt) to produce the death list. `NightOrchestrator` runs a live night:
prompts actors via Discord select menus / wolves via vote buttons, collects into the persisted
`GameSession.nightState`, and resolves on the scheduler deadline or once everyone has acted.
`NightDeclarationsBuilder` bridges `nightState` → `NightDeclarations` for the resolver.

**Roles are registry-driven for expansion** (`game/roles/`). `RoleRegistry` collects every `Role`
`@Component` bean (28 identities: the 27 FEATURES §2 canonical ones + 隱狼). Adding a role is one new
bean (plus optional `NightAbility` beans + i18n keys) — no engine, controller, or frontend change.
Faction is explicit registry data with `Faction.fromName` as the FEATURES §2 fallback. The frontend
renders identities purely from server data (`roleId` + localized name + `faction`) — **never
hardcode a role name in the UI**.

**Death is applied in one place** (`service/DeathService`). Night, judge-kill, expel, 決鬥, and 自爆
all route through `applyDeath`/`killSeat`, which marks the card dead, syncs the nickname, and runs
the cross-cutting ROLES.md rules once: death-revenge arming (獵人/狼王 unless suppressed; 白狼王 only
via 自爆), 殉情 cascade (邱比特 lover / 狼美人 charm, with the 騎士-duel exemption), and 隱狼
auto-death. Day-side role actions (`/seats/{n}/revenge|duel|self-destruct`) live in `DayOrchestrator`
alongside the speech/poll loop. Never re-implement card-death inline — call `DeathService`.

**Discord is behind the `DiscordGateway` seam** (`discord/`). `NoOpDiscordGateway` is the default
(boots without a token); `JdaDiscordGateway` is the full JDA implementation (provisioning, seat
role/channel grants, audio cues via lavaplayer, wolf-chat relay via cached webhooks, `/server`
slash command, night button/menu prompts, membership lifecycle). The gateway is wired in
`DiscordConfig` (picks the impl by token presence). Services that need to react to Discord events
(`NightOrchestrator`, `ServerProvisioningService`) implement a handler interface and register
themselves with the gateway via `@PostConstruct` (`setInteractionHandler` / `setCommandHandler`),
avoiding a constructor cycle.

**Bulk Discord mutations go through `ops/BulkOperationEngine`** (FEATURES §10.1–10.4): a barrier
that awaits every item, isolates per-item failures (`[完成]`/`[失敗]`), maps phases onto percent
sub-ranges, and streams progress over the WebSocket. Assignment/reset run through
`DiscordOpsService`, which enforces **critical-before-cosmetic** (§10.2): role grants + nicknames
complete as their own phase before any notification messages.

**Auth/authorization.** Discord OAuth2 → a Mongo-backed HTTP session (`security/CurrentUser`).
Endpoints are guarded by `@CanManageGuild` / `@CanViewGuild` meta-annotations, which `@PreAuthorize`
against the `identityUtils` bean → `DashboardRoleService` (judge/spectator derivation + the
**active-player lockout**: a logged-in user holding a seat can't open the dashboard).

## Toolchain gotchas (these have bitten before)

- **Spring Boot 4 uses Jackson 3 (`tools.jackson`)** as the primary `ObjectMapper`. Import
  `tools.jackson.databind.ObjectMapper`, not `com.fasterxml...`; the build depends on
  `tools.jackson.module:jackson-module-kotlin`. (Jackson 2 is still present transitively via JDA.)
- **JDA 6 moved components** to `net.dv8tion.jda.api.components.*` (`actionrow` / `buttons` /
  `selections`), not the pre-6 `interactions.components.*`.
- **The i18n bundle is `messages_zh_TW.properties`** (Spring underscore locale convention), even
  though the locale tag is `zh-TW`.
- `src/test/resources/application.properties` fully shadows the main one on the classpath —
  re-declare any `@Value` placeholders there or the context fails to load.

## Conventions

- Commit on a branch, never directly to `main`; this rebuild lives on the orphan `refactor` branch.
- Pure game logic (`game/`) is deterministic and unit-tested in isolation (inject `Random`, use the
  `support/TestFixtures` registry); side-effecting Discord/persistence sits behind services. Prefer
  adding logic to the pure layer and wiring it through a service.
