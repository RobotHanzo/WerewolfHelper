package dev.robothanzo.werewolf.game.model

/**
 * Enum representing all possible action definition IDs in the game.
 * Provides type safety and prevents typos in action ID references.
 * The enum name itself serves as the ID (via toString()).
 */
enum class ActionDefinitionId(val actionName: String) {
    // Werewolf Actions
    WEREWOLF_KILL("擊殺"),
    WOLF_YOUNGER_BROTHER_EXTRA_KILL("狼弟復仇刀"),

    // Witch Actions
    WITCH_ANTIDOTE("解藥"),
    WITCH_POISON("毒藥"),

    // Seer Actions
    SEER_CHECK("查驗"),

    // Guard Actions
    GUARD_PROTECT("守護"),

    // Hunter Actions
    HUNTER_REVENGE("開槍"),

    // Wolf King Actions
    WOLF_KING_REVENGE("復仇"),

    // Dark Merchant Actions
    DARK_MERCHANT_TRADE_SEER("交易 (預言家查驗)"),
    DARK_MERCHANT_TRADE_POISON("交易 (女巫毒藥)"),
    DARK_MERCHANT_TRADE_GUN("交易 (獵人獵槍)"),

    // Merchant Gift Actions
    MERCHANT_SEER_CHECK("查驗 (商人贈予)"),
    MERCHANT_POISON("毒藥 (商人贈予)"),
    MERCHANT_GUN("獵槍 (商人贈予)"),
    MERCHANT_GUARD_PROTECT("守衛 (商人贈予)"),

    // Miracle Merchant Actions
    MIRACLE_MERCHANT_TRADE_GUARD("交易 (守衛守護)"),

    // Dream Weaver Actions
    DREAM_WEAVER_LINK("攝夢"),

    // Nightmare Actions
    NIGHTMARE_FEAR("恐懼"),

    // Ghost Rider Actions
    GHOST_RIDER_REFLECT("反傷"),

    // System Actions
    DEATH_RESOLUTION("結算"),
    DREAM_DEATH("夢亡"),
    DEATH("死亡");

    companion object {
        /**
         * Get ActionDefinitionId from string ID
         */
        fun fromString(id: String): ActionDefinitionId? {
            return entries.find { it.toString() == id }
        }
    }
}
