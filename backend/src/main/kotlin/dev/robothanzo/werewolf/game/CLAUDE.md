# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`game/` — the **pure, deterministic game engine**. No Spring web, no Mongo I/O, no JDA here:
everything operates on `domain` objects (and injected collaborators like `RoleRegistry`), so it is
unit-tested in isolation (see `src/test/.../game/**` and `support/TestFixtures`). Inject `Random`
where randomness is needed (assignment) so tests are reproducible. Side effects live in `service/`
and `discord/`; keep logic here and wire it through a service.

Sub-areas:
- `roles/` — the identity registry and the 28 role beans (extensibility core; see its own CLAUDE.md).
- `night/` — the dependency-graph night engine (the most complex part; see its own CLAUDE.md).
- `day/` — `DuelResolver`: the pure 騎士 決鬥 rule (who dies, was the target a wolf). The day-side
  side effects (arming the death, entering night) live in `service/DayOrchestrator`.
- `win/` — `WinConditionChecker`: living-identity faction tally, 金寶寶 (double mode), parity with
  the police +0.5 bonus, all-god setups. The same checker feeds both the engine and the snapshot
  meters, so the dashboard and the rules never disagree.
- `assign/` — `AssignmentService`: shuffle + deal with the 金寶寶 cap, 複製人 copy, and wolf-card
  -second ordering; throws `AssignmentException(messageKey)` for actionable zh-TW validation errors.
- `vote/` — `PollEngine` shared by police election and expel: stage-gated enroll/withdraw,
  switch/retract, police ×1.5, a single PK runoff, dead-白癡 exclusion.
- `speech/` — `SpeechService`: alive-only wrap-around ordering (UP/DOWN) + interrupt majority.
- `flow/` — `GameFlowService` (coarse phase machine; day-1 runs the police election) and the
  **cancellable** `GameScheduler` (every interrupt path must cancel running countdowns).

`GameConstants` holds the FEATURES §12 timings/weights.
