# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`domain/` — MongoDB documents and repositories. Plain Kotlin data classes; no behaviour beyond
small derived helpers.

- `GameSession` (`@Document`, **id = guildId**) is the aggregate root: settings, the identity
  `pool` (roleId → count), `seats`, `phase`/`day`/`paused`, `policeSeat`, `discordIds`, and
  `nightState`. Also `lastExpelledSeat` (the 守墓人 reads its faction from night 2) and
  `bloodMoonSeal` (a 血月使徒 自爆 voids the next night's 神職 abilities + wolf knife). One document
  per Discord guild.
- `GameSettings` carries the 房規 toggles: `doubleIdentity`, `muteAfterSpeech`, `witchSelfSave`
  (女巫 may antidote herself), `hiddenWolfInheritsKnife` (隱狼 takes the knife as the last wolf
  instead of auto-dying). All are default-safe and Mongo-additive.
- **Soft death is per-`IdentityCard`** (`Seat.cards`, each with a mutable `dead`). A seat is alive
  while any card lives (`Seat.alive`) — there is no boolean "dead" on the seat. Double-identity
  mode relies on this; never collapse it to a single flag.
- **`Seat` also holds the ROLES.md behavioural state** (all default-safe, Mongo-additive):
  `loverSeat`/`charmedSeat` (殉情 bonds), `revengePending` (an armed 獵人/狼王/白狼王 shot),
  `idiotRevealed` (白癡 flipped on expel — alive but voteless), `learnedRoleId` (機械狼),
  `knifeArmed` (石像鬼/隱狼 inherited the knife), `duelUsed` (騎士), `bloodMoonRevived` (血月使徒
  last-wolf expel survival). These are written by `service/DeathService` and the orchestrators, not
  by the role beans.
- `NightState` + `NightIntentData` are the **persisted** night round (so it survives restart and
  the snapshot can render the board) — the engine in `game/night` reconstructs its working types
  from these. `DiscordIds` / `Seat.roleId`/`channelId` hold provisioned Discord ids (0 = not yet
  provisioned).
- `GameLogEntry` stores the message **key + params and** the rendered zh-TW text (own collection,
  indexed by guild, cleared on reset).

Repositories are in `repo/` (Spring Data Mongo interfaces). These docs are pure data; game rules
live in `game/`, side effects in `service/` and `discord/`.
