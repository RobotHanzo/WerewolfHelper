package dev.robothanzo.werewolf.domain

/**
 * The three win-condition factions. A role's faction is explicit registry data
 * (see `RoleRegistry`), not inferred at call sites — but the historical name-based rule
 * (name contains 狼, plus 石像鬼/血月使者/惡靈騎士 → WOLF; 平民 → VILLAGER; else GOD) is kept
 * as a fallback for unknown/custom identity names.
 */
enum class Faction(val glyphKey: String) {
    WOLF("faction.wolf"),
    GOD("faction.god"),
    VILLAGER("faction.vill");

    companion object {
        private val WOLF_NAME_EXCEPTIONS = setOf("石像鬼", "血月使者", "惡靈騎士")

        /** Fallback classification by Chinese identity name, per FEATURES §2. */
        fun fromName(name: String): Faction = when {
            name.contains("狼") -> WOLF
            name in WOLF_NAME_EXCEPTIONS -> WOLF
            name == "平民" -> VILLAGER
            else -> GOD
        }
    }
}
