# WerewolfHelper — Feature Inventory & Rebuild Reference

This document is the authoritative feature list of the current system, written to prepare for a
full rebuild and restructuring. It covers the **backend** (Kotlin / Spring Boot / JDA) and the
**frontend** (React dashboard), plus a section of **learned implementation details** — hard-won
fixes (rate-limit-safe Discord task queueing, nickname permission pitfalls, WebSocket
thread-safety, etc.) that any rebuild must preserve.

---

## 1. System Overview

WerewolfHelper runs Chinese Werewolf (狼人殺) games on Discord. One Discord guild = one game
session. The bot automates guild setup, role (identity) distribution, day flow (speeches, timers),
police/sheriff elections, expel polls, death & win-condition tracking, and voice-channel audio
cues. A judge-facing web dashboard mirrors and controls the game in real time over WebSocket.

| Layer | Stack |
|---|---|
| Backend | Kotlin 2.3.0, Java 25, Spring Boot 4.0.2 (web, security, websocket, data-mongodb), JDA 6.3.0, JDAInteractions 0.2.0, LavaPlayer 2.2.6 + jdave natives, discord-webhooks, discord-oauth2-api, MongoDB (sessions + Spring Session store) |
| Frontend | React 18, TypeScript 5, Vite 5, Tailwind CSS 3 (`dark` class strategy), react-router-dom 7, lucide-react icons. **No external state/WS/i18n libraries** — hand-rolled singletons |
| Persistence | MongoDB `WerewolfHelper` db: `sessions` (game state), `http_sessions` (Spring Session, 7-day timeout) |
| Transport | REST under `/api/**`, raw WebSocket at `/ws?guildId=...` (JSON text frames) |
| CI | GitHub Actions: Gradle build on JDK 25 |
| Config (env) | `TOKEN` (bot token), `DATABASE` (Mongo URI), `DASHBOARD_URL`; Discord OAuth client config |

LOC for scale: backend ~6,600 lines Kotlin, frontend ~4,800 lines TS/TSX.

---

## 2. Backend Features

### 2.1 Bootstrap (`WerewolfApplication.kt`)
- `@SpringBootApplication` + `@EnableScheduling`.
- Extracts bundled MP3 sound files from the jar to a `sounds/` working directory at startup
  (LavaPlayer plays from local filesystem, not classpath).
- Hardcoded constants: `AUTHOR` (bot owner ID), `SERVER_CREATORS` (3 user IDs allowed to
  create game servers), and the canonical list of **27 supported identities** (狼人, 女巫, 獵人,
  預言家, 平民, 狼王, 狼美人, 白狼王, 夢魘, 混血兒, 守衛, 騎士, 白癡, 守墓人, 魔術師, 黑市商人,
  邱比特, 盜賊, 石像鬼, 狼兄, 狼弟, 複製人, 血月使者, 惡靈騎士, 通靈師, 機械狼, 獵魔人).
- `StaticBridge` `@Component` copies Spring beans into `companion object` statics so legacy
  non-Spring code (commands/listeners) can reach services. **Rebuild note: this is a transitional
  anti-pattern; commands/listeners should become Spring beans with constructor injection.**

### 2.2 Data Model (MongoDB)

**`Session`** (`sessions` collection, unique-indexed `guildId`):
- Guild infrastructure IDs: `courtTextChannelId`, `courtVoiceChannelId`, `spectatorTextChannelId`,
  `judgeTextChannelId`, `judgeRoleId`, `spectatorRoleId`, `owner`.
- Game config/state: `doubleIdentities`, `hasAssignedRoles`, `muteAfterSpeech` (default true),
  `roles` (the identity pool), `players` (map "1".."N" → Player), `logs`.
- `hasEnded(simulateRoleRemoval)`: win-condition evaluation. Counts **per living role** (not per
  player) into wolves / gods / villagers; supports simulating a death before committing it.
  Returns NOT_ENDED / VILLAGERS_DIED / GODS_DIED / WOLVES_DIED / JIN_BAO_BAO_DIED /
  EQUAL_PLAYERS (the last only in single-identity mode).
- **`Session.Player`**: `id` (seat number), `roleId` + `channelId` (the player's dedicated Discord
  role & private text channel), `userId` (assigned member), `roles`, `deadRoles` (soft-death for
  double identities: alive while `deadRoles.size < roles.size`), flags `jinBaoBao`, `duplicated`
  (複製人), `idiot` (白癡), `police`, `rolePositionLocked`.
  - Computed `nickname` convention: `[死人] 玩家NN [警長]` (dead prefix, zero-padded seat, police suffix).
  - Faction classifiers: `isWolf` = name contains 狼 or is 石像鬼/血月使者/惡靈騎士; `isVillager` = 平民;
    `isGod` = everything else.
