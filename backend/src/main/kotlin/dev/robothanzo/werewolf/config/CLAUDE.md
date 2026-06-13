# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`config/` — Spring `@Configuration` beans. Small, but two are load-bearing:

- `SecurityConfig` — `@EnableMethodSecurity` + a filter chain that `permitAll`s every request. CSRF
  off, CORS open to `localhost:*` with credentials. **Authorization is enforced per-method** by
  `@CanManageGuild` / `@CanViewGuild` (see `security/`), not here. Don't add path-based rules
  expecting them to gate access — they won't replace the method guards.
- `SpaConfig` — serves the bundled SPA from `classpath:/static` (packed by `bootJar`) and **falls
  back to `index.html`** so deep-link refreshes work. It explicitly returns `null` for `api/` and
  `ws`/`ws/*` so those never fall through to the SPA (their handlers take precedence regardless).
- `OpenApiConfig` — the OpenAPI `Info` bean; Scalar UI at `/scalar`.

Note: Discord/i18n/websocket each own their config in their own package
(`DiscordConfig`, `I18nConfig`, `WebSocketConfig`), not here.
