package dev.robothanzo.werewolf.support

import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.GameSettings
import dev.robothanzo.werewolf.domain.IdentityCard
import dev.robothanzo.werewolf.domain.Seat
import dev.robothanzo.werewolf.game.night.NightAbility
import dev.robothanzo.werewolf.game.night.abilities.*
import dev.robothanzo.werewolf.game.roles.Role
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.impl.*
import dev.robothanzo.werewolf.i18n.I18nConfig
import dev.robothanzo.werewolf.i18n.Msg

/**
 * Shared fixtures for pure-logic unit tests — a real [RoleRegistry] over the canonical role beans
 * and the actual zh-TW message bundle, plus tiny builders for sessions and seats.
 */
object TestFixtures {

    fun msg(): Msg = Msg(I18nConfig().messageSource())

    fun allRoles(): List<Role> = listOf(
        // wolves
        WolfRole(), WolfKingRole(), WolfBeautyRole(), WhiteWolfKingRole(),
        WolfBrotherRole(), WolfYoungerRole(), MechanicWolfRole(), NightmareRole(),
        GargoyleRole(), BloodMoonRole(), EvilKnightRole(), HiddenWolfRole(),
        // gods
        SeerRole(), WitchRole(), HunterRole(), GuardRole(), KnightRole(), IdiotRole(),
        GravekeeperRole(), MagicianRole(), BlackMerchantRole(), PsychicRole(),
        DemonHunterRole(), CupidRole(), ThiefRole(), HybridRole(), CloneRole(),
        // villager
        VillagerRole(),
    )

    fun registry(): RoleRegistry = RoleRegistry(allRoles(), msg())

    /** Every canonical night-ability bean — for orchestrator/planner tests that need the full set. */
    fun allAbilities(): List<NightAbility> = listOf(
        MagicianSwap(), NightmareFear(), WolfKill(), GuardProtect(), SeerInvestigate(),
        PsychicInvestigate(), GargoyleInvestigate(), WolfBeautyCharm(), BlackMerchantTrade(),
        MechanicWolfLearn(), DemonHunterHunt(), WitchPotion(), GravekeeperPeek(), CupidBond(),
    )

    fun seat(number: Int, vararg cards: Pair<String, Boolean>, configure: Seat.() -> Unit = {}): Seat =
        Seat(
            number = number,
            memberId = number.toLong(),
            cards = cards.map { IdentityCard(it.first, it.second) }.toMutableList(),
        ).apply(configure)

    fun session(
        doubleIdentity: Boolean = false,
        playerCount: Int = 12,
        seats: List<Seat> = emptyList(),
        configure: GameSession.() -> Unit = {},
    ): GameSession = GameSession(
        guildId = 1L,
        settings = GameSettings(playerCount = playerCount, doubleIdentity = doubleIdentity),
        seats = seats.toMutableList(),
    ).apply { if (seats.isNotEmpty()) assigned = true }.apply(configure)
}
