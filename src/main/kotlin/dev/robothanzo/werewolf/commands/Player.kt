package dev.robothanzo.werewolf.commands

import dev.robothanzo.jda.interactions.annotations.Button
import dev.robothanzo.jda.interactions.annotations.slash.Command
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.utils.CmdUtils
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import org.slf4j.LoggerFactory

@Command
class Player {
    companion object {
        private val log = LoggerFactory.getLogger(Player::class.java)

        // Delegated to PoliceService
        fun selectNewPolice(event: EntitySelectInteractionEvent) {
            WerewolfApplication.policeService.selectNewPolice(event)
        }

        // Delegated to PoliceService
        fun confirmNewPolice(event: ButtonInteractionEvent) {
            WerewolfApplication.policeService.confirmNewPolice(event)
        }

        // Delegated to PoliceService
        fun destroyPolice(event: ButtonInteractionEvent) {
            WerewolfApplication.policeService.destroyPolice(event)
        }
    }

    @Button
    fun changeRoleOrder(event: ButtonInteractionEvent) {
        if (event.guild == null) return
        event.deferReply().queue()
        val session = CmdUtils.getSession(event) ?: return
        val player = session.getPlayer(event.user.idLong)
        if (player == null) {
            event.hook.editOriginal(":x: 你不是玩家").queue()
            return
        }
        try {
            WerewolfApplication.playerService.switchRoleOrder(player)
            event.hook.editOriginal(":white_check_mark: 交換成功").queue()
        } catch (e: Exception) {
            event.hook.editOriginal(":x: " + e.message).queue()
        }
    }
}
