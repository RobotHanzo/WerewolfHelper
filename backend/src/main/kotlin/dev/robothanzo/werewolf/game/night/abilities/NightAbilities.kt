package dev.robothanzo.werewolf.game.night.abilities

import dev.robothanzo.werewolf.game.night.Effect
import dev.robothanzo.werewolf.game.night.Effect.CHARM
import dev.robothanzo.werewolf.game.night.Effect.DEATH
import dev.robothanzo.werewolf.game.night.Effect.FEAR
import dev.robothanzo.werewolf.game.night.Effect.GUARD_PROTECT
import dev.robothanzo.werewolf.game.night.Effect.HUNT
import dev.robothanzo.werewolf.game.night.Effect.INVESTIGATE
import dev.robothanzo.werewolf.game.night.Effect.SWAP
import dev.robothanzo.werewolf.game.night.Effect.TRADE
import dev.robothanzo.werewolf.game.night.Effect.WITCH_POISON
import dev.robothanzo.werewolf.game.night.Effect.WITCH_SAVE
import dev.robothanzo.werewolf.game.night.Effect.WOLF_KILL
import dev.robothanzo.werewolf.game.night.NightAbility
import dev.robothanzo.werewolf.game.roles.RoleIds
import org.springframework.stereotype.Component

/** Data-driven base; concrete night abilities are tiny `@Component` beans declaring their channels. */
abstract class SimpleNightAbility(
    override val id: String,
    override val roleId: String,
    override val reads: Set<Effect>,
    override val writes: Set<Effect>,
    override val targetCount: Int = 1,
    override val optional: Boolean = true,
    override val firstNightOnly: Boolean = false,
    override val mayTargetSelf: Boolean = false,
) : NightAbility

// ---- Wave 0: independent openers ----

/** 魔術師 swaps two seats' numbers — everything targeting them is redirected, so it writes SWAP
 *  and reads nothing (it runs first). */
@Component
class MagicianSwap : SimpleNightAbility(
    "magician.swap", RoleIds.MAGICIAN, reads = emptySet(), writes = setOf(SWAP), targetCount = 2,
)

// ---- Wave 1: depends on SWAP ----

/** 夢魘 fears a seat (voiding its ability) before the wolves open their eyes. */
@Component
class NightmareFear : SimpleNightAbility(
    "nightmare.fear", RoleIds.NIGHTMARE, reads = setOf(SWAP), writes = setOf(FEAR),
)

// ---- Wave 2: depend on SWAP + FEAR, mutually independent ----

/** Wolf-team collective kill. Modelled as one ability owned by the base wolf role; alive while any
 *  wolf-kill participant lives. */
@Component
class WolfKill : SimpleNightAbility(
    "wolf.kill", RoleIds.WOLF, reads = setOf(SWAP, FEAR), writes = setOf(WOLF_KILL),
    mayTargetSelf = true,
)

@Component
class GuardProtect : SimpleNightAbility(
    "guard.protect", RoleIds.GUARD, reads = setOf(SWAP, FEAR), writes = setOf(GUARD_PROTECT),
)

@Component
class SeerInvestigate : SimpleNightAbility(
    "seer.investigate", RoleIds.SEER, reads = setOf(SWAP, FEAR), writes = setOf(INVESTIGATE),
    optional = false,
)

@Component
class PsychicInvestigate : SimpleNightAbility(
    "psychic.investigate", RoleIds.PSYCHIC, reads = setOf(SWAP, FEAR), writes = setOf(INVESTIGATE),
    optional = false,
)

@Component
class GargoyleInvestigate : SimpleNightAbility(
    "gargoyle.investigate", RoleIds.GARGOYLE, reads = setOf(SWAP, FEAR), writes = setOf(INVESTIGATE),
    optional = false,
)

/** 狼美人 charms a seat after the wolves open their eyes; runs alongside the kill (independent of it). */
@Component
class WolfBeautyCharm : SimpleNightAbility(
    "wolf_beauty.charm", RoleIds.WOLF_BEAUTY, reads = setOf(SWAP, FEAR), writes = setOf(CHARM),
)

/** 黑市商人 trades an ability with a seat. */
@Component
class BlackMerchantTrade : SimpleNightAbility(
    "black_merchant.trade", RoleIds.BLACK_MERCHANT, reads = setOf(SWAP, FEAR), writes = setOf(TRADE),
)

/** 獵魔人 hunts a seat; resolves to a death immediately (good → self-death, wolf → target death).
 *  Cannot be used on the first night. */
@Component
class DemonHunterHunt : SimpleNightAbility(
    "demon_hunter.hunt", RoleIds.DEMON_HUNTER, reads = setOf(SWAP, FEAR), writes = setOf(HUNT, DEATH),
)

// ---- Wave 3: depends on WOLF_KILL ----

/** 女巫 sees who the wolves struck, then may save or poison (one potion per night). */
@Component
class WitchPotion : SimpleNightAbility(
    "witch.potion", RoleIds.WITCH,
    reads = setOf(SWAP, FEAR, WOLF_KILL), writes = setOf(WITCH_SAVE, WITCH_POISON),
)

// ---- Passive / first-night ----

/** 守墓人 learns the faction of the previously expelled seat — passive, no live target. */
@Component
class GravekeeperPeek : SimpleNightAbility(
    "gravekeeper.peek", RoleIds.GRAVEKEEPER, reads = emptySet(), writes = emptySet(), targetCount = 0,
)

/** 邱比特 bonds two lovers on the first night. */
@Component
class CupidBond : SimpleNightAbility(
    "cupid.bond", RoleIds.CUPID, reads = emptySet(), writes = setOf(CHARM),
    targetCount = 2, firstNightOnly = true, optional = false,
)
