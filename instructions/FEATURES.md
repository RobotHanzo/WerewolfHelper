# WerewolfHelper — Functional Specification (Rebuild Guide)

This document describes **what WerewolfHelper does**, written as instructions for rebuilding it
from scratch. It deliberately avoids describing the current code structure — only features,
game rules, behaviors, and hard-won operational requirements that any implementation must honor.

---

## 1. Product Overview

WerewolfHelper hosts Chinese Werewolf (狼人殺) games on Discord. **One Discord server (guild) =
one game session.** The bot automates everything a human judge would otherwise do manually:
building the server layout, secretly distributing identities, running timed speeches, holding
police (sheriff) elections and expel votes, tracking deaths and win conditions, relaying secret
wolf chatter, and playing audio cues in voice chat.

A companion **web dashboard** gives the judge a real-time control panel for the same game, and
gives spectators a read-only "God's view". The dashboard and Discord are two interfaces over the
same live game state — anything that changes the game from either side must be reflected on the
other immediately.

**Audience & language**: all player-facing text (Discord messages, embeds, button labels,
dashboard UI) is Traditional Chinese.

### Personas
- **Judge (法官)** — runs the game. Discord server admins. Full control on Discord and dashboard.
- **Player (玩家)** — seated participant with a numbered seat, one or two secret identities, a
  private text channel, and a colored hoisted Discord role named after their seat.
- **Spectator / dead player (旁觀者)** — can watch everything (including all private channels,
  read-only) but cannot interact with the game.
- **Server creators** — a small allowlist of trusted users permitted to bootstrap new game
  servers via the bot.

---

## 2. Core Game Concepts

### Seats and identities
- A game has **N seats**, numbered 1..N, displayed as zero-padded names: 玩家01, 玩家02, …
- Each seat owns: a dedicated Discord **role** (random color, hoisted so seats group in the
  member list) and a dedicated **private text channel**.
- The judge maintains an **identity pool** — a multiset of identity names (e.g. 3× 狼人,
  1× 女巫, 4× 平民). At assignment time the pool is dealt out randomly.
- **Single-identity mode**: pool size must equal N. **Double-identity mode (雙身分)**: pool size
  must equal 2N and every seat receives two identities.

### Supported identities (canonical list)
狼人, 女巫, 獵人, 預言家, 平民, 狼王, 狼美人, 白狼王, 夢魘, 混血兒, 守衛, 騎士, 白癡, 守墓人,
魔術師, 黑市商人, 邱比特, 盜賊, 石像鬼, 狼兄, 狼弟, 複製人, 血月使者, 惡靈騎士, 通靈師, 機械狼, 獵魔人

### Faction classification (drives win conditions and UI coloring)
- **Wolf (狼)**: any identity whose name contains 狼, plus 石像鬼, 血月使者, 惡靈騎士.
- **Villager (民)**: exactly 平民.
- **God (神)**: every other identity.

### Soft death (per-identity death)
A seat does not have a boolean "dead" flag. Instead each seat tracks **dead identities**
separately from its identity list; the seat is alive while it has more identities than dead
identities. This matters in double-identity mode (losing one identity keeps you in the game) and
enables "what-if" win-condition checks (simulate removing one identity before committing a kill).

### Win conditions (checked after every death)
Count *living identities* (not players) per faction, across all seats:
- All gods dead → wolves win (屠神).
- All wolves dead → good side wins.
- Double-identity mode: all 金寶寶 (see §5) dead → wolves win.
- Single-identity mode: all villagers dead → wolves win (only if the pool ever contained 平民,
  so all-god setups are supported).
- Single-identity mode only: wolves ≥ gods + villagers → wolves win by parity. When computing
  parity, the police badge adds **+0.5** to whichever faction the police player belongs to.

When the game ends, announce the result and reason to the spectator channel.

### Nickname convention (the single source of visible game state on Discord)
Every seated player's server nickname is managed by the bot:
`[死人] 玩家NN [警長]` — optional dead prefix, zero-padded seat name, optional police suffix.
The police badge is **only** a nickname suffix, never a Discord role (role-based approaches broke
badge transfers in the past). Nicknames must be updated on death, revival, badge transfer, and
reset.

