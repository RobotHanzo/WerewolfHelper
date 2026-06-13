# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`game/roles/` — the **identity registry**: the extensibility core that lets a new role be one bean.

- `Role` is the interface (stable ASCII `id`, i18n `nameKey`, explicit `faction`, `tags`, and the
  `onAssigned`/`onDeath` lifecycle hooks). `BaseRole` is the data-driven base; concrete roles in
  `impl/` are tiny `@Component` subclasses (`GodRoles`, `WolfRoles`, `VillagerRole`) supplying only
  id / nameKey / faction / tags. `RoleIds` holds the id constants.
- `RoleRegistry` (`@Service`) injects **every** `Role` bean on the classpath — so adding a role
  registers it everywhere automatically: `/api/roles`, Discord autocomplete, win conditions, and the
  frontend. Look-ups: `byId`, `require`, `byName` (localized zh-TW name, used by Discord input + the
  pool editor), `localizedName`, `factionOf` (falls back to `Faction.fromName` for unknown ids,
  FEATURES §2), `autocompleteNames` (capped at 25 for Discord's hard limit).
- **`RoleTag` is behaviour, separate from `Faction` (win condition)** — so a new behaviour never
  touches the faction enum. Tags drive engine/Discord decisions: `WOLF_CHAT` (joins the wolf-chat
  relay), `COUNTS_AS_GOD` (living 複製人 counts toward gods), `MAY_SELF_TARGET`, `DEATH_REVENGE`
  (狼王/獵人/白狼王 one-shot), `FIRST_NIGHT_ONLY` (邱比特/盜賊/混血兒).

## Adding a role

One new `@Component : BaseRole` here + its `nameKey` in `messages_zh_TW.properties`. If it acts at
night, add `NightAbility` bean(s) in `game/night/abilities/` (declare `reads`/`writes` channels —
never a priority int) and extend `NightResolverTest`. No engine, controller, or frontend change.
Keep beans pure: richer interactions belong in the night engine and services, not in the hooks.
