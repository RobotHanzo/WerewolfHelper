package dev.robothanzo.werewolf.game.roles.impl

import dev.robothanzo.werewolf.domain.Faction
import dev.robothanzo.werewolf.game.roles.BaseRole
import dev.robothanzo.werewolf.game.roles.RoleIds
import dev.robothanzo.werewolf.game.roles.RoleTag
import org.springframework.stereotype.Component

/**
 * Wolf-faction identities. Each is a single `@Component` bean — the only thing needed to add a
 * future role. Win-condition faction is explicit registry data (and matches the FEATURES §2
 * name rule used as fallback).
 */

@Component
class WolfRole : BaseRole(
    RoleIds.WOLF, "role.wolf", Faction.WOLF,
    setOf(RoleTag.WOLF_CHAT, RoleTag.MAY_SELF_TARGET),
)

@Component
class WolfKingRole : BaseRole(
    RoleIds.WOLF_KING, "role.wolf_king", Faction.WOLF,
    setOf(RoleTag.WOLF_CHAT, RoleTag.DEATH_REVENGE),
)

@Component
class WolfBeautyRole : BaseRole(
    RoleIds.WOLF_BEAUTY, "role.wolf_beauty", Faction.WOLF,
    setOf(RoleTag.WOLF_CHAT),
)

@Component
class WhiteWolfKingRole : BaseRole(
    RoleIds.WHITE_WOLF_KING, "role.white_wolf_king", Faction.WOLF,
    setOf(RoleTag.WOLF_CHAT, RoleTag.DEATH_REVENGE, RoleTag.REVENGE_ON_SELF_DESTRUCT_ONLY),
)

@Component
class WolfBrotherRole : BaseRole(
    RoleIds.WOLF_BROTHER, "role.wolf_brother", Faction.WOLF,
    setOf(RoleTag.WOLF_CHAT),
)

@Component
class WolfYoungerRole : BaseRole(
    RoleIds.WOLF_YOUNGER, "role.wolf_younger", Faction.WOLF,
    setOf(RoleTag.WOLF_CHAT),
)

/** 機械狼 — 互不相認 (no wolf chat); learns a role, and inherits the knife once the wolves are gone. */
@Component
class MechanicWolfRole : BaseRole(
    RoleIds.MECHANIC_WOLF, "role.mechanic_wolf", Faction.WOLF,
    setOf(RoleTag.INHERITS_KILL),
)

/** 隱狼 — 狼人陣營的平民: no wolf chat, reads as 好人, inherits the knife / dies with the team. */
@Component
class HiddenWolfRole : BaseRole(
    RoleIds.HIDDEN_WOLF, "role.hidden_wolf", Faction.WOLF,
    setOf(RoleTag.INVESTIGATED_AS_GOOD, RoleTag.INHERITS_KILL),
)

/** 夢魘 — nightmare. On the wolf chat team and acts at night, but per FEATURES §2's name rule
 *  its win-condition faction is GOD (no 狼 in the name, not an exception). */
@Component
class NightmareRole : BaseRole(
    RoleIds.NIGHTMARE, "role.nightmare", Faction.GOD,
    setOf(RoleTag.WOLF_CHAT),
)

/** 石像鬼 — gargoyle. Wolf faction by exception, no wolf chat; inherits the knife once wolves are gone. */
@Component
class GargoyleRole : BaseRole(
    RoleIds.GARGOYLE, "role.gargoyle", Faction.WOLF,
    setOf(RoleTag.INHERITS_KILL),
)

@Component
class BloodMoonRole : BaseRole(
    RoleIds.BLOOD_MOON, "role.blood_moon", Faction.WOLF,
)

@Component
class EvilKnightRole : BaseRole(
    RoleIds.EVIL_KNIGHT, "role.evil_knight", Faction.WOLF,
)