- **`LogEntry`** + **`LogType`**: ~40 typed audit events (player/speech/police/expel/system/judge
  categories with severity), appended via `$push` so logs persist incrementally.

**`AuthSession`** (stored inside Spring Session): Discord `userId`, `username`, `avatar`,
selected `guildId`, and `UserRole` ∈ {JUDGE, SPECTATOR, PENDING, BLOCKED}; helpers
`isPrivileged` (judge|spectator) etc.

**In-memory (non-persisted) flow state** — lost on restart, a known rebuild consideration:
- `SpeechSession`: speech `order` (list of players), `interruptVotes`, `speakingThread`,
  `currentSpeechEndTime`, `totalSpeechTime`, completion callback.
- `PoliceSession`: state machine value, `stageEndTime`, `candidates`
  (ConcurrentHashMap seat→Candidate), the Discord message hosting the buttons.
- `Candidate`: `electors` (voter user IDs), `quit`, `expelPK` (runoff flag);
  `getVotes(police)` gives the police elector's vote **1.5 weight**.

### 2.3 Slash Commands (JDAInteractions)

- **`/server`** — `create` (server-creator only; stores a pending setup config + invite link;
  actual guild build happens on bot join — Discord no longer lets bots create guilds),
  `delete`, `list` & `session` (author-only debug), `dashboard` (prints dashboard URL),
  `roles list|add|delete` (identity pool management with autocomplete, **capped at 25
  entries** per Discord limit), `set double_identities|mute_after_speech|players`.
- **`/speech`** — `auto` (full day speech flow), `start <duration>` (standalone timer with
  30s-remaining and end sounds), `interrupt`, `mute_all`, `unmute_all`.
- **`/player`** — `judge`/`demote` (grant/revoke judge role), `died [last_words]`,
  `assign` (random identity distribution), `roles` (list), `force_police`.
- **`/poll`** — `expel` (expel vote with runoff/PK support), `police enroll` (full election),
  `police start` (force-skip to voting).
- Authorization helpers: `isAdmin` (guild ADMINISTRATOR), `isAuthor`, `isServerCreator`.

### 2.4 Discord Listeners
- **ButtonListener**: routes all button/entity-select interactions — police/expel votes
  (`votePolice{seat}` / `voteExpel{seat}`), vote toggle & switch semantics, enrollment button,
  role-order swap, police transfer confirm/destroy, timer termination. Validates voter
  eligibility (alive, not a candidate, dead-idiot restrictions) and broadcasts state to the
  dashboard after each vote.