---

## 3. Game Server Provisioning

### Creation flow
1. A **server creator** runs a create command (in any server the bot shares with them),
   specifying player count and optionally double-identity mode. The bot stores this as a pending
   setup config and replies with setup instructions and a bot invite link. (Bots can no longer
   create guilds themselves — the human creates an empty guild and invites the bot.)
2. When the bot joins a guild with a pending config (or belatedly receives admin permissions
   there), it builds the server automatically:
   - Rename guild to a standard name, set a wolf icon, set notification level to mentions-only.
   - Delete all existing channels.
   - Create the **judge role** (yellow, administrator).
   - Create N **seat roles** + N **private seat channels**. Channel permissions: the seat's role
     may view and send; spectators may view but not send; @everyone can neither view, send, nor
     use slash commands.
   - Create the **spectator role** (brown).
   - Create shared channels: 法院 (court) text + voice — where public game life happens;
     法官 (judge) text — judge-only; 旁觀者 (spectator) text — spectator-only.
   - Persist the session and post a completion message.
3. Server creators can also delete a game server (removes stored session, bot leaves the guild).

### Ongoing membership rules
- When the owning server creator joins, automatically re-grant them the judge role.
- **Once identities have been assigned, anyone who joins the guild is automatically given the
  spectator role** — latecomers must never end up as unmarked observers who can talk or be
  mistaken for players.
- If the bot is removed from a guild, delete that session and halt any running game flows.

### Resizing
The judge can change the player count at any time before assignment. Excess seat roles/channels
are deleted; missing ones are created (with the permission layout above). This is a long-running
operation and must report live progress (see §10).

---

## 4. Game Configuration (pre-game)

