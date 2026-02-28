package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.game.model.BaseRole
import dev.robothanzo.werewolf.game.model.Camp
import dev.robothanzo.werewolf.game.roles.actions.*
import org.springframework.data.annotation.Transient
import org.springframework.stereotype.Component

@Component
class WolfMechanic(
    @Transient private val learnAction: WolfMechanicLearnAction,
    @Transient private val seerCheckAction: WolfMechanicSeerCheckAction,
    @Transient private val poisonAction: WolfMechanicPoisonAction,
    @Transient private val protectAction: WolfMechanicGuardProtectAction,
    @Transient private val hunterRevengeAction: WolfMechanicHunterRevengeAction,
    @Transient private val extraKillAction: WolfMechanicExtraKillAction
) : BaseRole("機械狼", Camp.WEREWOLF) {
    override fun getActions(): List<RoleAction> = listOf(
        learnAction,
        seerCheckAction,
        poisonAction,
        protectAction,
        hunterRevengeAction,
        extraKillAction
    )
}
