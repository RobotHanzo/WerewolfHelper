# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`discord/` — the Discord integration. There is **no gateway interface**: services hold a nullable
`JDA?` and call it **directly**. With no token (or a bad one) the `JDA?` bean is `null`, every
`jda?.…` call is a no-op, and the REST API + WebSocket hub still serve fully — the `null` *is* the
degrade-gracefully path (there is no separate no-op implementation to keep in sync).

- **`DiscordConfig`** produces the single `JDA?` bean (nullable on purpose; `null` when
  `DiscordProperties.hasToken` is false or JDA construction throws — wrapped so a bad token degrades
  to `null` rather than failing startup). `DiscordProperties` (`werewolf.discord.*`) also carries the
  OAuth client id/secret/redirect and the `serverCreators` allowlist.
- **`JdaExtensions.kt`** — the stateless `JDA` helper functions services call inline: channel/seat/
  member lookups, messaging (`sendChannelMessage`/`sendSeatEmbed`/`sendCourtButtons`), role +
  nickname mutations (`syncNickname`/`grantSeatRole`/`removeSeatRoles`…), voice (`muteAll`/
  `muteMember`), queries (`isJudge`/`canManage`/`listMembers`), and the night prompt builders
  (`sendSelectMenu`/`sendWolfVote`/`sendWitchChoice`). Every mutating helper is **best-effort**
  (returns on a missing guild/member instead of throwing), which is what lets `ops/BulkOperationEngine`
  isolate per-item faults. Also holds the surviving plain DTOs: `ChannelKind` (COURT/JUDGE/SPECTATOR)
  and `GuildMember` (a projection of a JDA `Member` for the web/snapshot/assignment layers — a JDA
  type never leaks past the seam).
- **`DiscordBot`** (`@Component`) — the irreducible bot runtime that has no service call site to
  inline into: the JDA event loop (the four `ListenerAdapter`s — relay / lifecycle / component /
  command), the audio-cue cache + playback (`playSound`), and the wolf-chat webhook relay (one cached
  webhook per channel). **No constructor cycle with services**: it injects only infrastructure
  (`JDA?`, the session repo, the role registry, i18n); services that react to Discord events register
  *back* into it post-construct (`setInteractionHandler` / `setCommandHandler` / `setWolfChatHandler`
  / `setCourtChatHandler`). `DiscordInteractions.kt` defines those inbound handler interfaces plus
  `InteractionIds` (custom-id constants, namespaced `wh:…`, shared between the prompt builders and the
  service that parses them) and `SeatOption`/`InteractionReply`. A single router implements each
  inbound interface and fans out — `InteractionRouter` (component ids → `Night`/`DayOrchestrator` by
  namespace) and `CommandRouter` (`/server` → `ServerProvisioningService`, `/game detonate` →
  `DayOrchestrator`).
- **`GuildProvisioner`** (`@Component`) — guild provisioning (`provisionGuild`/`resizeGuild`/
  `deleteGuild`); the resize streams progress through `BulkOperationEngine`. With a null `JDA?` it
  still keeps the in-memory seat list in sync (all the tokenless/dev path needs).
- `NicknameService` formats `[死人] 玩家NN [警長]`; `SoundCue` enumerates audio cues;
  `AudioPlayerSendHandler` bridges lavaplayer to JDA voice.

When adding a Discord operation: if a service calls it, add a `JDA` extension in `JdaExtensions.kt`
(keep it best-effort + null-safe) and call it `jda?.…` from the service. Only put it on `DiscordBot`
if it is event-driven or holds shared mutable state (a cache, the audio/voice connection).
