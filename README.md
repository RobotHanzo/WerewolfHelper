# 狼人殺助手 · WerewolfHelper

Hosts Chinese Werewolf (狼人殺) games on Discord — **one Discord guild = one game session** —
with a real-time web dashboard for the judge (法官) and a read-only God's view for spectators
(旁觀者). The Discord bot and the dashboard are two thin interfaces over a single game engine:
after **every** state mutation the backend broadcasts a full game-state snapshot to that guild's
WebSocket clients.

This is a from-scratch rebuild driven by the specs in [`instructions/`](instructions/)
(`FEATURES.md`, `GAMEPLAY.md`, `UI_SPEC.md`).

## Architecture

```
backend/   Kotlin · Spring Boot 4 · MongoDB · JDA · Gradle (Kotlin DSL)
frontend/  React 19 · Vite · TypeScript · Yarn · Lucide · framer-motion · zustand
```

The night phase is driven by a **dependency-graph engine** (not a linear priority list):
each role ability declares the night "channels" (`Effect`s) it reads and writes, and the
`NightPlanner` topologically sorts abilities into **waves** — independent abilities in the same
wave act simultaneously. See `backend/.../game/night`.

Roles are **registry-driven** for future expansion: adding a role is one new `@Component`
`Role` bean (plus optional `NightAbility` beans and i18n keys) — no engine, controller, or
frontend changes. The frontend renders identities purely from server data (`roleId`, localized
name, `faction`); no role names are hardcoded in the UI.

Everything player-facing is internationalised, with **zh-TW (繁體中文 · 台灣)** as the default
and only shipped locale today.

## Development

### Backend

```bash
cd backend
./gradlew build          # compile + unit tests
./gradlew bootRun        # needs a local MongoDB; Discord token optional
```

Without a `DISCORD_TOKEN` the bot layer degrades gracefully behind its interface — the REST API
and WebSocket hub still serve. OpenAPI (Scalar) renders at `/scalar`.

### Frontend

```bash
cd frontend
yarn install
yarn dev                 # http://localhost:5173 (proxies /api and /ws to :8080)
yarn typecheck && yarn test && yarn build
```

## Design

The dashboard implements the **月光主控台 "Moonlit console"** design system: deep blue-black
night surfaces, one cold moonlight-cyan accent, faction colours (狼 red / 神 violet / 民 green)
reserved strictly for game semantics, and gold reserved exclusively for the police badge (警長).
Tokens live in `frontend/src/theme/tokens/`.
