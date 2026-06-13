package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.domain.Faction

/**
 * Data-driven base for canonical roles. Concrete roles are tiny `@Component` subclasses that
 * supply id / nameKey / faction / tags; behaviour beyond classification is expressed as separate
 * NightAbility beans (night actions) and lifecycle-hook overrides (deaths/assignment).
 */
abstract class BaseRole(
    override val id: String,
    override val nameKey: String,
    override val faction: Faction,
    override val tags: Set<RoleTag> = emptySet(),
) : Role
