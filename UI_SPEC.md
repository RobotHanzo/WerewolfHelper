# WerewolfHelper Dashboard — UI Feature Brief (for Redesign)

This brief describes everything the web dashboard must let its users **see and do**, screen by
screen. It intentionally says nothing about how the current interface looks or is built — visual
design, layout, and interaction patterns are open for the redesign. Game rules and terminology
are defined in `FEATURES.md`; treat that as the domain glossary.

---

## 1. Product Context

The dashboard is the control panel for a live Chinese Werewolf (狼人殺) game played over Discord
voice. It has two faces:

- For the **judge (法官)**: a real-time game-master console. The judge uses it *while* hosting —
  talking to players, watching Discord, and operating this UI at the same time. They need to
  read game state at a glance and act in one or two interactions, under time pressure.
- For **spectators (觀眾)** — including dead players: a read-only "God's view" of the game and
  of whoever is currently speaking. Spectators are frequently on phones; the speech view may
  also be projected on a shared screen.

**Seated players must never see the dashboard** — it reveals every secret identity. Logged-in
users who hold a seat in the running game get an explanation screen instead.

**Language**: entire UI in Traditional Chinese. All copy must remain localizable (no text baked
into imagery; strings support parameter substitution).

**Tone**: it's a social deduction night-game — atmosphere (wolves, night, drama) is welcome, but
never at the cost of operational clarity for the judge.

---

## 2. Users, Roles & Access

| Role | Capabilities |
|---|---|
| Judge | Everything: game control, player management, settings, all views |
| Spectator | Spectator view + speech view, read-only |
| Pending | Has logged in but not yet confirmed a server — must be routed through server selection |
| Blocked | Denied access entirely |

Additional access requirements:
- **Judge → spectator preview**: judges need a one-tap way to see exactly what spectators see,
  and an equally easy way back.
- **Session expiry**: when the login session dies mid-use, interrupt with a clear "session
  expired, log in again" prompt — never fail silently or strand the user on a frozen screen.
- **Player lockout screen**: explains *why* an active player can't enter and what to do about it
  (ask the judge to make them a spectator).

---

## 3. Screens & Required Capabilities

### 3.1 Login
- Single action: **sign in with Discord** (OAuth redirect). The Discord affordance should be
  instantly recognizable per Discord's brand conventions.
- A short note that the dashboard is only for judges and spectators.
- Nothing else — no forms, no registration.

### 3.2 Post-login transition
- A brief loading state while the session is established, then automatic routing to either the
  user's game server or the server selection screen. Should feel instantaneous.

### 3.3 Server selection
- List of the user's Werewolf game servers, each showing the server's icon, name, and player
  count. Selecting one enters its dashboard.
- Required states: loading; error with retry; empty (no game servers, with a hint that a server
  creator must set one up); a way back to login.
- Expect 1–5 servers; optimize for choosing among a few, not browsing many.

### 3.4 Persistent navigation (all in-game screens)
Always available while inside a game:
- Switch between the four in-game areas: **Dashboard**, **Game Settings** (judge only),
  **Spectator View**, **Speech Manager**. Navigation must reflect the user's role (spectators
  only see their two areas).
- **Identity & role display**: who I am (Discord avatar/name) and my role; for judges this is
  also where the spectator-preview toggle lives.
- **Live connection indicator**: whether the dashboard is currently receiving live updates from
  the game. This is the user's trust signal — it must be visible at all times, with an
  unmissable difference between connected and disconnected.
- Utilities: light/dark theme toggle, switch server, sign out.

### 3.5 Main dashboard (judge's home)

**At-a-glance game status**, always visible:
- Current phase (day/night) and day number.
- The current speaker (if any) with their remaining speaking time — and a shortcut to the
  speech manager.
- Any running timer, counting down live; remaining time must become visually urgent in the
  final seconds.
- In the pre-game lobby: a prominent **start game** action. During the game: phase-advance and
  pause controls.

**The player roster** — one unit per seat, the judge's main working surface. Per seat, show:
- Seat number/name (玩家01…), the assigned member's Discord avatar and username, or a clear
  "unassigned" treatment before the game starts.
- **Alive/dead state** — readable from across the room.
- **Identities** (one, or two in double-identity games, in order). Faction must be
  distinguishable at a glance (wolf / villager / god). In double-identity games, each identity's
  individual alive/dead state must be visible.
- **Badges**: police (警長), 金寶寶, and — in double-identity games — whether the identity order
  is still swappable or locked.
