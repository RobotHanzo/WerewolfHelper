package dev.robothanzo.werewolf.game.night

/**
 * A role's night action, declared in terms of the [Effect] channels it reads and writes. This is
 * what replaces the old flat `priority: Int` — the planner reconstructs ordering from the
 * read/write dependencies, so independent actions provably run together and a new ability only has
 * to declare its channels (no global re-numbering).
 *
 * Adding a future night ability = one new bean implementing this interface (plus i18n + a Role).
 */
interface NightAbility {
    /** Stable id for the ability (distinct from the role id; a role may have several abilities). */
    val id: String

    /** The role that owns this ability. */
    val roleId: String

    /** Channels this ability consumes — creates a dependency on whoever writes them. */
    val reads: Set<Effect>

    /** Channels this ability produces. */
    val writes: Set<Effect>

    /** How many targets the prompt collects (0 = passive/info, e.g. 守墓人). */
    val targetCount: Int get() = 1

    /** Whether the actor may decline to act (skip). */
    val optional: Boolean get() = true

    /** Only relevant on the first night (邱比特 / 盜賊 / 混血兒). */
    val firstNightOnly: Boolean get() = false

    /** Whether the actor may target their own seat (e.g. 狼人 self-knife). */
    val mayTargetSelf: Boolean get() = false
}
