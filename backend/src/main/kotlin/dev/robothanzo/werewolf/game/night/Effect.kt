package dev.robothanzo.werewolf.game.night

/**
 * The night "channels". Abilities declare which channels they **read** and **write** instead of a
 * linear priority number; the [NightPlanner] derives execution order from those declarations.
 *
 * If ability B reads a channel that ability A writes, B depends on A and must run in a later wave;
 * abilities that neither read nor write each other's channels are independent and run in the same
 * wave, simultaneously.
 */
enum class Effect {
    /** 魔術師 swaps two seats' numbers; abilities targeting them are redirected. */
    SWAP,

    /** 夢魘 fears a seat, voiding its ability this night. */
    FEAR,

    /** 狼美人 / 攝夢人 charm — links the charmed seat's fate to the charmer. */
    CHARM,

    /** Wolf-team night kill. */
    WOLF_KILL,

    /** 守衛 protection against the wolf knife. */
    GUARD_PROTECT,

    /** 女巫 antidote. */
    WITCH_SAVE,

    /** 女巫 poison. */
    WITCH_POISON,

    /** 預言家 / 通靈師 / 石像鬼 investigation. */
    INVESTIGATE,

    /** 獵魔人 hunt. */
    HUNT,

    /** 黑市商人 trade. */
    TRADE,

    /** Terminal channel: a death has been declared (resolution output). */
    DEATH,
}
