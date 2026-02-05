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

    // Action IDs
    const val WEREWOLF_KILL = "WEREWOLF_KILL"
    const val WITCH_ANTIDOTE = "WITCH_ANTIDOTE"
    const val WITCH_POISON = "WITCH_POISON"
    const val SEER_CHECK = "SEER_CHECK"
    const val GUARD_PROTECT = "GUARD_PROTECT"
    const val HUNTER_REVENGE = "HUNTER_REVENGE"
    const val WOLF_KING_REVENGE = "WOLF_KING_REVENGE"

    // Special death causes (legacy)
    const val DOUBLE_PROTECTION = "DOUBLE_PROTECTION"
}
