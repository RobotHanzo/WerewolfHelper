package dev.robothanzo.werewolf.commands

import dev.robothanzo.jda.interactions.annotations.slash.Command
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.game.model.DeathCause
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent

@Command
class Game {
    @Subcommand(description = "狼人自爆")
    private fun detonate(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        val guildId = event.guild?.idLong ?: return

        WerewolfApplication.gameSessionService.withLockedSession(guildId) { session ->
            val player = session.getPlayer(event.user.idLong)
            if (player == null) {
                event.hook.editOriginal(":x: 你不是玩家").queue()
                return@withLockedSession
            }

            if (!player.alive) {
                event.hook.editOriginal(":x: 你已經死了").queue()
                return@withLockedSession
            }

            if (!player.wolf) {
                event.hook.editOriginal(":x: 只有狼人可以自爆").queue()
                return@withLockedSession
            }

            if (player.roles.contains("狼兄")) {
                event.hook.editOriginal(":x: 狼兄不可自爆").queue()
                return@withLockedSession
            }

            // Execute detonation
            session.addLog(LogType.SYSTEM, "${player.nickname} 選擇自爆")
            player.died(DeathCause.EXPEL, allowLastWords = true)

            // End the current phase (usually day speech)
            WerewolfApplication.gameStateService.nextStep(session)

            event.hook.editOriginal(":boom: ${player.nickname} 已自爆，進入黑夜").queue()
        }
    }
}
