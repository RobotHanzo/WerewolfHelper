# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`ops/` — `BulkOperationEngine`: the one way bulk Discord mutations run (FEATURES §10.1–10.4). Used by
`service/DiscordOpsService` (assignment / reset) and `discord/GuildProvisioner` (provision / resize).

- A `BulkOperation` is a list of `BulkPhase`s, each a list of `BulkItem`s (`description` + suspend
  `run`). The engine, per phase:
  - **Barrier** — awaits every item (success *or* failure) before the next phase.
  - **Per-item fault isolation** — a failure logs `[失敗] …: <原因>` and the batch continues;
    successes log `[完成] …`. This is why the `JdaExtensions` mutating helpers return rather than throw.
  - **Percent mapping** — each phase fills its own `percentStart..percentEnd` sub-range so the bar
    advances smoothly across phases.
  - **Timeout** — a phase that doesn't drain within `phaseTimeoutMillis` (default 120s) emits
    `部分操作逾時` and stops, surfacing what completed.
- Items run **sequentially**, deliberately leaning on the Discord library's own rate-limit
  serialization — no manual sleeps or parallel blasts.
- Progress is pushed through a `ProgressSink`, which the callers bridge to
  `GameWebSocketHandler.broadcastProgress` (percent + log line) over the guild's WebSocket.

Phase ordering encodes FEATURES §10.2 **critical-before-cosmetic**: role/nickname grants are their
own phase, ahead of notification-message phases. Keep that ordering when composing new operations.
