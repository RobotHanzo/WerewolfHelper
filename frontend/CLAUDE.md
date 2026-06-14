# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Frontend for WerewolfHelper — the judge console + spectator God's view. See the repo-root
`CLAUDE.md` for cross-cutting context; this file is the frontend deep-dive. React 19 · Vite ·
TypeScript · Yarn · `lucide-react` · `framer-motion` · `zustand` · `react-i18next`.

## Commands

- `yarn install`, then `yarn dev` (http://localhost:5173; `vite.config.ts` proxies `/api` and `/ws`
  to `:8080`). `@/` aliases `src/`.
- `yarn typecheck` (`tsc --noEmit`), `yarn test` (vitest, jsdom), `yarn build`
  (`tsc --noEmit && vite build` — typecheck failures fail the build).

## Architecture — the load-bearing ideas

**Snapshot-as-truth.** The backend pushes a full `GameSnapshot` after every mutation; the store
replaces its state wholesale. `stores/gameStore.ts` owns `snapshot`, `connected`, `sessionExpired`,
the per-seat change set, unread-log count, and the long-op progress overlay. `applySnapshot` diffs
the previous vs new snapshot (`diffChangedSeats`, exported + unit-tested) to populate `changedSeats`,
which drives the `.wh-changed` "this just changed" cue on the roster. Optimistic local updates are
fine; the next snapshot wins.

**Types mirror the backend DTOs by hand** (`types/snapshot.ts`) — keep them in lock-step with the
OpenAPI schema. Identities carry `roleId` + localized `name` + `faction`; **never hardcode a role
name** — faction → colour is via the enum only. Seats also carry the ROLES.md state flags
(`revengePending`, `idiotRevealed`, `knifeArmed`, `learnedRoleId`, `loverSeat`/`charmedSeat`) —
`PlayerCard` renders these as state badges and gates the judge's day-action picker
(revenge/duel/self-destruct, a two-step target select wired through `useGameActions`). New 房規
toggles live on `Settings` (`witchSelfSave`, `hiddenWolfInheritsKnife`).

**Data layer.** `api/client.ts` is a typed fetch wrapper over the backend's `ApiResponse` envelope
(throws `ApiError` on `!success`). `api/ws.ts` is the per-guild `GameSocket`: 15 s heartbeat,
exponential reconnect backoff (`nextBackoff`, exported + tested: 1 s → ×1.5 → cap 10 s), and it
distinguishes an **expired session** (close code 4001 → re-login modal) from a **network blip**
(→ reconnect). `hooks/useGameActions.ts` bridges UI → API, and in **demo mode** patches the local
snapshot optimistically instead.

**Demo mode.** `App.tsx` tries `/api/auth/me`; on failure it seeds `mock/scenarios.ts` and enters
the dashboard as a judge so every screen renders without a backend. Real mode connects the WS in
`GameSurface`. The `demo` flag flows through to `useGameActions`.

**Stores** (`stores/`): `gameStore` (above), `authStore` (app status machine:
loading→login→servers→lockout/blocked→ready, + `demo`), `uiStore` (current screen, roster density,
spectator-preview toggle, overlay state, toasts), `themeStore` (persisted dark/light, system
default; sets `data-theme`).

**Components & screens.** `components/ui/` is the design-system kit (Button with `armed` two-step,
faction Badge with 狼/神/民 glyphs, Avatar, endsAt-based Countdown with the `.wh-urgent` final-10s
pulse, LiveIndicator, FactionMeter, ProgressBar, LogFeed, PlayerCard, Switch, Stepper, Modal,
Toast). `screens/` are composed from those: `AppShell` (role-aware nav + disconnected banner),
`Dashboard` (status header, `NightBoard`, roster in 3 densities, log + judge commands),
`SpeechManager` (speaking stage + vote panel), `Spectator`, `Settings`, `Overlays`. The `AppShell`
chooses judge vs read-only (spectator / preview) per screen.

## Design system (月光主控台)

Tokens are copied from the Claude Design handoff into `src/theme/tokens/*.css` (consumed via CSS
custom properties; **don't hardcode hex** — use `var(--…)`). Conventions: faction/state coding
never relies on colour alone (glyphs + strike-through), gold is reserved for the police badge,
`.wh-changed` / `.wh-urgent` are the two sanctioned animations, and all motion is gated behind
`prefers-reduced-motion`. framer-motion handles speaker-handoff crossfade, roster layout, vote-bar
reflow, and modal fade+scale.

## i18n

All UI copy lives in `src/i18n/zh-TW.json` (parameterized; `react-i18next`, default + only locale
`zh-TW`). Add a key rather than inlining a string; `test/i18n.test.ts` guards completeness.

## Tests

vitest covers the pure logic that's easy to get wrong: countdown math (`formatClock`/`secondsUntil`),
WS backoff, snapshot seat-diffing, and i18n key coverage. Add cases there when touching those.
