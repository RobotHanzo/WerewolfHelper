# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`controller/` — thin REST entry points; **all real work is in `service/`**. Routes hang off
`/api/sessions/{guildId}` (Game/Members/Roles/Settings/Sessions) plus `AuthController`.

- **Every mutating action funnels through `GameSessionService.mutate`** (or a `GameActionService`
  method that does), so the response is just an `ApiResponse.ok()` envelope — the real result reaches
  the client as the broadcast snapshot, not the HTTP body. Don't add per-field response payloads for
  mutations; let the snapshot flow.
- **Authorization is per-method**, via `@CanManageGuild` (judge) / `@CanViewGuild` (spectator+) on
  the path's `#guildId` — see `security/`. The filter chain permits everything; the guards decide.
- OpenAPI is hand-annotated in the proven auto-branch style: `@Tag` on the class, `@Operation` +
  `@ApiResponses` per method (note the `io.swagger...ApiResponse as SwaggerApiResponse` alias to
  avoid colliding with our own `dto.ApiResponse`). Surfaced at `/scalar`.
- Entering `Phase.NIGHT` (in `start`/`next`) is a two-step: `mutate` to flip the phase, **then**
  `night.startNight(...)` outside the mutate. Timer endpoints schedule cancellable `GameScheduler`
  jobs (final + 30s warning) and must cancel them on stop.
- Day-phase role actions are their own seat endpoints: `POST /seats/{seat}/revenge` (fire an armed
  獵人/狼王/白狼王 shot, via `GameActionService`), `/seats/{seat}/duel` (騎士 決鬥) and
  `/seats/{seat}/self-destruct` (自爆), both via `DayOrchestrator`. The latter two return whether the
  game must enter night; when they do, the controller runs the same `night.startNight` two-step.

`controller/dto/` — the wire contract. `ApiResponse` is the envelope (`success`/`message`/`error`);
data endpoints return typed subclasses with a `data` field so the OpenAPI schema **and the
hand-written frontend types stay precise**. `GameSnapshot` is the full broadcast state; `RequestDtos`
holds request bodies. Keep these in sync with `frontend/src` types by hand — there's no codegen.
