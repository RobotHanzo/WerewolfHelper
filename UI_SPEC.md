# WerewolfHelper Dashboard — UI Specification & Redesign Brief

A screen-by-screen description of the web dashboard's UI: every view, component, state, and
interaction, plus the current visual language. Written as a handoff for a full redesign — it
describes **what the UI must let users see and do**, with the current design documented as
reference, not as a constraint. Backend/game rules live in `FEATURES.md`; treat that as the
domain glossary.

---

## 1. Product Context

The dashboard is the **judge's control panel** for a live Chinese Werewolf (狼人殺) game running
on a Discord server, and a **read-only "God's view"** for spectators. It is used *during* a live
voice game: the judge is simultaneously talking to players, watching Discord, and operating this
UI — so glanceability, large touch targets, and zero-ambiguity state indication matter more than
information density. Sessions are real-time: state streams in over WebSocket and the UI must
always reflect the live game within a second.

**Language**: the entire UI is Traditional Chinese (all strings centralized; the redesign must
keep full localizability with `{param}` substitution).

**Users & access levels**
- **Judge (法官)** — full control. Typically on a laptop, possibly a tablet.
- **Spectator (觀眾)** — read-only views (spectator view + speech manager). Often on phones.
- Judges can flip a **"view as spectator" simulation toggle** to preview what spectators see.
- Players never see the dashboard (they'd see secret identities); there is a dedicated lockout
  screen explaining this.

---

## 2. Information Architecture & Navigation

```
/login                      Login screen (Discord OAuth)
/auth/callback              Transitional spinner while session settles
/access-denied              Player-lockout explanation page
/                           Server selection (game server cards)
/server/:guildId            Main dashboard (player grid)        [judge]
/server/:guildId/settings   Game settings page                  [judge]
/server/:guildId/speech     Speech & election manager           [judge + spectator]
/server/:guildId/spectator  God's view                          [judge + spectator]
```

**Persistent shell** (everything under `/server/...`): a left **sidebar** + main content column.

Sidebar contains, top to bottom:
1. **Brand**: app logo (moon glyph in an indigo rounded square) + app name 狼人殺助手 (the 助手
   suffix accented in indigo).
2. **Navigation** (role-aware): Dashboard and Game Settings (judge only, hidden in spectator
   simulation); Spectator View and Speech Manager (judge + spectator). Active item gets a tinted
   indigo pill; inactive items are muted with hover states.
3. **User card**: Discord avatar (ring accent), username, and a **role badge** — for judges the
   badge itself is the spectator-simulation toggle (purple = 法官, blue = 觀眾, click to flip);
   for others it's a static label.
4. **Bot connection indicator**: pulsing green dot + "已連線" or red dot + "已斷線", driven by
   live WebSocket state. This is the user's trust signal that what they see is current — keep it
   always visible.
5. **Utility row**: theme toggle (sun/moon), switch-server button, sign-out (red hover).

Responsive behavior today: sidebar is a fixed 256 px column on ≥768 px, and stacks full-width
above the content on mobile. **Known weakness — redesign opportunity**: on phones the stacked
sidebar pushes content down; a collapsible drawer or bottom tab bar would serve spectators
(mostly mobile) far better.

---

## 3. Screens

### 3.1 Login (`/login`)
- Centered glass-morphism card (max-width ~28 rem, backdrop blur, soft border, heavy shadow) on
  a near-black/near-white page with two huge blurred radial glows (indigo top-left, blood-red
  bottom-right) setting the werewolf mood.
- Card content: app mark (20×20 indigo→purple gradient rounded square with moon glyph, slight
  rotation), title 狼人殺助手, one-line subtitle, then a single **"使用 Discord 登入" button in
  Discord blurple (#5865F2)** with the Discord logo, and a fine-print restriction note
  (dashboard is for judges/spectators only).
- No other actions. Clicking navigates away to Discord OAuth; no in-page loading state needed.

### 3.2 Auth callback (`/auth/callback`)
- Pure transition: spinner + "登入中…" while the session cookie settles (~0.5 s) before
  redirecting to the chosen server or the server list. Keep it minimal; it flashes briefly.

### 3.3 Access denied (`/access-denied`)
- Full-page notice with a shield-alert icon: explains that **seated players cannot open the
  dashboard** (they would see all identities), suggests asking the judge for the spectator role,
  and offers a single "back" action to `/`.

### 3.4 Server selection (`/`)
- Heading + grid of **server cards**: guild icon (or fallback), guild name, player count.
  Clicking a card enters that game's dashboard.
- States: loading (spinner), error (message + retry button), empty ("no servers yet" with a hint
  that a server creator must set one up), plus a back-to-login escape.
- Typically 1–5 cards; design for the small-N case, not an infinite catalog.

### 3.5 Main dashboard (`/server/:guildId`) — the core screen

**Game header** (sticky top bar of the content column):
- Left cluster: **phase chip** (sun icon + 白天 / moon icon + 夜晚), **day counter** (第 N 天),
  and — whenever someone is speaking — the **current speaker's name with their live countdown**
  (links to the speech manager). A general game **timer** also renders MM:SS, turning red and
  urgent below 10 s.
- Right cluster (judge only): in lobby a primary **開始遊戲** button; once running, **pause** and
  **next phase** controls.
- Spectators/simulation see the header without controls.

**Player grid** (main content): section title with live alive-count ("玩家 (8 存活)"), then
responsive cards — 1 column on phones, 2 at ≥768 px, 3 at ≥1280 px. Each **player card** packs
the per-seat state and is the judge's main working surface:
- Discord avatar (placeholder gear icon when the seat is unassigned), seat name (玩家03),
  Discord username underneath.
- **Status badges**: green shield = 警長 (sheriff/police); pink heart = 金寶寶; amber unlock /
  indigo lock icon for identity-order state in double-identity games (the lock icon appears
  briefly with an animation at the moment of locking, then fades).
- **Identity list**, numbered when there are two. Identity chips are color-coded by faction —
  wolf red, villager emerald, god indigo. A **dead identity renders struck-through and dimmed,
  and is itself the revive affordance (clicking it revives that identity)**. Between two
  identities, a swap button (↔) appears while unlocked.
- Dead seats: whole card desaturates/dims with a clear 死亡 treatment.
- **Action row** (judge only): kill (skull, opens confirm modal) or revive when fully dead, and
  edit (opens the player edit modal).
- **Live-change animations**: the card flashes (scale + indigo glow) when its identities change
  remotely; identity rows slide left/right when swapped. These matter — multiple people mutate
  the game at once and the judge needs to notice remote changes peripherally.

**Floating game log** (judge's command drawer):
- A **floating action button** bottom-right (indigo circle, chat icon, hover-grow) with a **red
  unread dot** when new log entries arrived while closed. Toggles a **floating panel**
  (~350–400 px wide, ~500 px tall, slides up + fades in) containing:
  - **Scrollable audit log**: timestamped entries colored by severity (info gray / action
    indigo / alert red). Auto-updates live.
  - **Grouped judge commands** (hidden for spectators):
    - 遊戲流程: random identity assignment, start game, **force reset (two-step armed button —
      first click arms it with a warning color, second click within a few seconds confirms)**.
    - 語音與計時: open timer modal, mute all, unmute all.
    - 管理與身分: assign judge, demote judge (both open a member picker), force police.

**Overlays & modals on this screen** (all: dimmed backdrop, centered card, fade+scale-in ~300 ms,
close on backdrop click where non-destructive):
- **Death confirmation**: player's avatar/name/identities, an "允許遺言" (allow last words)
  checkbox, cancel + destructive-styled confirm.
- **Player edit**: identity dropdown(s) (second only in double-identity mode, with a "none"
  option), save; identity-order lock toggle; police badge transfer (dropdown of other alive
  players + transfer button).
- **Player select** (assign/demote judge, force police): search field with live fuzzy filter,
  scrollable member list (avatar, name, identities), click to choose.
- **Timer control**: minutes + seconds numeric inputs, preset chips (30s / 60s / 90s / 180s),
  start.
- **Progress overlay** — the signature long-operation UI (assignment, reset, player-count
  resize): title, **percent progress bar**, and a terminal-style log area streaming lines from
  the backend (per-item ✓/✗ outcomes, in Chinese). Three visual states: processing, success
  (auto-dismisses after ~1.5 s), error (red accents, requires explicit OK; failed lines
  highlighted). The judge *reads* this log when something goes wrong — keep it legible and
  copyable, not decorative.
- **Expel vote overlay**: while an expel poll runs on Discord, the dashboard shows a modal
  vote status (see 3.7's vote panel) that cannot be dismissed until the poll ends.
- **Session expired modal**: logout icon, "登入已過期" explanation, single "重新登入" action.

### 3.6 Game settings (`/server/:guildId/settings`)
- **General settings**: two toggles — 發言後靜音 (mute after speech) and 雙身分 (double
  identities) — that **auto-save** (debounced ~500 ms) with an inline Saving…/已儲存 indicator.
- **Player count**: stepper (−/+) around a large number; an "update" button appears only when
  the value differs from the server's. Applying runs the resize with the progress overlay.
- **Identity pool**: add-row (text input with suggestion list of the canonical identities +
  add button) and a grid/chip list of current identities with counts and per-identity remove
  buttons. Pool-size vs player-count mismatch should be visibly flagged (validation warning).
- **Random assign** shortcut button (same operation as the game-log command).

### 3.7 Speech manager (`/server/:guildId/speech`)
A judge + spectator screen that mirrors the live "stage" of the game. It has distinct modes:

- **Idle**: two large actions — 開始自動發言 (start auto speech flow) and 開始警長競選 (start
  police election). Spectators see an idle/empty state instead.
- **Police election stages**: a stage banner (參選中 / 退選中 / 投票中) with a **live countdown
  to the stage's end**, the candidate grid (avatars + names, withdrawn candidates marked), and
  enroll/withdraw permission badges.
- **Police voting** (and expel polls, shared component): **vote status panel** — per-candidate
  rows/cards with avatar, name, **weighted vote count**, and the voters' avatars listed under
  each candidate; an overall turnout bar (votes cast / eligible voters); a countdown (red under
  10 s); "等待 N 位玩家" hint.
- **Active speech**: a hero **speaker card** — large avatar (subtle hover rotation), speaker
  name, "發言中" label, big MM:SS countdown (red under 10 s), an animated bouncing/pulsing
  microphone, and a pulsing glow around the card. Judge controls: **跳過 (skip)** and
  **強制結束 (interrupt)**. Below it, the **upcoming-speaker queue** in order. Speaker
  transitions animate: outgoing card swipes out left, incoming swipes in from the right (~400 ms).
- **Order controls** (when the flow waits on direction): force 往上 / 往下 buttons.

This screen is the one most likely projected on a shared screen or watched on phones during the
game — prioritize big type and stage-like presentation in the redesign.

### 3.8 Spectator view (`/server/:guildId/spectator`)
"上帝視角 / 死者的國度" — read-only:
- **Faction summary cards**: 狼人 (red), 神職 (yellow/amber), and 平民 (emerald) or 金寶寶 (pink)
  depending on game mode. Each: icon, faction name, **alive/total count**, and a percentage
  progress bar of survivors.
- **Win-condition explainer** box (text adapts to single/double-identity mode).
- The full **player grid** with identities visible but every control removed (cards render in
  read-only mode).

---

## 4. Live-Data Behaviors the Design Must Accommodate

- **Everything updates remotely without user action.** Any value on screen (identities, deaths,
  votes, speakers, logs, settings) can change at any moment from Discord-side actions. The
  design needs a consistent "remote change" cue language (the current flash/slide animations
  serve this) and must never rely on the user having triggered the change.
- **Countdowns** tick client-side from server end-timestamps (smooth, no jumps), with a shared
  urgency treatment (<10 s → red) across header timer, speech timer, vote timers, stage timers.
- **Connection state** is a first-class element (sidebar indicator). On disconnect the UI keeps
  rendering last-known state while reconnecting; on session expiry it interrupts with the
  re-login modal.
- **Optimistic updates**: judge actions reflect immediately, then the next server snapshot
  reconciles — brief flicker on conflict is acceptable, blocking spinners on every action are not.
- **Long operations** (assign/reset/resize, 10–60 s) always run behind the progress overlay with
  streamed logs; the rest of the UI is intentionally blocked during them.
- **Unread accumulation**: the log FAB's unread badge pattern — any redesign needs an equivalent
  for "things happened while you weren't looking".

---

## 5. Current Visual Language (reference, not mandate)

- **Palette**: slate neutrals (light: slate-50 page / white cards; dark: slate-900/950 page,
  slate-800 borders). **Indigo** is the brand/action color (buttons, active nav, focus, glow).
  Semantic colors: red = wolves/danger/urgency, emerald = villagers/success/connected,
  amber/yellow = gods/warnings/unlock, pink = 金寶寶, purple = judge role, blue = spectator role,
  Discord blurple only on the login button.
- **Theme**: full light/dark support, class-based, persisted, defaults to system preference.
  Background/border colors transition (~200 ms) on theme flip.
- **Type**: system sans stack; hierarchy via weight (bold titles, medium labels) and slate tints
  rather than many sizes. Timers use large numerals.
- **Surfaces**: rounded-lg/xl/2xl cards with 1 px borders; shadows reserved for floating elements
  (modals, FAB, log panel); glass-morphism only on the login card.
- **Iconography**: lucide line icons throughout (moon, sun, shield, skull, mic, activity, users,
  settings, log-out…), typically 16–24 px paired with labels.
- **Motion**: 200–500 ms, mostly ease-out/springy cubic-beziers — fade+scale for modals
  (`scale 0.95→1`, one bouncy overshoot variant), flash-highlight for remote changes, slide
  left/right for identity swaps, swipe out/in for speaker changes, pulse for live/urgent things
  (connection dot, speaker glow, mic). Custom scrollbars hidden on scroll areas.
- **Buttons**: solid indigo for primary, tinted/ghost slate for secondary, red treatments for
  destructive (with the two-step arm pattern for reset), pill chips for presets/badges.

---

## 6. Redesign Notes — Known Pain Points & Opportunities

1. **Mobile**: the stacked full-width sidebar wastes the first screenful on phones; spectators
   (the mobile-heavy audience) deserve a dedicated lightweight layout (bottom nav / drawer).
2. **The log FAB hides the judge's command center.** Frequently used commands (mute all, timer,
   assign) are buried behind the floating panel; consider promoting a judge action bar.
3. **Phase/pause controls are partly cosmetic** (day/night is not a real backend state machine
   yet) — design them as such or flag for product decision.
4. **No table/compact mode** for the player grid; with 12+ seats judges scroll. A dense list
   toggle could help.
5. **Vote panels and police stage UI live on two screens** (dashboard overlay vs speech
   manager); unifying the "current stage of the game" presentation would reduce confusion.
6. **Settings modal for backend URL** (a developer/ops tool with connection test) is mixed into
   the user-facing UI; tuck it away.
7. **Accessibility** is currently untreated: color-only faction coding (wolf red vs god indigo),
   small unread dot, no reduced-motion path. The redesign should add non-color identity cues and
   honor `prefers-reduced-motion`.
8. Empty/edge states worth designing deliberately: unassigned seats (pre-game), game-over,
   bot disconnected, kicked-mid-session, and the lobby (everything zero).
