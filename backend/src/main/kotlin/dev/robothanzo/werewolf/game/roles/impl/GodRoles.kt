package dev.robothanzo.werewolf.game.roles.impl

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.game.roles.BaseRole
import dev.robothanzo.werewolf.game.roles.RoleIds
import dev.robothanzo.werewolf.game.roles.RoleTag
import org.springframework.stereotype.Component

/** God-faction identities (every non-wolf, non-villager identity per FEATURES §2). */

@Component
class SeerRole : BaseRole(RoleIds.SEER, "role.seer", Faction.GOD)

@Component
class WitchRole : BaseRole(RoleIds.WITCH, "role.witch", Faction.GOD)

@Component
class HunterRole : BaseRole(
    RoleIds.HUNTER, "role.hunter", Faction.GOD,
    setOf(RoleTag.DEATH_REVENGE),
)

@Component
class GuardRole : BaseRole(RoleIds.GUARD, "role.guard", Faction.GOD)

@Component
class KnightRole : BaseRole(RoleIds.KNIGHT, "role.knight", Faction.GOD)

@Component
class IdiotRole : BaseRole(RoleIds.IDIOT, "role.idiot", Faction.GOD)

@Component
class GravekeeperRole : BaseRole(RoleIds.GRAVEKEEPER, "role.gravekeeper", Faction.GOD)

@Component
class MagicianRole : BaseRole(RoleIds.MAGICIAN, "role.magician", Faction.GOD)

@Component
class BlackMerchantRole : BaseRole(RoleIds.BLACK_MERCHANT, "role.black_merchant", Faction.GOD)

@Component
class PsychicRole : BaseRole(RoleIds.PSYCHIC, "role.psychic", Faction.GOD)

@Component
class DemonHunterRole : BaseRole(RoleIds.DEMON_HUNTER, "role.demon_hunter", Faction.GOD)

/** 邱比特 — first night only: bonds two players as lovers. */
@Component
class CupidRole : BaseRole(
    RoleIds.CUPID, "role.cupid", Faction.GOD,
    setOf(RoleTag.FIRST_NIGHT_ONLY),
)

/** 盜賊 — first night only: picks one of the two leftover cards. */
@Component
class ThiefRole : BaseRole(
    RoleIds.THIEF, "role.thief", Faction.GOD,
    setOf(RoleTag.FIRST_NIGHT_ONLY),
)

/** 混血兒 — first night only: copies a role model's win condition. */
@Component
class HybridRole : BaseRole(
    RoleIds.HYBRID, "role.hybrid", Faction.GOD,
    setOf(RoleTag.FIRST_NIGHT_ONLY),
)

/** 複製人 — clone. A living clone counts toward gods for win-condition purposes. */
@Component
class CloneRole : BaseRole(
    RoleIds.CLONE, "role.clone", Faction.GOD,
    setOf(RoleTag.COUNTS_AS_GOD),
)
