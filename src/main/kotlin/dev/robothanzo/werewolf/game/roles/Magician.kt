package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.game.model.BaseRole
import dev.robothanzo.werewolf.game.model.Camp
import dev.robothanzo.werewolf.game.roles.actions.MagicianSwapAction
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import org.springframework.data.annotation.Transient
import org.springframework.stereotype.Component

@Component
class Magician(@Transient private val swapAction: MagicianSwapAction) : BaseRole("魔術師", Camp.GOD) {
    override fun getActions(): List<RoleAction> = listOf(swapAction)
}
