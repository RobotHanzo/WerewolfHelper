# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`websocket/` — the per-guild realtime hub. This is the second half of snapshot-as-truth: after every
mutation `GameSessionService` broadcasts a full `GameSnapshot` here, and the frontend store replaces
its state wholesale from it.

- `GameWebSocketHandler` keeps a `guildId → sessions` registry. Three event types: `snapshot` (full
  state, after every mutation), `progress` (percent + log line, during bulk operations), `pong`
  (reply to a client `ping`). There are **no per-field patch messages** — don't add any.
- **Sends are serialized per connection** under a per-session lock: concurrent writes to one
  WebSocket throw in the Spring/Jakarta stack. Route any new send through the private `send(...)`
  (or the same lock) — never call `session.sendMessage` directly. Empty per-guild registries are
  removed on disconnect.
- `WebSocketConfig` registers the handshake; auth + the guild binding happen there and land in
  session attributes (`ATTR_GUILD_ID` / `ATTR_USER_ID`). A handshake without a resolvable guild id
  is closed `BAD_DATA`.
- JSON uses the Jackson 3 `tools.jackson.databind.ObjectMapper` bean (Spring Boot 4) — not Jackson 2.
