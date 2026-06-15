# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`discord/` — the **`DiscordGateway` seam**. Every JDA/Discord call goes through this interface so the
rest of the app is testable and **degrades gracefully without a token**.

- `DiscordGateway` is the only interface the app depends on. **Mutating calls return success/failure,
  never throw** — that's what lets `ops/BulkOperationEngine` isolate per-item faults. Two impls:
  `NoOpDiscordGateway` (default, boots tokenless; REST/WS still serve) and `JdaDiscordGateway` (full
  JDA: provisioning, seat role/channel grants, lavaplayer audio cues, wolf-chat relay via cached
  webhooks, `/server` slash command, night prompts, membership lifecycle).
- `DiscordConfig` picks the impl by token presence (`DiscordProperties.hasToken`). **JDA
  construction is wrapped in try/catch** — a bad token falls back to no-op rather than failing
  startup, because the dashboard must always come up. `DiscordProperties` (`werewolf.discord.*`)
  also carries the OAuth client id/secret/redirect and the `serverCreators` allowlist.
- **No constructor cycle with services.** Services that react to Discord events register *back* into
  the gateway via `@PostConstruct` → `setInteractionHandler` / `setCommandHandler`.
  `DiscordInteractions.kt` defines those handler interfaces (`DiscordInteractionHandler` and
  `DiscordCommandHandler`) plus `InteractionIds` (custom-id constants, namespaced `wh:...`, shared
  between the gateway that builds them and the service that parses them). A single router implements
  each interface and fans out to the owning service — `InteractionRouter` (component ids → `Night`/
  `DayOrchestrator` by namespace) and `CommandRouter` (`/server` → `ServerProvisioningService`,
  `/game detonate` → `DayOrchestrator`). Keep the meaning in the handler, the JDA wiring in the gateway.
- `NicknameService` formats `[死人] 玩家NN [警長]`; `SoundCue` enumerates audio cues; `ChannelKind`
  selects COURT/JUDGE/SPECTATOR shared channels.

When extending the gateway, add the method to the interface **and both impls** (the no-op must stay
a complete, side-effect-free stand-in or the tokenless boot/test path breaks).
