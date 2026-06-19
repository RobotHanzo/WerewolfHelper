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

## Production deployment

Production is a **single self-contained Spring Boot JAR**: `bootJar` builds the React app and packs
its `dist/` onto the classpath (`classpath:/static`), so the one process serves the dashboard, the
REST API, and the WebSocket on the same port and origin. (Frontend → backend calls are relative
`/api` and `/ws`, so same-origin is automatic — no CORS, first-party session cookie.)

### Prerequisites

- **Java 25** JRE on the host (the build toolchain is Java 25; the wrapper is Gradle 9.3.1).
  Building the jar also needs **Node + Yarn** (to compile the frontend); the runtime host only
  needs the JRE.
- A reachable **MongoDB** (game state, the audit log, and the dashboard session store).
- A **Discord application**: a bot token, and OAuth2 client id/secret with the redirect URI set to
  `https://<your-domain>/api/auth/callback`. Invite the bot with the `bot` + `applications.commands`
  scopes and Administrator permission (provisioning creates roles/channels and manages nicknames).

### Configuration (environment variables)

| Variable | Purpose |
|---|---|
| `MONGODB_URI` | e.g. `mongodb://user:pass@host:27017/werewolf` |
| `DISCORD_TOKEN` | bot token (absent → no-op bot; REST/WS still serve) |
| `DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET` | OAuth2 app credentials |
| `DISCORD_REDIRECT_URI` | `https://<your-domain>/api/auth/callback` |
| `DISCORD_SERVER_CREATORS` | comma-separated Discord user ids allowed to run `/server create` |
| `DASHBOARD_BASE_URL` | public dashboard URL (used for the post-OAuth redirect and dashboard links) |
| `PORT` | HTTP port (default `8080`) |

### Build & run

```bash
cd backend
./gradlew clean bootJar                # builds the frontend and bundles it → build/libs/werewolf-helper-1.0.0.jar
java --enable-native-access=ALL-UNNAMED -jar build/libs/werewolf-helper-1.0.0.jar
```

- `bootJar` runs `yarn install && yarn build` in `../frontend` and packs the result into the jar.
  Pass `-PskipFrontend` to build a backend-only jar (e.g. CI without Node).
- `--enable-native-access=ALL-UNNAMED` is required on Java 25 for the lavaplayer/jdave voice
  natives. Run the jar as a service (systemd / container) with the environment variables above.

### Container (Docker)

A multi-stage [`Dockerfile`](Dockerfile) builds the same single self-contained jar (frontend +
backend) and ships it on a JRE 25 runtime. CI publishes the image to GHCR on every push to `main`
and on `v*` tags (`.github/workflows/docker-publish.yml`).

```bash
cp .env.example .env                        # fill in Discord creds (all optional)
docker compose up -d --build                # build locally + run app and MongoDB
# or pull the published image instead of building:
docker compose pull && docker compose up -d
```

`docker-compose.yml` runs the app alongside a `mongo` service (data on a named volume). The app's
`MONGODB_URI` defaults to that bundled MongoDB — set it in `.env` to point at an external database
instead. The image (`ghcr.io/robothanzo/werewolfhelper`) can also be run standalone:

```bash
docker run -d -p 8080:8080 \
  -e MONGODB_URI=mongodb://host:27017/werewolf \
  -e DISCORD_TOKEN=... -e DISCORD_CLIENT_ID=... -e DISCORD_CLIENT_SECRET=... \
  ghcr.io/robothanzo/werewolfhelper:latest
```

### TLS / reverse proxy (optional)

The jar already serves everything on one port, so a proxy is only needed for TLS termination. If you
use one, forward **all** traffic to the backend and pass the WebSocket upgrade headers — e.g. nginx:

```nginx
server {
  listen 443 ssl;
  server_name your-domain;

  location / { proxy_pass http://127.0.0.1:8080; }

  location /ws {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;                          # keep the long-lived socket open
  }
}
```

(Hosting the SPA on a separate origin is possible but then you must add that origin to the CORS
allowlist in `backend/.../config/SecurityConfig.kt`, which ships allowing `localhost` only.)

### First run

Once the bot is online, a server creator runs **`/server create <players> [double]`** (in any guild
they share with the bot), creates a fresh Discord server, and invites the bot — it auto-provisions
the roles/channels and registers the session, after which the judge opens the dashboard at
`DASHBOARD_BASE_URL` and signs in with Discord.

## Design

The dashboard implements the **月光主控台 "Moonlit console"** design system: deep blue-black
night surfaces, one cold moonlight-cyan accent, faction colours (狼 red / 神 violet / 民 green)
reserved strictly for game semantics, and gold reserved exclusively for the police badge (警長).
Tokens live in `frontend/src/theme/tokens/`.