Judges configure, via Discord commands **and** the dashboard settings page (both must exist):
- **Player count** (drives seat roles/channels, see §3 resizing).
- **Identity pool**: add/remove identities with quantities; list the pool with counts and a
  validation warning when pool size ≠ N (or 2N). Identity name inputs should autocomplete from
  the canonical list — capped at 25 suggestions (Discord's hard limit on autocomplete choices).
- **Double-identity mode** toggle.
- **Mute after speech** toggle (default ON): whether players are server-muted in voice once
  their speech turn ends.
- Dashboard settings changes auto-save (debounced ~500 ms) with a visible saving/saved indicator.

---

## 5. Identity Assignment

Triggered by the judge (Discord command or dashboard button). This is the most failure-prone
operation and has strict behavioral requirements (see also §10).

### Eligibility & validation (fail with actionable Chinese error messages)
- Eligible members = everyone in the guild who is not a bot, not the guild owner, not a judge,
  and not a spectator.
- Eligible member count must equal the configured player count; otherwise tell the judge to mark
  extras as spectators or fix the player count.
- Pool size must equal N (or 2N in double-identity mode); otherwise say so with current counts.
- Refuse to assign twice — the session remembers that assignment已完成 until a reset.

### Assignment algorithm
- Shuffle the eligible members onto seats; shuffle the identity pool; deal in seat order.
- **白癡 (idiot)**: receiving it flags the seat (affects voting rules when dead, §7).
- **Double-identity special rules**:
  - **金寶寶 (golden baby)**: the first player dealt a 平民 as their first card becomes 金寶寶 and
    receives 平民+平民. Further double-civilian deals may also become 金寶寶, but enforce the cap:
    once the allowed number of 金寶寶 exists, a player about to receive 平民+平民 must have the
    second card re-dealt from the remaining non-civilian cards (a historical bug allowed >2).
  - **複製人 (clone)**: if dealt alongside another identity, the clone copies it — the player
    effectively holds two copies of the other identity and is flagged as 複製人. For win-condition
    counting a living clone counts toward gods.
  - **Wolf ordering**: if the first card is a wolf identity, swap the two so the wolf identity is
    second (consistent presentation; players reveal/lose identities in order).
- Record for each seat: its identities, flags (金寶寶/複製人/白癡), and the assigned member.

### Discord side-effects, in mandatory order
1. **First, the game-critical mutations**: grant each member their seat role and set their seat
   nickname. These must complete (or visibly fail) **before** any notification messages are
   sent — see §10.2 for why.
2. **Then notifications**: post each seat's identities as an embed in that seat's private channel
   ("你抽到的身分是…", noting 金寶寶/複製人 status, and reminding wolves/金寶寶 to use their channel
   to coordinate).
3. In double-identity mode, the identity embed carries a **"change identity order" button**: the
   player may swap their two identities (relevant to which dies first). Exactly **120 seconds**
   after assignment the order locks: the seat is flagged locked and the channel is told
   身分順序已鎖定. Swap attempts after lock are refused.
4. Post a **summary embed** (every seat → identities, with 警長/金寶寶/複製人 annotations) plus a
   link to the web dashboard, to both the judge channel and the spectator channel.

---

## 6. Day Flow — Speeches & Timers

### Auto speech flow (the standard "day" sequence)
- The judge starts the flow. If a police exists, the police chooses the speaking direction —
  **UP (往上) or DOWN (往下)** from a dropdown with a confirm button; with no police, direction
  and starting seat are random. The judge can also force a direction from the dashboard.
- Speakers proceed seat-by-seat in the chosen direction, wrapping around, alive seats only.
- A visual "day separator" line is posted to channels when a new day's flow begins so channel
  history reads in days.
- Each speaker gets a fixed speech time with a countdown. During a speech:
  - **Skip (button / dashboard)**: the current speaker voluntarily ends their turn.
  - **Interrupt vote (button)**: other alive players vote to cut the speaker off; reaching a
    majority of alive players ends the turn immediately.
  - The judge can force-skip or terminate the whole flow at any time.
- When a turn ends and "mute after speech" is on, server-mute the finished speaker.
- When all seats have spoken the flow completes (and any completion action fires, e.g. the police
  election advances, §7).

### Last words (遺言)
When a player dies in a way that allows last words, automatically run a single-speaker speech
session for them in court.

### Standalone timer
Judge can start an arbitrary timer (duration given as e.g. `1m30s`, or from the dashboard's
minutes/seconds inputs with 30/60/90/180 s presets). The timer:
- counts down publicly, plays a **30-seconds-remaining** sound and a **timer-ended** sound in the
  court voice channel,
- can be terminated early via a button.

### Voice control
Judge can mass-mute and mass-unmute everyone (non-admins) in the guild, from Discord or
dashboard. Timers/speech threads must be cleanly cancellable — every "interrupt the flow" path
must actually stop the running countdown (past deadlock bugs lived here).

---

## 7. Police (Sheriff) Election

A timed state machine started by the judge:

| Stage | Duration | Behavior |
|---|---|---|
| **Enrollment (參選)** | 30 s | Audio cue plays in court voice at start; a "參選警長" button is posted. Players click to enroll; clicking again withdraws. A 10-seconds-remaining audio cue plays at the 20 s mark. |
| **Resolution** | — | 0 candidates → announce 無人參選，警徽撕毀 (badge destroyed), end. 1 candidate → auto-elected immediately. 2+ → list candidates and continue. |
| **Campaign speeches** | per-speaker | Candidates speak via the speech system (§6). |
| **Withdrawal (退選)** | 20 s | Candidates may withdraw. If everyone withdraws → badge destroyed, end. |
| **Voting** | 30 s | Audio cue at start, 10-seconds-remaining cue at 20 s. One button per candidate. |

**Voting rules (shared with expel polls):**
- Only alive non-candidates vote. Clicking a different candidate switches your vote; clicking the
  same candidate again retracts it.
- Vote tallies show each candidate's voters (mentions) and weighted count.
- **Enrollment and withdrawal are stage-gated**: once voting begins, enrolling/withdrawing is
  impossible (a past bug let players slip in/out late). Stage transitions are scheduled, never
  instant — players need the posted buffer time.
- **Tie → PK runoff**: tied winners give another round of speeches, then a revote among them only.

**Effects of election:** winner's seat gets the police flag and the [警長] nickname suffix; from
then on the police's vote counts **1.5** in polls, the police picks speech direction, and the
badge contributes +0.5 to parity win checks.

The judge can force-start voting (skipping earlier stages), force-assign police to any player,
and interrupt the election.

### Police badge on death
When the police dies (fully — no living identities), prompt them with a member select menu plus
two buttons: **移交 (transfer)** to a chosen alive player, or **撕毀 (destroy)**. If they don't
act within the timeout, the badge is destroyed. Transfers update both players' flags and
nicknames and are logged.

---

## 8. Expel Poll (放逐投票)

- Judge starts it; an audio cue plays; every alive seat gets a vote button; 30 s window with a
  10-seconds-remaining cue.
- Voting mechanics identical to police voting (switch/retract, police ×1.5, voters listed).
- **A dead 白癡 (idiot) cannot vote** (it stays revealed in the game but loses voting rights).
- Tie → PK runoff: tied players speak, then a revote restricted to them (no further nesting —
  a second tie ends without expulsion).
- Result announces the expelled player and full tally. Expulsion uses the standard death flow
  (last words allowed per judge choice, win-condition check, police transfer if applicable).

---

## 9. Deaths, Revivals, Wolf Chat & Audit Log

### Kill (judge action, Discord command or dashboard with confirm modal)
- Marks **one identity** of the seat dead (soft death, §2). Dashboard kill flow includes an
  "allow last words" checkbox; the Discord command takes an equivalent option.
- Announce the death in court; update the nickname ([死人] prefix when fully dead); run the
  win-condition check; if fully dead, trigger police transfer if they held the badge; optionally
  run last words.
- Fully-dead players should be treated as spectators for visibility purposes.

### Revive (judge action)
- Revive the whole seat or a **single named identity** (dashboard: click a struck-through dead
  identity on the player card). Restores nickname, logs the event.

### Judge identity editing
From the dashboard the judge can directly edit a seat's identities (dropdowns over the pool),
toggle the identity-order lock, swap identity order, and transfer the police badge — all
reflected to Discord (nicknames) immediately.

### Wolf chat relay (secret coordination)
Messages posted in a wolf-team member's private seat channel are mirrored into every other
wolf-team member's private channel **and** into the judge channel, attributed to the sender
(their name and avatar, via webhook impersonation). The wolf team for this purpose includes wolf
identities and 夢魘. Use **one cached webhook per channel** — creating webhooks ad hoc hits
Discord's 10-per-channel cap and rate limits.

**金寶寶 have their own separate cross-chat group**, mirrored only among the 金寶寶 channels (not
into the wolf channels and **not** into the judge channel — golden-baby coordination stays private).
At assignment each 金寶寶 is told who the other 金寶寶 is, or that they are the only one on the board,
so the team knows its members before the first night.

### Audit log
Every game event is appended to a persistent, typed log on the session: deaths, revivals,
assignment completed, police enrolled/withdrew/elected/transferred/destroyed, votes cast and
results, speeches started/skipped/interrupted, game started/ended/reset, judge promotions, etc.
Each entry: unique id, timestamp, type, human-readable Chinese message, optional structured
metadata. The log feeds the dashboard's Game Log panel (entries colored by severity:
info/action/alert) and survives restarts. The log is cleared on game reset.

