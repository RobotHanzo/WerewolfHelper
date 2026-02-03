package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.RoleActionService
import dev.robothanzo.werewolf.utils.parseLong
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class SpeechStep(
    @param:Lazy private val speechService: dev.robothanzo.werewolf.service.SpeechService,
    private val roleActionService: RoleActionService
) : GameStep {
    override val id = "SPEECH_PHASE"
    override val name = "發言流程"

    override fun onStart(session: Session, service: GameStateService) {
        // Automatically start speech flow when entering this step
        speechService.startAutoSpeechFlow(session.guildId, session.courtTextChannelId)
    }

    override fun onEnd(session: Session, service: GameStateService) {
        speechService.interruptSession(session.guildId)
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val action = input["action"] as? String

        return when (action) {
            "submit_action" -> {
                // Handle daytime action submission (e.g., Hunter revenge during speech)
                val actionDefinitionId = input["actionDefinitionId"] as? String
                val actorUserId = parseLong(input["actorUserId"])

                @Suppress("UNCHECKED_CAST")
                val targetUserIds = (input["targetUserIds"] as? List<*>)?.mapNotNull { parseLong(it) } ?: emptyList()
                val submittedBy = input["submittedBy"] as? String ?: "PLAYER"

                if (actionDefinitionId == null || actorUserId == null) {
                    return mapOf("success" to false, "error" to "Missing required parameters")
                }

                roleActionService.submitAction(
                    session.guildId,
                    actionDefinitionId,
                    actorUserId,
                    targetUserIds,
                    submittedBy
                )
            }

            else -> mapOf("success" to true)
        }
    }
}
