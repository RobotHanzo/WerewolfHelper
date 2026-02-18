package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.game.model.*
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
        val deathCause = context.metadata["deathCause"] as? DeathCause
        revengeAction.onDeath(context.session, context.actorPlayerId, deathCause ?: DeathCause.UNKNOWN)
    }
}

@Component
class WolfKing(@Transient private val revengeAction: WolfKingRevengeAction) : BaseRole("狼王", Camp.WEREWOLF) {
    override fun getActions(): List<RoleAction> = listOf(revengeAction)

    override fun onDeath(context: RoleEventContext) {
        val deathCause = context.metadata["deathCause"] as? DeathCause
        revengeAction.onDeath(context.session, context.actorPlayerId, deathCause ?: DeathCause.UNKNOWN)
    }
}
@Component
class DreamWeaver(
    @Transient private val linkAction: DreamWeaverLinkAction
) : BaseRole("攝夢人", Camp.GOD) {
    override fun getActions(): List<RoleAction> = listOf(linkAction)

    override fun onDeath(context: RoleEventContext) {
        // Linked death logic will be handled in DeathResolutionAction mainly,
        // but we might need to trigger immediate death if night is over?
        // For simplicity, Dream Weaver death processing is usually done at night end or day transition.
        // Rule: "If Dream Weaver dies at night, the Sleepwalker dies too."
        // This is best handled in DeathResolutionAction to avoid circular dependencies or complex event triggers during death.
    }
}

@Component
class Nightmare(
    @Transient private val fearAction: NightmareFearAction,
    @Transient private val killAction: WerewolfKillAction
) : BaseRole("夢魘", Camp.WEREWOLF) {
    override fun getActions(): List<RoleAction> = listOf(fearAction, killAction)
}


@Component
class GhostRider(@Transient private val killAction: WerewolfKillAction) : BaseRole("惡靈騎士", Camp.WEREWOLF) {
    override fun getActions(): List<RoleAction> = listOf(killAction)
}

@Component
class Villager : BaseRole("平民", Camp.VILLAGER)

@Component
class WolfBrother(@Transient private val killAction: WerewolfKillAction) : BaseRole("狼兄", Camp.WEREWOLF) {
    override fun getActions(): List<RoleAction> = listOf(killAction)

    override fun onDeath(context: RoleEventContext) {
        // Record the day the Wolf Brother died by adding a DEATH action to executedActions
        val deathAction = RoleActionInstance(
            actor = context.actorPlayerId,
            actorRole = "狼兄",
            actionDefinitionId = ActionDefinitionId.DEATH,
            targets = arrayListOf(),
            submittedBy = ActionSubmissionSource.SYSTEM,
            status = ActionStatus.PROCESSED
        )
        context.session.stateData.executedActions.getOrPut(context.session.day) { mutableListOf() }
            .add(deathAction)

        // Set the awakened flag for the Younger Brother
        val yb = context.session.players.values.find { it.roles.contains("狼弟") }
        if (yb != null) {
            context.session.stateData.wolfBrotherAwakenedPlayerId = yb.id
        }
    }

    override fun onRevived(context: RoleEventContext) {
        // Reset the awakened flag when the Wolf Brother revives
        context.session.stateData.wolfBrotherAwakenedPlayerId = null
    }
}

@Component
class WolfYoungerBrother(
    @Transient private val killAction: WerewolfKillAction,
    @Transient private val extraKillAction: WolfYoungerBrotherExtraKillAction
) : BaseRole("狼弟", Camp.WEREWOLF) {
    override fun getActions(): List<RoleAction> = listOf(killAction, extraKillAction)
}

abstract class BaseMerchant(
    name: String,
    camp: Camp
) : BaseRole(name, camp) {
    override fun onDeath(context: RoleEventContext) {
        // Nothing special on death for now
    }
}

@Component
class DarkMerchant(
    @Transient private val tradeSeerAction: DarkMerchantTradeSeerAction,
    @Transient private val tradePoisonAction: DarkMerchantTradePoisonAction,
    @Transient private val tradeGunAction: DarkMerchantTradeGunAction
) : BaseMerchant("黑市商人", Camp.GOD) {
    override fun getActions(): List<RoleAction> = listOf(tradeSeerAction, tradePoisonAction, tradeGunAction)
}

@Component
class MiracleMerchant(
    @Transient private val tradeSeerAction: DarkMerchantTradeSeerAction,
    @Transient private val tradePoisonAction: DarkMerchantTradePoisonAction,
    @Transient private val tradeGuardAction: MiracleMerchantTradeGuardAction
) : BaseMerchant("奇蹟商人", Camp.GOD) {
    override fun getActions(): List<RoleAction> = listOf(tradeSeerAction, tradePoisonAction, tradeGuardAction)
}