### Game reset
One judge action returns the server to pre-assignment state: clear every seat's member binding,
identities, flags; remove seat and spectator roles from members; reset nicknames (skip the guild
owner — bots can never rename owners — and anyone the bot cannot interact with, logging a notice
instead); clear logs; mark identities unassigned. Long-running; live progress required (§10).
Dashboard reset requires a two-step confirmation (armed button).

---

## 10. Operational Requirements (learned the hard way)

These behaviors were earned through production failures. They are requirements, not suggestions.

### 10.1 Bulk Discord mutations: queue, don't fire-and-forget
Any operation touching many members/channels/roles (assignment, reset, resize) must:
- Submit each Discord call through a **rate-limit-aware queue** (let the Discord library's
  limiter serialize per route) — never raw parallel blasts, never manual sleeps.
- Treat the batch as a **barrier**: wait (with a generous timeout) until every call has succeeded
  or failed before moving to the next phase.
- **Per-item fault isolation**: one member failing (left the guild, permission issue) must not
  abort the batch. Log each item's outcome individually — `[完成] <description>` /
  `[失敗] <description>: <reason>` — streamed live to the judge.
- On timeout, surface which items completed and which didn't (部分操作逾時 warning), then raise.
- Report progress as a percentage; multi-phase operations map each phase onto a sub-range of
  0–100 % so the judge sees one smooth bar (e.g. deletions 0–30 %, role creation 30–60 %,
  channel creation 60–95 %).

