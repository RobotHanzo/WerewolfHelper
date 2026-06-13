# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`domain/` — MongoDB documents and repositories. Plain Kotlin data classes; no behaviour beyond
small derived helpers.

- `GameSession` (`@Document`, **id = guildId**) is the aggregate root: settings, the identity
  `pool` (roleId → count), `seats`, `phase`/`day`/`paused`, `policeSeat`, `discordIds`, and
  `nightState`. One document per Discord guild.
- **Soft death is per-`IdentityCard`** (`Seat.cards`, each with a mutable `dead`). A seat is alive
  while any card lives (`Seat.alive`) — there is no boolean "dead" on the seat. Double-identity
  mode relies on this; never collapse it to a single flag.
- `NightState` + `NightIntentData` are the **persisted** night round (so it survives restart and
  the snapshot can render the board) — the engine in `game/night` reconstructs its working types
  from these. `DiscordIds` / `Seat.roleId`/`channelId` hold provisioned Discord ids (0 = not yet
  provisioned).
- `GameLogEntry` stores the message **key + params and** the rendered zh-TW text (own collection,
  indexed by guild, cleared on reset).

Repositories are in `repo/` (Spring Data Mongo interfaces). These docs are pure data; game rules
live in `game/`, side effects in `service/` and `discord/`.
