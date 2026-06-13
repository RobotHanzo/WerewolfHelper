package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.i18n.Msg
import org.springframework.stereotype.Service

/**
 * Single source of truth for the playable identities. Spring injects every [Role] bean, so adding
 * a role anywhere on the classpath registers it automatically — the registry, the `/api/roles`
 * list, Discord autocomplete, win conditions, and the frontend all pick it up with no other change.
 */
@Service
class RoleRegistry(roles: List<Role>, private val msg: Msg) {

    private val byId: Map<String, Role> = roles.associateBy { it.id }
    private val byName: Map<String, Role> = roles.associateBy { msg.msg(it.nameKey) }

    /** All registered roles, ordered by faction then id for a stable canonical listing. */
    val all: List<Role> = roles.sortedWith(compareBy({ it.faction.ordinal }, { it.id }))

    fun byId(id: String): Role? = byId[id]

    fun require(id: String): Role =
        byId[id] ?: throw IllegalArgumentException("Unknown roleId: $id")

    /** Look up by localized (zh-TW) name — used by Discord command input and the pool editor. */
    fun byName(name: String): Role? = byName[name]

    fun localizedName(id: String): String = byId[id]?.let { msg.msg(it.nameKey) } ?: id

    /**
     * Resolve a role's faction. Registered roles carry explicit faction data; unknown/custom names
     * fall back to the FEATURES §2 name rule.
     */
    fun factionOf(id: String): Faction = byId[id]?.faction ?: Faction.fromName(id)

    /** Canonical localized names, capped at 25 for Discord's autocomplete hard limit. */
    fun autocompleteNames(query: String): List<String> =
        all.map { msg.msg(it.nameKey) }
            .filter { query.isBlank() || it.contains(query) }
            .take(25)
}
