package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.commands.Poll
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.*
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class SetupStep(
    private val speechService: SpeechService,
    @param:Lazy
    private val policeService: PoliceService,
    @param:Lazy
    private val roleService: RoleService,
    private val gameActionService: GameActionService,
    @param:Lazy
    private val gameStateService: GameStateService
) : GameStep {
    override val id = "SETUP"
    override val name = "遊戲設置"

    override fun onStart(session: Session, service: GameStateService) {
        speechService.interruptSession(session.guildId)
        policeService.interrupt(session.guildId)
        Poll.expelCandidates.remove(session.guildId)
        gameActionService.muteAll(session.guildId, false)
        session.addLog(LogType.SYSTEM, "進入遊戲設置")
    }

    override fun onEnd(session: Session, service: GameStateService) {
        if (!session.hasAssignedRoles) {
            session.addLog(LogType.SYSTEM, "提醒：尚未分配身分")
        }
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val action = input["action"] as? String ?: return mapOf(
            "success" to false,
            "message" to "Missing action"
        )

        return when (action) {
            "assign_roles" -> {
                roleService.assignRoles(
                    session.guildId,
                    { msg: String -> gameActionService.broadcastProgress(session.guildId, msg, null) },
                    { pct: Int -> gameActionService.broadcastProgress(session.guildId, "", pct) }
                )
                mapOf("success" to true)
            }

            "start_game" -> {
                gameStateService.startStep(session, "NIGHT_PHASE")
                mapOf("success" to true)
            }

            else -> mapOf("success" to false, "message" to "Unknown action")
        }
    }

    override fun getDurationSeconds(session: Session): Int {
        return -1 // Manual start
    }
}
