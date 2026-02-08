package dev.robothanzo.werewolf.game.model

/**
 * Enum representing all possible action definition IDs in the game.
 * Provides type safety and prevents typos in action ID references.
 * The enum name itself serves as the ID (via toString()).
 */
enum class ActionDefinitionId {
    // Werewolf Actions
    WEREWOLF_KILL,
    WOLF_YOUNGER_BROTHER_EXTRA_KILL,

    // Witch Actions
    WITCH_ANTIDOTE,
    WITCH_POISON,

    // Seer Actions
    SEER_CHECK,

    // Guard Actions
    GUARD_PROTECT,

    // Hunter Actions
    HUNTER_REVENGE,

    // Wolf King Actions
    WOLF_KING_REVENGE,

    // Dark Merchant Actions
    DARK_MERCHANT_TRADE_SEER,
    DARK_MERCHANT_TRADE_POISON,
    DARK_MERCHANT_TRADE_GUN,

    // Merchant Gift Actions
    MERCHANT_SEER_CHECK,
    MERCHANT_POISON,
    MERCHANT_GUN,

    // System Actions
    DEATH_RESOLUTION,
    DEATH;

    companion object {
        /**
         * Get ActionDefinitionId from string ID
         */
        fun fromString(id: String): ActionDefinitionId? {
            return entries.find { it.toString() == id }
        }

        /**
         * Check if a string ID is valid
         */
        fun isValid(id: String): Boolean {
            return entries.any { it.toString() == id }
        }
    }
}