- **MessageListener**: wolf-chat relay. Messages in a wolf's (or 夢魘 / 金寶寶 partner's) private
  channel are mirrored via **webhooks** (one cached webhook per channel, impersonating the
  sender's name/avatar) into teammates' channels and the judge channel.
- **MemberJoinListener**: re-grants judge role to the owning server-creator; if the game already
  started (`hasAssignedRoles`), new joiners automatically get the spectator role (prevents
  latecomers seeing/saying things they shouldn't).
- **GuildJoinListener**: on bot join, runs `SetupHelper` if a pending `/server create` config
  exists (with a role-add fallback trigger in case permissions arrive late); on guild leave,
  deletes the session and interrupts any running speech.

### 2.5 Guild Setup (`SetupHelper`)
Builds the entire game guild from scratch: renames guild + wolf icon, wipes existing channels,
creates judge role (yellow, admin), N player roles (random colors, hoisted) each with a private
text channel (player can view/send; spectators view-only; @everyone denied), spectator role,
and the core channels — 法院 court text+voice, 法官 judge text, 旁觀者 spectator text — then
persists the `Session`.

### 2.6 Services
- **DiscordServiceImpl**: builds JDA (intents GUILD_MEMBERS + MESSAGE_CONTENT, member cache ALL,
  jdave audio), registers listeners + command package, presence "狼人殺 by Hanzo". Accessors for
  guild/member/channels.
- **GameSessionServiceImpl**: session CRUD; JSON serialization of the full game state (session +
  players incl. Discord names/avatars + speech + police + expel + logs); `broadcastUpdate(guildId)`
  pushes the whole state as an `UPDATE` frame to every dashboard client of that guild;
  `updateUserRole` mutates stored AuthSessions (judge promote/demote from the dashboard);
  `updateSettings`, `getGuildMembers`.
- **RoleServiceImpl**: identity pool add/remove/list; `assignRoles` — the most complex op (see
  §4.1/§4.2 for the queueing lore). Validates pending member count == player count and pool size
  == players × (1|2); shuffles both; handles 白癡 flag, 複製人 duplication (copies the other
  identity), wolf-identity ordering (wolf goes second), and 金寶寶 (double-civilian) including the
  historical "no more than allowed 金寶寶" fix; sends per-player identity embeds with a
  "change role order" button that **locks 120 s later** via scheduled task; posts a summary embed
  + dashboard link to judge & spectator channels.
- **PlayerServiceImpl**: `setPlayerCount` — three dependent phases (delete excess roles/channels →
  create roles → create channels using the just-created roles); `updatePlayerRoles`,
  `switchRoleOrder` (honors `rolePositionLocked`), `setRolePositionLock`.
- **GameActionServiceImpl**: `resetGame` (clears all player state, strips roles, resets nicknames
  with owner/permission guards, wipes logs); `markPlayerDead` (soft-kill one role → court
  announcement → win-condition check → police transfer flow if fully dead → optional last-words
  speech); `revivePlayer` / `reviveRole`; `setPolice`; `broadcastProgress` (PROGRESS frames for
  the dashboard overlay).
- **SpeechServiceImpl**: auto speech flow (police picks UP/DOWN via select menu, else random;
  "white day separator" lines posted to channels), per-speaker countdown threads, skip button,
  majority **interrupt vote**, last-words speeches, standalone timers with audio cues,
  `setAllMute`/unmute, session interruption. Timer threads are interruptible — an earlier
  rewrite removed deadlock potential here.
- **PoliceServiceImpl**: election state machine `NONE → ENROLLMENT(30s) → SPEECH →
  UNENROLLMENT(20s) → VOTING(30s) → FINISHED`, with audio cues (enroll sound at start,
  10s-remaining sound at the 20s mark, poll sound at voting start). Zero candidates ⇒ badge torn
  up; one candidate ⇒ auto-elected; tie ⇒ PK runoff (speech + revote). Enrollment/withdrawal is
  **only allowed in states whose `canEnroll`/`canQuit` permit it** (historical fix: people could
  enroll after voting began). Badge transfer on death: select-menu + 移交/撕毀 buttons with a
  timeout that defaults to destruction; force-police supported.
- **Audio / AudioPlayerSendHandler**: LavaPlayer → JDA Opus bridge; 7 sound resources
  (expel_poll, police_enroll, police_poll, enroll_10s_remaining, poll_10s_remaining,
  timer_30s_remaining, timer_ended) played into the court voice channel.

### 2.7 REST API (all JSON, cookie-session auth)

| Area | Endpoints |
|---|---|
| Auth | `GET /api/auth/login` (Discord OAuth redirect; scopes identify, guilds, guilds.members.read), `GET /api/auth/callback` (role pre-computed: ADMINISTRATOR/MANAGE_SERVER ⇒ JUDGE else SPECTATOR), `POST /api/auth/select-guild/{guildId}`, `GET /api/auth/me`, `POST /api/auth/logout` |
| Sessions | `GET /api/sessions` (filtered to guilds the user is in), `GET /api/sessions/{guildId}` |
| Game | `POST .../start`, `POST .../reset`, `PUT .../settings`, `POST .../player-count`, `GET .../members` |
| Players | `GET .../players`, `POST .../players/assign`, per-player: `roles`, `role` (dashboard user-role), `switch-role-order`, `role-lock`, `died?lastWords=`, `revive`, `revive-role`, `police` |
| Identity pool | `GET .../roles`, `POST .../roles/add`, `DELETE .../roles/{role}` |
| Speech | `POST .../speech/auto`, `skip`, `interrupt`, `police-enroll`, `order` (UP/DOWN), `confirm`, `manual-start` (timer), `mute-all`, `unmute-all` |

### 2.8 Security
- Spring Security: CSRF off; CORS allowlist (localhost:5173, wolf.robothanzo.dev);
  `/api/auth/**`, `/ws/**`, `/actuator/**` open; everything else authenticated; JSON 401/403 bodies.
- `UserSessionFilter` lifts `AuthSession` from the HTTP session into the SecurityContext with
  `ROLE_{userRole}` authorities.
- Method security via `@CanViewGuild` (`identityUtils.canView`: guild match + judge|spectator)
  and `@CanManageGuild` (guild match + judge) on controller methods.
- WebSocket handshake (`GlobalWebSocketHandler`): rejects unauthenticated, non-privileged, or
  guild-mismatched connections (guildId query param must equal the authorized guild).

### 2.9 WebSocket Protocol (backend → frontend)
- `UPDATE` — full session JSON snapshot (the dashboard's source of truth).
- `PROGRESS` — `{message?, percent?}` for long-running operations (assignment, reset, resize).
- `PING`/`PONG` heartbeat (client sends PING every 15 s).
- Per-guild registry `ConcurrentHashMap<guildId, Set<WebSocketSession>>` with empty-set cleanup.

---

## 3. Frontend Features (React Dashboard)

### 3.1 Shell & Routing
- Provider stack: `ThemeProvider → BrowserRouter → AuthProvider → App`.
- Routes: `/login`, `/auth/callback`, `/access-denied`, `/` (server selection),
  `/server/:guildId/*` (dashboard with nested routes: `""` main, `settings`, `spectator`, `speech`).
- Vite dev proxy: `/api` → `localhost:8080`, `/ws` → ws upgrade; production served same-origin
  (cookie auth, no CORS).
- Sidebar: role-aware navigation (judges see Dashboard + Settings; judges & spectators see
  Spectator View + Speech Manager), user profile with **judge↔spectator simulation toggle**,
  bot connection indicator (driven by WS state), theme toggle, server switch, logout.

### 3.2 Auth Flow
- `AuthContext`: `GET /api/auth/me` on mount; `login()` redirects to `/api/auth/login?guild_id=`;
  logout posts and clears.
- `useDashboardAuth` guard: unauthenticated → `/login`; PENDING → `select-guild` confirmation;
  guild mismatch → re-select; BLOCKED → `/access-denied`; SPECTATOR → forced into spectator view;
  **active players (users holding an in-game player role) are locked out** of the dashboard.
- `AuthCallback`: 500 ms cookie-settlement delay, then re-checks auth and routes to the guild.
- `SessionExpiredModal`: shown when the WS disconnect reason indicates a rejected/expired session.

### 3.3 Server Selection
- `GET /api/sessions` → card grid (guild icon, name, player count), loading/error/empty states.

### 3.4 Game Dashboard
- `useGameState`: WS-first state. `UPDATE` frames map backend players → UI players
  (police→isSheriff, deadRoles, jinBaoBao, duplicated, rolePositionLocked…); `PROGRESS` frames
  feed the overlay (percent + terminal-style log lines; error detection by substring 錯誤 /
  Error / Failed); initial REST fetch on mount; 1 s local timer tick.
- `useGameActions`: every judge action (kill w/ last-words confirm, revive, revive single role,
  edit roles, transfer/force sheriff, switch role order, start game, reset with overlay,
  random assign with overlay, manual timer, mute/unmute all, assign/demote judge via member
  picker). Optimistic local updates; the next WS `UPDATE` reconciles.
- Components: `GameHeader` (phase + day count + current speaker + countdown, start/next-phase
  buttons), `PlayerCard` (avatar, sheriff/金寶寶/lock badges, color-coded identities — wolf red,
  civilian emerald, god indigo — strikethrough dead roles clickable to revive, swap-order button,
  kill/revive/edit actions, flash & slide animations on changes), `PlayerEditModal` (role
  dropdowns honoring double identities, lock toggle, police transfer), `PlayerSelectModal`
  (searchable member picker), `GameLog` (typed log entries + grouped admin command buttons with
  two-stage reset confirm), `VoteStatus` (candidates, voters with avatars, weighted counts,
  progress bar, countdown), `DeathConfirmModal` (last-words checkbox), `TimerControlModal`
  (min/sec inputs + 30/60/90/180s presets), `SettingsModal` (backend URL override + test
  connection), `ProgressOverlay` (processing/success/error, progress bar, auto-close on success).

### 3.5 Game Settings Page
- Auto-saving toggles (500 ms debounce, Saving→Saved indicator): mute-after-speech,
  double identities.
- Player count stepper → `POST .../player-count` (progress overlay while Discord
  roles/channels are rebuilt).
- Identity pool editor: add (datalist of preset identities) / remove with counts; random assign.

### 3.6 Speech Manager
- Idle: start auto speech / start police enrollment.
- Police phases: enrollment/unenrollment banners with `stageEndTime` countdowns, candidate grid,
  voting via `VoteStatus`.
- Active speech: large `SpeakerCard` (animated mic, countdown, red <10 s), skip & interrupt
  controls, upcoming-speaker queue, UP/DOWN force-order buttons, 400 ms swipe transitions.

### 3.7 Spectator View
- Read-only faction overview: wolves / gods / villagers-or-金寶寶 cards with alive counts and
  progress bars (adapts to double-identity mode), win-condition explainer, read-only player grid.

### 3.8 Cross-cutting
- `lib/api.ts`: singleton fetch client, `credentials: include`, normalized error handling, all
  ~30 endpoints typed.
- `lib/websocket.ts`: singleton WS client — auto-reconnect with exponential backoff (1 s × 1.5,
  cap 10 s), 15 s PING heartbeat, connect/disconnect handler registry, session-expiry detection
  from close reasons, `useWebSocket` React wrapper.
- `lib/i18n.ts` + `locales/zh-TW.json`: ~330-key hand-rolled translation function (dot paths,
  `{param}` substitution, key fallback). UI is fully Traditional Chinese.
- `ThemeProvider`: dark/light with localStorage persistence + system preference fallback.
- Responsive (mobile-first sidebar collapse, 1/2/3-column player grid), full dark-mode coverage.

---

## 4. Learned Implementation Details (must survive the rebuild)

These are behaviors and fixes earned through production failures. Several map directly to git
history (`assignment stability enhancement`, `Fixed police transfers`, `Removed potential dead
locks`, `Mute newly joined members if the game has already begun`, …).

### 4.1 Discord task queueing — `ActionTask` / `runActions` (`utils/DiscordActionRunner.kt`)
The core mechanism that stopped roles/nicknames from silently failing to apply:
- Every bulk Discord mutation is wrapped as `ActionTask(restAction, description, onSuccess?)` and
  executed through `Collection<ActionTask>.runActions(statusLogger, progressCallback,
  startPercent, endPercent, timeoutSeconds)`.
- Tasks are submitted via JDA's `queue()` so **JDA's internal rate-limiter serializes them per
  route/bucket** — no manual sleeps, no thundering herd of blocking `complete()` calls.
- A `CompletableFuture` + `AtomicInteger` barrier blocks the caller until *all* tasks finish or
  the timeout fires. **Individual failures do not abort the batch** — each task logs
  `[完成]`/`[失敗] <description>: <error>` through the status logger (streamed to the dashboard
  overlay) and still counts toward completion, so one bad member doesn't strand the whole game.
- Progress is mapped linearly into a `[startPercent, endPercent]` window so multi-phase
  operations compose into one smooth 0–100 % bar.
- Timeout raises after logging a warning (部分 Discord 變更操作逾時) so the operator sees exactly
  which items completed before the stall.

### 4.2 Priority vs. notification phases in role assignment (`RoleServiceImpl.assignRoles`)
The "assignment stability enhancement" fix split the single task batch in two:
1. **Priority batch** — role grants + nickname changes — runs first, progress 10→60 %,
   timeout 60 s.
2. **Notification batch** — per-player identity embeds + judge/spectator summaries — runs only
   after the priority batch completes, progress 60→95 %, timeout 120 s.

Rationale: member PATCHes (nicknames especially) hit Discord's strictest per-guild rate buckets.
When mixed with a flood of channel messages in one batch, the messages consumed time/attention
while role/nickname PATCHes timed out half-applied. Separating them guarantees the
game-critical mutations land (or visibly fail) before any cosmetic messaging starts.

### 4.3 Permission preflight for nicknames
Never blindly `modifyNickname`:
- Check `guild.selfMember.canInteract(member)` first; if false, log a visible warning
  (權限不足) instead of queueing a doomed request.
- Skip the guild **owner** entirely on nickname reset (bots can never rename the owner).
- Only send the PATCH when the nickname actually differs (`member.effectiveName != newNickname`)
  to avoid wasting rate-limit budget.

### 4.4 Phase-dependent resource creation (`PlayerServiceImpl.setPlayerCount`)
Resizing the game runs three *dependent* batches, each barriered by `runActions` before the next
starts: delete excess roles/channels (0–30 %) → create new player roles (30–60 %) → create the
matching private channels **using the roles created in the previous phase** (60–95 %). Created
entities are captured through the `onSuccess` callback into a `ConcurrentHashMap` (callbacks fire
on JDA threads). Channel permission overrides are set at creation time (player view/send,
spectator view-only, @everyone denied + no slash commands) rather than patched afterwards.

### 4.5 WebSocket robustness
- **Server**: sends are wrapped in `synchronized(session)` — Spring WS sessions throw on
  concurrent writes, and broadcasts originate from multiple threads (JDA callbacks, schedulers,
  HTTP threads). Per-guild session sets are `ConcurrentHashMap.newKeySet()` with empty-set
  cleanup on disconnect to avoid leaks. Handshake re-validates auth (privileged role + exact
  guild match) so the WS layer never trusts the URL.
- **Client**: 15 s PING keep-alive (proxies kill idle WS), exponential-backoff reconnect
  (1 s → ×1.5 → cap 10 s), and session-expiry is *detected from the close reason text*
  ("No user in session" / "Rejected WS connection") to pop the re-login modal instead of
  reconnect-looping forever.
- State sync model: the dashboard applies optimistic local updates but treats the next full
  `UPDATE` snapshot as truth — no per-field patching, no reconciliation bugs.

### 4.6 Game-flow lore (fixes encoded in current behavior)
- **Police election lockout**: enrollment/withdrawal buttons are gated by the state machine's
  `canEnroll`/`canQuit` — added after players enrolled/un-enrolled *during voting*.
- **Delay before police poll**: voting doesn't start instantly; the 20 s unenrollment buffer and
  scheduled `next()` transitions came from the "delay before starting the police poll" fix.
- **Police vote weight**: the elected police's vote counts 1.5; win-condition math likewise adds
  ±0.5 to a faction when the police badge sits on it.
- **Police is a nickname suffix** (`玩家03 [警長]`), not a separate Discord role — earlier
  role-based attempts broke transfers ("Fixed police transfers not working").
- **Badge transfer on death** defaults to destruction on timeout; transfer UI is a select menu +
  confirm/destroy buttons (two historical fix commits live here).
- **金寶寶 cap** ("Fixed jin bao bao > 2"): double-civilian assignment re-rolls the second card
  from remaining non-civilian roles once the cap is reached.
- **Role-order lock**: in double-identity games players get 120 s to swap their two identities,
  then a scheduled task sets `rolePositionLocked` and announces it — prevents post-hoc swaps
  after hearing the night's information.
- **Late joiners**: if `hasAssignedRoles`, new guild members are immediately given the spectator
  role ("Mute newly joined members if the game has already begun").
- **Wolf chat relay** uses one *cached webhook per channel* for impersonated mirroring —
  creating webhooks per message hits the 10-webhook channel cap and rate limits.
- **Autocomplete capped at 25** entries (hard Discord limit; uncapped lists throw).
- **Dead idiot (白癡)** voting restrictions enforced in the button handler.
- **Speech timer threads** are interruptible and tracked per session ("Removed potential dead
  locks") — every flow interruption path must `interrupt()` the speaking thread.
- **Soft death** (`deadRoles`) instead of boolean-dead — required for double identities and for
  `hasEnded`'s simulate-removal lookahead (used to preview whether a kill ends the game).
- **Audio**: sounds must exist on the local filesystem for LavaPlayer (hence jar extraction);
  cues fire at fixed offsets (10 s remaining = scheduled at 20 s of a 30 s stage).
- **OAuth callback cookie race**: the frontend waits 500 ms before calling `/api/auth/me` —
  immediately after redirect the session cookie isn't reliably committed.

### 4.7 Known structural debt (targets for the restructure)
- `StaticBridge` statics + legacy `Database.initDatabase()` raw-driver path coexisting with
  Spring Data — unify on one data access layer.
- `SpeechSession`/`PoliceSession` are in-memory only: a bot restart mid-game loses speech/election
  state (game `Session` survives). Consider persisting or making flows resumable.
- Java `Timer`-based scheduling (`CmdUtils.schedule`) — single thread, swallows nothing; migrate
  to a managed scheduler.
- Mixed command-layer (JDAInteractions statics) vs service-layer logic; controllers call services,
  but slash commands sometimes duplicate flow logic.
- Frontend `next_phase`/`pause` are partly local-only (no backend phase state machine);
  day/night phase is largely cosmetic on the dashboard today.
- Hardcoded `SERVER_CREATORS`/`AUTHOR` IDs and dashboard URL fallback (`localhost:5173`).