- Per-seat judge actions:
  - **Kill** → confirmation step that includes an "allow last words" choice.
  - **Revive** the whole seat, or **revive one specific dead identity**.
  - **Swap identity order** (double-identity, while unlocked).
  - **Edit** → change identities (pick from the game's pool), toggle the order lock, and
    transfer the police badge to another living player.

**Remote-change awareness**: any seat can change at any moment from Discord-side events. The
design needs a deliberate cue that draws the judge's peripheral attention to a seat that just
changed without their input.

**Game event log**: a live, timestamped feed of everything that happens (deaths, votes,
elections, speeches, resets…), with severity distinguished (info / action / alert). When the
log isn't in view, newly arrived entries need an unread indicator. Spectator-facing surfaces
never include it.

**Global judge commands**, reachable from this screen, organized in three groups:
- *Game flow*: randomly assign identities; start game; **force reset** — destructive, must
  require a deliberate two-step confirmation.
- *Voice & timing*: start a custom timer (free minutes/seconds entry plus common presets such
  as 30/60/90/180 s); mute everyone; unmute everyone.
- *Administration*: promote a member to judge; demote a judge; force-assign the police badge —
  each via a searchable member picker (filter by name, show avatars, only valid targets
  selectable).

**Blocking progress for long operations**: identity assignment, reset, and player-count changes
take 10–60 seconds and stream per-step results. While one runs, the UI must:
- block other interaction,
- show overall progress (percentage) and a live, readable feed of step-by-step outcomes
  (each step succeeds or fails individually, in Chinese),
- end in an explicit success (may auto-dismiss) or error state (must be acknowledged, with the
  failed steps inspectable afterwards — judges genuinely read these to fix permission issues).

**Expel vote takeover**: while an expel poll is running, the dashboard must foreground the live
vote status (see §3.7) until the poll resolves.

### 3.6 Game settings (judge)
- **Game options**: toggle 雙身分 (double identities) and 發言後靜音 (mute after speech).
  Changes save automatically with visible save-state feedback.
- **Player count**: adjust the number of seats; applying it is one of the long operations above.
  Unapplied changes must be visually distinct from saved state.
- **Identity pool**: add identities (with suggestions from the canonical identity list, see
  `FEATURES.md`), remove them, see each identity's count and the pool total. When the pool size
  doesn't match the player count, show a clear validation warning — this is the #1 setup
  mistake.
- Shortcut to **random identity assignment**.

### 3.7 Speech manager (judge + spectator)
This screen mirrors the live "stage" of the game and changes shape with the game's state:

- **Idle**: judge can start the **auto speech flow** or a **police election**. Spectators see a
  calm "nothing in progress" state.
- **Police election in progress**: show the current stage (enrollment / withdrawal / voting),
  a live countdown to the stage's end, the candidate list (avatars + names, withdrawn candidates
  clearly marked), and whether enrolling/withdrawing is currently allowed.
- **Voting (police election and expel polls share this)**: per candidate — avatar, name,
  weighted vote count, and *who* voted for them (voters' avatars); overall turnout
  (votes cast vs eligible voters); a countdown with end-of-window urgency; an indication of how
  many players haven't voted.
- **Someone is speaking**: the centerpiece. Show the speaker (large), their remaining time
  (urgent when low), a clear "live/speaking" feeling, and the queue of upcoming speakers in
  order. Judge controls: **skip** the speaker, **terminate** the whole flow, and force the
  speaking direction (往上 / 往下) when the flow is waiting on that choice.
- Speaker handoffs happen every couple of minutes; the transition from one speaker to the next
  should be unmistakable, including to a passive viewer on a projector.

### 3.8 Spectator view ("God's view", judge + spectator, read-only)
- **Faction overview**: for each faction in play — wolves, gods, and villagers *or* 金寶寶
  (depending on game mode) — show living vs total counts with a sense of proportion.
- A plain-language **win-condition reminder**, adapted to the game mode.
- The **full roster with all identities revealed**, in a strictly read-only presentation
  (no actions, no affordances that look tappable).

---

## 4. Real-Time & Stateful Behaviors (design constraints)

These behaviors are inherent to the product and must be designed for, whatever the visual
direction:

1. **Everything updates remotely.** Any value on any screen can change at any second from
   Discord-side actions. No screen may assume the user caused what they're seeing. The redesign
   needs a consistent vocabulary for "this just changed".
2. **Countdowns everywhere.** Speech time, vote windows, election stages, custom timers — all
   tick live and share one urgency convention for the final seconds.
3. **Connected vs not.** When live updates stop, the user must know immediately; when the
   session expires, they must be interrupted and offered re-login. Stale data shown as fresh is
   the worst failure mode.
4. **Act first, reconcile after.** Judge actions should feel instant; the authoritative state
   arrives a moment later and may correct the display. Avoid blocking spinners on routine
   actions; reserve blocking UI for the long operations.
5. **Things happen while you look away.** The log accrues, votes come in, speakers change.
   Unread/missed-activity indication is a core pattern, not an extra.
6. **Multiple concurrent surfaces.** The same game is being driven from Discord and possibly
   another judge's dashboard simultaneously; the UI is a window onto shared state, never the
   sole owner of it.

---

## 5. Platform & Quality Requirements

- **Devices**: judges on laptop/tablet; spectators predominantly on phones; the speech view
  potentially full-screened on a projector. Each area should be genuinely usable at its likely
  size — not merely responsive-by-reflow.
- **Theming**: light and dark, user-switchable, defaulting to system preference. Night-game
  sessions make dark mode the primary experience.
- **Localization**: Traditional Chinese throughout; design must tolerate CJK line-length
  behavior and string growth for any future locales.
- **Accessibility**: faction and state coding must not rely on color alone; timers and urgent
  states need non-color cues; motion should respect reduced-motion preferences; touch targets
  sized for use mid-conversation.
- **Edge states to design deliberately**: pre-game lobby (seats unassigned, everything zero),
  game over, bot/connection lost, empty server list, the player-lockout screen, and the
  blocked-user state.

---

## 6. What the Redesign May Freely Change

Open territory — nothing below is a requirement:
- All layout, navigation structure, and visual styling.
- Where the global judge commands and the event log live and how they're surfaced.
- How votes, elections, and the "current stage of the game" are presented (today they're split
  across screens; unifying them is welcome).
- How the roster scales for larger games (12+ seats) — denser modes, grouping, etc.
- The faction color conventions (wolf-red, villager-green are genre-familiar to players, but
  not mandated — just keep factions distinguishable, accessibly).
- Whether spectator and judge experiences share one design or diverge into tailored layouts.
