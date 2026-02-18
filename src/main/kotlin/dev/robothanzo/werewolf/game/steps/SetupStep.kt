package dev.robothanzo.werewolf.game.steps

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
    private val gameStateService: GameStateService,
    private val expelService: ExpelService
) : GameStep() {
    override val id = "SETUP"
    override val name = "遊戲設置"

    override fun onStart(session: Session, service: GameStateService) {
        super.onStart(session, service)
        speechService.interruptSession(session.guildId)
        policeService.interrupt(session.guildId)
        expelService.removePoll(session.guildId)
        speechService.setAllMute(session.guildId, false)
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
                    session,
                    { msg: String -> gameActionService.broadcastProgress(session.guildId, msg, null) },
                    { pct: Int -> gameActionService.broadcastProgress(session.guildId, "", pct) }
                )
                mapOf("success" to true)
            }

            "start_game" -> {
                // Initialize day to 0 for the first night (Morning 1 will increment to 1)
                session.day = 0
                gameStateService.startStep(session, "NIGHT_STEP")
                mapOf("success" to true)
            }

            else -> mapOf("success" to false, "message" to "Unknown action")
        }
    }

}
