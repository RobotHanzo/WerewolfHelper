package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.PoliceService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class SheriffElectionStep(
    @param:Lazy
    private val policeService: PoliceService,
    private val discordService: DiscordService
) : GameStep {
    override val id = "SHERIFF_ELECTION"
    override val name = "警長參選"

    override fun onStart(session: Session, service: GameStateService) {
        val guild = discordService.getGuild(session.guildId) ?: return
        val channel = session.courtTextChannel ?: return
        policeService.startEnrollment(session, channel, null) {
            service.nextStep(session)
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {
        if (policeService.sessions.containsKey(session.guildId)) {
            policeService.interrupt(session.guildId)
        }
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val action = input["action"] as? String ?: return mapOf(
            "success" to false,
            "message" to "Missing action"
        )

        return when (action) {
            "force_start_voting" -> {
                policeService.forceStartVoting(session.guildId)
                mapOf("success" to true)
            }

            "interrupt" -> {
                policeService.interrupt(session.guildId)
                mapOf("success" to true)
            }

            "start" -> {
                val guild = discordService.getGuild(session.guildId) ?: return mapOf(
                    "success" to false,
                    "message" to "Guild not found"
                )
                val channel = session.courtTextChannel ?: return mapOf(
                    "success" to false,
                    "message" to "Channel not found"
                )
                policeService.startEnrollment(session, channel, null)
                mapOf("success" to true)
            }

            else -> mapOf("success" to false, "message" to "Unknown action")
        }
    }
}
