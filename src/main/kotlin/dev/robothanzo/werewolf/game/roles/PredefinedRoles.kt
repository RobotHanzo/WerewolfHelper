package dev.robothanzo.werewolf.game.roles

object PredefinedRoles {

    // Priority constants
    const val WEREWOLF_PRIORITY = 100
    const val WITCH_ANTIDOTE_PRIORITY = 200
    const val WITCH_POISON_PRIORITY = 210
    const val SEER_PRIORITY = 300
    const val GUARD_PRIORITY = 150
    const val HUNTER_PRIORITY = 250
    const val POLICE_PRIORITY = 400
    const val DARK_MERCHANT_PRIORITY = 50
    const val DREAM_WEAVER_PRIORITY = 60 // Before wolves
    const val NIGHTMARE_PRIORITY = 0 // First thing at night

    // Action IDs
    const val WEREWOLF_KILL = "WEREWOLF_KILL"
    const val WITCH_ANTIDOTE = "WITCH_ANTIDOTE"
    const val WITCH_POISON = "WITCH_POISON"
    const val SEER_CHECK = "SEER_CHECK"
    const val GUARD_PROTECT = "GUARD_PROTECT"
    const val HUNTER_REVENGE = "HUNTER_REVENGE"
    const val WOLF_KING_REVENGE = "WOLF_KING_REVENGE"
    const val DARK_MERCHANT_TRADE_PREFIX = "DARK_MERCHANT_TRADE_"
    const val DARK_MERCHANT_TRADE_SEER = "DARK_MERCHANT_TRADE_SEER"
    const val DARK_MERCHANT_TRADE_POISON = "DARK_MERCHANT_TRADE_POISON"
    const val DARK_MERCHANT_TRADE_GUN = "DARK_MERCHANT_TRADE_GUN"
    const val MIRACLE_MERCHANT_TRADE_GUARD = "MIRACLE_MERCHANT_TRADE_GUARD"
    const val MIRACLE_MERCHANT = "MIRACLE_MERCHANT"
    const val MERCHANT_SEER_CHECK = "MERCHANT_SEER_CHECK"
    const val MERCHANT_POISON = "MERCHANT_POISON"
    const val MERCHANT_GUN = "MERCHANT_GUN"
    const val MERCHANT_GUARD_PROTECT = "MERCHANT_GUARD_PROTECT"
    const val WOLF_YOUNGER_BROTHER_EXTRA_KILL = "WOLF_YOUNGER_BROTHER_EXTRA_KILL"
    const val DREAM_WEAVER_LINK = "DREAM_WEAVER_LINK"
    const val NIGHTMARE_FEAR = "NIGHTMARE_FEAR"

    // Special death causes (legacy)
    const val DOUBLE_PROTECTION = "DOUBLE_PROTECTION"

    // System Actions
    const val DEATH_RESOLUTION = "DEATH_RESOLUTION"
}
