package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.game.model.BaseRole
import dev.robothanzo.werewolf.game.model.Camp
import dev.robothanzo.werewolf.game.model.RoleEventContext
import dev.robothanzo.werewolf.game.roles.actions.*
import org.springframework.data.annotation.Transient
import org.springframework.stereotype.Component

@Component
class Werewolf(@Transient private val killAction: WerewolfKillAction) : BaseRole("狼人", Camp.WEREWOLF) {
    override fun getActions(): List<RoleAction> = listOf(killAction)
}

@Component
class Seer(@Transient private val checkAction: SeerCheckAction) : BaseRole("預言家", Camp.GOD) {
    override fun getActions(): List<RoleAction> = listOf(checkAction)
}

@Component
class Witch(
    @Transient private val antidoteAction: WitchAntidoteAction,
    @Transient private val poisonAction: WitchPoisonAction
) : BaseRole("女巫", Camp.GOD) {
    override fun getActions(): List<RoleAction> = listOf(antidoteAction, poisonAction)
}

@Component
class Guard(@Transient private val protectAction: GuardProtectAction) : BaseRole("守衛", Camp.GOD) {
    override fun getActions(): List<RoleAction> = listOf(protectAction)
}

@Component
class Hunter(@Transient private val revengeAction: HunterRevengeAction) : BaseRole("獵人", Camp.GOD) {
    override fun getActions(): List<RoleAction> = listOf(revengeAction)

    override fun onDeath(context: RoleEventContext) {
        context.session.stateData.roleFlags["${revengeAction.actionId}Available"] = context.actorPlayerId
    }
}

@Component
class WolfKing(@Transient private val revengeAction: WolfKingRevengeAction) : BaseRole("狼王", Camp.WEREWOLF) {
    override fun getActions(): List<RoleAction> = listOf(revengeAction)

    override fun onDeath(context: RoleEventContext) {
        context.session.stateData.roleFlags["${revengeAction.actionId}Available"] = context.actorPlayerId
    }
}

@Component
class Villager : BaseRole("平民", Camp.VILLAGER)

@Component
class WolfBrother(@Transient private val killAction: WerewolfKillAction) : BaseRole("狼兄", Camp.WEREWOLF) {
    override fun getActions(): List<RoleAction> = listOf(killAction)

    override fun onDeath(context: RoleEventContext) {
        // Record the day the Wolf Brother died
        context.session.stateData.roleFlags["WolfBrotherDiedDay"] = context.session.day
    }
}

@Component
class WolfYoungerBrother(
    @Transient private val killAction: WerewolfKillAction,
    @Transient private val extraKillAction: WolfYoungerBrotherExtraKillAction
) : BaseRole("狼弟", Camp.WEREWOLF) {
    override fun getActions(): List<RoleAction> = listOf(killAction, extraKillAction)
}

@Component
class DarkMerchant(
    @Transient private val tradeSeerAction: DarkMerchantTradeSeerAction,
    @Transient private val tradePoisonAction: DarkMerchantTradePoisonAction,
    @Transient private val tradeGunAction: DarkMerchantTradeGunAction,
    @Transient private val seerAction: MerchantSeerCheckAction,
    @Transient private val poisonAction: MerchantPoisonAction,
    @Transient private val gunAction: MerchantGunAction
) : BaseRole("黑市商人", Camp.GOD) {
    override fun getActions(): List<RoleAction> = listOf(tradeSeerAction, tradePoisonAction, tradeGunAction)

    override fun onDeath(context: RoleEventContext) {
        // Nothing special on death for now
    }
}