### 10.2 Critical-before-cosmetic ordering in assignment
During identity assignment, **role grants and nickname changes must run as their own batch and
fully complete before any notification messages are sent.** Member PATCHes (nicknames
especially) sit in Discord's strictest per-guild rate buckets; when mixed into one batch with
dozens of channel messages, the messages starve them and the game starts with players missing
roles/nicknames. Give the notification batch its own (longer) timeout — message floods are slow
but harmless; half-applied roles ruin a game.

### 10.3 Permission preflight
- Before changing any nickname, check the bot can actually interact with that member
  (role hierarchy); if not, log a visible warning (權限不足) instead of queueing a doomed call.
- Never attempt to rename the guild **owner**; skip with a notice.
- Skip no-op updates (nickname already correct) — don't waste rate-limit budget.

### 10.4 Dependent-resource phasing
When creating channels that reference just-created roles (seat setup/resize), the role-creation
batch must fully complete first, with created entities captured from each call's success callback
(callbacks arrive on library threads — use thread-safe collection). Set channel permission
overwrites **at creation time**, not as follow-up edits.

### 10.5 Real-time sync (dashboard ↔ backend)
- Push-based: after **every** state mutation (from Discord or HTTP), broadcast a **full game
  state snapshot** to all dashboard clients of that guild. Snapshot-as-truth avoids per-field
  patch reconciliation bugs; the dashboard may apply optimistic local updates but the next
  snapshot wins.
- Long-running operations additionally stream **progress events** (percent + log lines) that
  drive the dashboard's progress overlay; the overlay flags failure if a line contains an error
  marker (錯誤 / Error / Failed).
- **Server-side WS**: connections are per-guild; authenticate at handshake (privileged users
  only, requested guild must equal the authorized guild); sends must be serialized per
  connection (concurrent writes on one socket throw); clean up empty per-guild registries.
- **Client-side WS**: heartbeat ping every 15 s (idle proxies kill quiet sockets); reconnect
  with exponential backoff (1 s, ×1.5, cap 10 s); distinguish "session expired / rejected" close
  reasons from network blips — expiry pops a re-login modal instead of reconnect-looping.

### 10.6 Timing & limits cheat-sheet
- Police enrollment 30 s; withdrawal 20 s; voting 30 s; "10 s remaining" audio at the 20 s mark
  of 30 s stages. Expel poll 30 s. Identity-order lock 120 s after assignment.
- Police vote weight 1.5; police parity bonus 0.5.
- Autocomplete suggestions ≤ 25 (Discord hard limit).
- OAuth callback: wait ~500 ms before fetching the authenticated user — the session cookie may
  not be committed immediately after redirect.
- Stage transitions are scheduled with visible buffer time, never instantaneous.

### 10.7 Audio cues
Seven sounds, played into the court **voice** channel: expel-poll start, police-enroll start,
police-vote start, enroll 10 s remaining, poll 10 s remaining, timer 30 s remaining, timer ended.
Sound assets ship with the app and must be playable from the runtime environment.

---

## 11. Web Dashboard

### 11.1 Authentication & roles
- **Login with Discord** (OAuth; identity + guild membership + member info scopes). Cookie
  session, long-lived (~7 days).
