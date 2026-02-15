# Dashboard Features

Generated: 2026-02-10

This document lists every implemented feature of the dashboard (UI, admin, game, and integration) in a hierarchical and
exhaustive way. It was assembled by statically scanning the dashboard source.

---

## 📄 Top-level pages & routes

- `/login`
  - Discord OAuth login button (`/api/auth/login`)
  - Login screen visuals and restriction text
- `/` (Server selector)
  - `ServerSelector` list of sessions (guild name, icon, player count)
  - Retry / reload sessions
  - Back-to-login button
- `/server/:guildId` (Main server dashboard)
  - `MainDashboard` (stage content + navigator)
  - `GameHeader` (day, step, speaker, timer, actions)
  - Stage-specific content (see Stages section)
- `/server/:guildId/players` — Player management grid (`PlayerCard` list)
- `/server/:guildId/speech` — `SpeechManager` (speech management UI)
- `/server/:guildId/spectator` — `SpectatorView` (read-only spectator dashboard & stats)
- `/server/:guildId/settings` — `GameSettingsPage` (game configuration UI)
- `/access-denied` — Access denied / blocked user flow
- Session-expired handling (`SessionExpiredModal` + logout/redirect)

---

## 🎯 Main game flow / stage features (MainDashboard)

- Stage navigator UI (jump to any stage)
- Start game & Assign roles controls
- Next step / Skip stage
- Stage-specific content and rendering:
  - SETUP: assign roles, start game
  - NIGHT_PHASE: `NightStatus` (full night management UI)
    - **Overview header**
      - Night number, countdown timer, remaining time display (minute:second)
      - Phase-aware behavior (phase start/end times derived from session state)
    - **Tabs & auto-switching**
      - Two main tabs: `狼人投票` (Werewolf Voting) and `職業行動` (Role Actions)
      - Auto-switches to `actions` tab when phase becomes `ROLE_ACTIONS` or `WOLF_YOUNGER_BROTHER_ACTION`
    - **Werewolf Voting Screen**
      - Discussion/messages pane:
        - Shows werewolf chat messages with avatars, timestamps, sender names
        - Auto-scroll to newest messages
        - Groups consecutive messages from the same sender when within a 5-minute window (continuation
          detection)
        - Visual styles for continuation / grouping and hover effects
      - Voting result panel:
        - Lists per-vote entries (voter → target)
        - Displays "跳過" (skip) when applicable
        - Shows voter name and target name/avatar (or placeholder for unknown IDs)
        - Animated entries and incremental listing as votes come in
    - **Role Actions Screen**
      - Wolf kill summary (top target):
        - Displays computed top wolf target name, vote count, and avatar; shows special icon for
          skip/fast-forward
      - Grid of role action cards (non-wolf actions):
        - Each card shows actor name/avatar, actor role label, localized action label
        - Target section shows target name/avatar or '跳過' if skipped
        - Status badge with visual states: SUBMITTED (green), SKIPPED (amber), ACTING (blue), PROCESSED (indigo)
        - Role-specific border colors (wolf, witch, hunter, seer, guard, etc.) for quick scanning
        - Deduplication by actor (handles backend duplication issues)
        - Animation delays and responsive grid behavior
    - **Data enrichment & handling**
      - Maps raw session state IDs to player nicknames and userIds (fallback to `玩家 {id}` when unknown)
      - Computes wolf vote counts and resolves top target or shows `未定` if no consensus
      - Sorts/enriches messages, votes, and statuses for display
    - **Styling & UX**
      - Custom scrollbar styling for message lists
      - Animated transitions and slide/fade-in effects
      - Dark-theme friendly styling consistent with rest of dashboard
  - DAY_PHASE: day overview
  - SHERIFF_ELECTION / SPEECH_PHASE: `SpeechManager` integration
  - DEATH_ANNOUNCEMENT: dead players display, last words timer & progress
  - VOTING_PHASE: `VoteStatus` (candidates, voters, timer)
- Stage animation transitions and enter/exit animations

---

## 🧑‍⚖️ Player management features

- Player grid (`PlayerCard`) shows:
  - Avatar & nickname (`DiscordAvatar`, `DiscordName`)
  - Role list (single or double identities)
  - Dead roles visually struck-through; revive-role action
  - Badges: police (sheriff), jinBaoBao, dead indicator
  - Lock/unlock role-position visuals and transient lock animation
  - Switch role order (if unlocked & double identity)
  - Kill (mark dead) and Revive actions
  - Edit button (opens `PlayerEditModal`)
- `PlayerEditModal`:
  - Edit primary/secondary role(s)
  - Lock/unlock role position (`setRoleLock`)
  - Transfer police badge to another player
- `PlayerSelectModal`:
  - Searchable, filtered selection for assign/demote judge, force police, etc.
- `DeathConfirmModal` for confirmation when marking a player as dead
- Global orchestration: `PlayerManager` handles modals, logs, timer modal, spectator simulation
- Player info caching / fetch (`PlayerContext`) for resolving names/avatars

---

## 🗣️ Speech management (SpeechManager & SpeakerCard)

