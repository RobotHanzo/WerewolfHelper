package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.domain.Faction

/**
 * Behavioural tags that the engine and Discord layer key off of, kept separate from the
 * win-condition [Faction] so new behaviours never require touching the faction enum.
 */
enum class RoleTag {
    /** Participates in the secret wolf-chat relay (wolves, 夢魘, …). */
    WOLF_CHAT,

    /** Counts toward gods for win conditions even if not faction GOD (e.g. a living 複製人). */
    COUNTS_AS_GOD,

    /** Can self-target with its night ability (e.g. 狼人 self-knife, 女巫 self-save by board). */
    MAY_SELF_TARGET,

    /** Acquires a one-shot revenge kill on death (狼王 / 獵人 / 白狼王). */
    DEATH_REVENGE,

    /** First-night-only ability (邱比特 / 盜賊 / 混血兒). */
    FIRST_NIGHT_ONLY,

    /** Reads as 好人 to 預言家 / 通靈師 investigations (隱狼). */
    INVESTIGATED_AS_GOOD,

    /** Inherits the wolf knife once the chat wolves are dead (石像鬼 / 隱狼 / 機械狼). */
    INHERITS_KILL,

    /** Death-revenge fires only via 自爆, never on a normal death (白狼王). */
    REVENGE_ON_SELF_DESTRUCT_ONLY,
}

/**
 * A playable identity. The registry collects every [Role] bean; adding a future role is a single
 * new bean (plus optional [dev.robothanzo.werewolf.game.night.NightAbility] beans and i18n keys) —
 * no engine, controller, or frontend change.
 */
interface Role {
    /** Stable ASCII id, used in persistence, the API, and the frontend (never localized). */
    val id: String

    /** i18n key for the display name (zh-TW today), e.g. `role.wolf` → 狼人. */
    val nameKey: String

    /** Explicit win-condition faction. */
    val faction: Faction

    val tags: Set<RoleTag>

    /** Hook fired once when this identity is dealt to a seat. */
    fun onAssigned(ctx: RoleLifecycleContext) {}

    /** Hook fired when this identity dies (badge transfer prompts, revenge grants, …). */
    fun onDeath(ctx: RoleLifecycleContext) {}

    fun hasTag(tag: RoleTag): Boolean = tag in tags
}

/**
 * Minimal context handed to lifecycle hooks. Kept deliberately small so role beans stay pure and
 * testable; richer interactions go through the night engine and services.
 */
data class RoleLifecycleContext(
    val guildId: Long,
    val seatNumber: Int,
    val day: Int,
)