- Dashboard role derived from Discord permissions on the selected guild: admins/managers →
  **JUDGE**; everyone else → **SPECTATOR**; plus **PENDING** (hasn't picked a guild) and
  **BLOCKED** states. Judges can promote/demote other users' dashboard roles.
- Route guards: unauthenticated → login; PENDING → guild confirmation; BLOCKED → access-denied
  page; SPECTATOR → forced into the spectator view.
- **Active players are locked out**: a logged-in user who holds a seat in the running game must
  not reach the dashboard (they'd see everyone's identities). Show an explanatory page.
- Session expiry (detected via WS, §10.5) shows a "login again" modal.

### 11.2 Server selection
After login, a card grid of the user's game servers (guild icon, name, player count) with
loading/error/empty states; picking one enters its dashboard.

### 11.3 Main game view (judge)
- **Header**: current phase (day/night with icon), day counter, current speaker + their
  countdown, game timer (red under 10 s), start-game / next-phase / pause controls.
- **Player grid** — one card per seat:
  - avatar, seat name, Discord username; badges for 警長, 金寶寶, identity-order lock state.
  - identities listed in order, color-coded by faction (wolf red, villager green, god indigo);
    dead identities struck through and dimmed — **clicking a dead identity revives it**;
    swap-order control when unlocked.
  - actions: kill (opens confirm modal with "allow last words" checkbox), revive, edit.
  - subtle animations on changes (flash on identity change, slide on swap, transient lock icon).
- **Edit player modal**: identity dropdowns (second one only in double-identity mode), order-lock
  toggle, police badge transfer to any alive player.
- **Game log panel**: scrollable typed log plus grouped judge commands — game flow (random
  assign, start, two-stage force reset), voice & timer (manual timer, mute all, unmute all),
  admin (assign/demote judge via searchable member picker, force police).
- **Vote status panel** (during any poll): candidates with avatars, voters listed per candidate,
  weighted counts, turnout progress bar, stage countdown.
- **Progress overlay** for long operations: title, live log lines (terminal style), percent bar,
  success auto-close, error state requiring acknowledgment.

### 11.4 Settings page (judge)
Player count stepper (apply triggers the resize flow with overlay), identity pool editor
(add with suggestions, remove, counts, validation), auto-saving toggles for double identities and
mute-after-speech, random-assign shortcut.

### 11.5 Speech manager (judge & spectator)
Idle: start auto speech / start police election. During police stages: stage banner with
countdown, candidate grid, enrollment/withdrawal status. During voting: the vote status panel.
During speeches: a large active-speaker card (avatar, countdown, animated mic), skip & interrupt
controls (judge only), upcoming-speaker queue, and force direction UP/DOWN controls.

### 11.6 Spectator view ("God's view", read-only)
Faction overview cards — wolves / gods / villagers (or 金寶寶 in double-identity mode) — each
with alive/total counts and progress bars; a win-condition explainer; and the full player grid
with identities visible but all controls disabled. Judges can toggle a spectator-simulation mode
to preview this view.

### 11.7 Presentation
- Fully Traditional Chinese UI (translations centralized, parameterizable strings).
- Dark/light theme, toggleable, persisted, defaulting to system preference.
- Responsive: sidebar collapses on mobile; player grid 1/2/3 columns by width.
- Countdowns tick smoothly client-side from server-provided end timestamps.

---

## 12. Conventions & Constants Appendix

| Item | Value |
|---|---|
| Seat name format | 玩家 + zero-padded 2-digit seat (玩家01) |
| Nickname format | `[死人] 玩家NN [警長]` (prefix/suffix optional) |
| Police representation | nickname suffix only — never a Discord role |
| Police vote weight | 1.5 (polls); +0.5 faction parity bonus |
| Wolf-faction identities | name contains 狼; 石像鬼; 血月使者; 惡靈騎士 |
| Identity-order lock delay | 120 s after assignment |
| Police election timings | enroll 30 s · withdraw 20 s · vote 30 s · 10 s-warning at 20 s |
| Expel poll | 30 s, one PK runoff max |
| Mute after speech | default ON |
| Autocomplete cap | 25 entries |
| WS heartbeat / reconnect | 15 s ping · backoff 1 s ×1.5 capped 10 s |
| Dashboard session lifetime | ~7 days |
| Court / judge / spectator channels | 法院 (text+voice) · 法官 · 旁觀者 |
