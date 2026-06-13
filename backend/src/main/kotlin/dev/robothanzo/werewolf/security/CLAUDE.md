# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`security/` — Discord OAuth2 → Mongo-backed HTTP session → per-method authorization.

- `@CanManageGuild` / `@CanViewGuild` (in `annotations/`) are `@PreAuthorize` meta-annotations that
  call the **`identityUtils`** bean against the path's `#guildId`. `config/SecurityConfig` enables
  method security and otherwise `permitAll` — **the guards, not the filter chain, enforce access.**
- `IdentityUtils` (bean name `identityUtils`, referenced by SpEL in the annotations) resolves the
  current user via `CurrentUser` and delegates to `DashboardRoleService`. `CurrentUser` reads the
  user id from the HTTP session.
- `DashboardRoleService` derives the role (FEATURES §11.1): explicit `DashboardUser` override wins,
  else a `serverCreators`-allowlisted user is JUDGE, else SPECTATOR.
- **The active-player lockout is the load-bearing rule here.** A logged-in user who *holds a seat in
  the running game* must never reach the dashboard (they'd see every identity). `IdentityUtils`
  throws `ActivePlayerLockoutException` for such users **before** any role check, in both `canManage`
  and `canView`. Preserve this short-circuit if you touch either method.
