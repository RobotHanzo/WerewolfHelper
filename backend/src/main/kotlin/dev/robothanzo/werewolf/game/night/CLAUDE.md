# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`game/night/` — the **dependency-graph night engine**. This replaced a flat `priority: Int` list and
is the reason for the rebuild — **never reintroduce a linear priority**.

- `Effect` — the night "channels" (SWAP, FEAR, WOLF_KILL, GUARD_PROTECT, WITCH_SAVE/POISON,
  INVESTIGATE, CHARM, HUNT, …).
- `NightAbility` — each declares the channels it `reads` and `writes` (plus targetCount/optional/
  firstNightOnly). The concrete abilities are beans in `abilities/`.
- `NightPlanner.plan(active)` — builds a DAG (B depends on A iff `B.reads ∩ A.writes ≠ ∅`) and
  longest-path-layers it into **waves**: abilities in the same wave are independent and prompt
  **simultaneously**; a dependency cycle throws `NightCycleException` (fail fast — tested).
- `NightResolver.resolve(declarations, session)` — applies the GAMEPLAY.md interaction matrix in one
  place: swap redirection (魔術師), fear voiding (夢魘), 同守同救=死, witch save/poison, 狼美人 殉情
  ordering, poison-suppresses-revenge, 獵魔人 hunt. Returns the ordered death list. **This is where
  interaction rules go — extend `NightResolverTest` (the rule matrix) alongside any change.**
- `NightDeclarationsBuilder` — folds the persisted `domain.NightState` (collected Discord
  submissions + wolf button-vote consensus, ties → lowest seat) into `NightDeclarations` for the
  resolver.

`NightLedger`/`NightIntent` are the in-engine accumulation types; the **live orchestration**
(prompting players, collecting clicks, scheduling the deadline, applying deaths) is
`service/NightOrchestrator`, not here. Keep this package pure and side-effect-free.