- Start/stop automatic speech process
- Start police enrollment mode (police select)
- Display current speaker, countdown, and remaining order
- Confirm speech order (judge confirm)
- Skip current speaker (move to next)
- Interrupt entire speech process (interrupt action)
- Start auto-speech, judge override (force up/down ordering)
- Show speech order, candidates, and interrupt votes with thresholds
- Per-speaker controls (skip, display speaker card)
- Supports auto-mute after speech setting

---

## 🗳️ Voting & timer features

- `VoteStatus` component:
  - Candidate cards with avatars and voter count
  - Voter avatars and names for each candidate
  - Countdown timer and time formatting
  - Progress bar for votes vs. total voters
- Timer controls:
  - `TimerControlModal` (minutes/seconds + presets)
  - Manual start timer mutation
  - Display of timer in `GameHeader`

---

## ⚙️ Game settings & role management

- `GameSettingsPage`:
  - General toggles (auto-saved):
    - **Mute after speech**
    - **Double identities**
    - **Witch can save self**
    - **Allow wolf self-kill**
    - **Hidden role on death**
  - Player count control (counter + update)
  - Add role (input + suggestions), Remove role
  - Roles list with counts and quick add/remove per role
  - Random/Quick assign roles action
  - Special settings section (witch/wolf/hidden-role toggles)
  - Visual saving indicator and pending fields auto-save behavior

---

## 📜 Game log & admin global commands (GameLog)

- Scrollable chronological game log
- Timestamped messages with severity highlighting (alerts/actions/system)
- Auto-scroll to bottom on update
- Admin actions available in GameLog UI:
  - Random assign roles
  - Start game
  - Next phase / Skip stage
  - Force reset (double-confirm)
  - Start timer
  - Mute all / Unmute all
  - Assign Judge / Demote Judge / Force Police

---

## 🎛️ Global & utility UI features

- Sidebar navigation (`Sidebar`) with:
  - Dashboard, Players, Settings, Spectator, Speech, Logout, Switch server
  - Toggle spectator simulation mode
- `GameHeader`:
  - Day counter, current step label, current speaker quick link, timer display
  - Start/pause/next/manage speech controls
- Toast notification system (`Toast`, `useToast`)
- `ProgressOverlay` for PROGRESS events (processing/success/error) with logs
- Visual polish: animations, dark/light theme, responsive layout, loading indicators

---

## 👀 Spectator features

- `SpectatorView`:
  - Faction stats (wolves/gods/villagers/jinBaoBao counts + dead counts)
  - Win condition text depending on double identities
  - Read-only player grid
  - Progress bars & percentage displays

---

## 🔐 Auth & security behaviors

- `AuthProvider` with `me()` check, `login()`, `logout()`
- `useDashboardAuth`:
  - `selectGuild` when joining/switching guilds
  - Redirect to `/login` if unauthenticated
  - Redirect to `/access-denied` for blocked users
  - Spectator redirect to spectator view when applicable
  - Prevent players with assigned in-game roles from accessing judge UI
- WebSocket session/rejection handling (redirect on policy rejection)

---

## 🔁 Realtime & networking

- WebSocket client (`lib/websocket.ts`):
  - Connects to `/ws`, supports `guildId` query param
  - Heartbeat (PING/PONG), auto-reconnect with exponential backoff
  - Uses `json-bigint` to parse messages safely
  - Handles events: UPDATE (session), PROGRESS, PLAYER_UPDATE
  - Session-expired handling with optional modal redirect
- API setup (`api-setup.ts`):
  - `client.setConfig` with relative `baseURL`, `withCredentials`, and `transformResponse` using `json-bigint`
- `useGameState` hook:
  - Loads session via React Query (`getSession`)
  - Subscribes to WebSocket updates and updates state
  - Timer sync based on `currentStepEndTime`
  - Progress overlay state management

---

## 🧩 Quality-of-life & misc features

- Localization (i18n) via `lib/i18n` and `locales/zh-TW.json`
- Theme support: `ThemeProvider` + `ThemeToggle` (localStorage + system fallback)
- `Counter`, `Toggle` UI helpers
- Player info caching & fetch (`PlayerContext`) to resolve Discord names/avatars
- Safety UXs: double-confirm reset, disabled states while saving/muting, transient animations
- Development/test helpers: `utils/mockData.ts`, some console debug logs for development

---

## 🧾 API / Events surface area (summary)

- Mutations & endpoints used: assignRoles, addRole, removeRole, nextState, resetGame, markDead, revive, reviveRole,
  muteAll, unmuteAll, manualStartTimer, confirmSpeech, interruptSpeech, setSpeechOrder, skipSpeech, startAutoSpeech,
  setState, startGame, setPlayerCount, updateSettings, updatePlayerRoles, setRoleLock, setPolice, selectGuild,
  getSession, getAllSessions, me, logout
- WebSocket events: UPDATE (session), PROGRESS, PLAYER_UPDATE, PONG

---

If anything should be added, reworded, or exported differently (e.g., checklists or per-component files), say which
format you'd like and I will add it.
