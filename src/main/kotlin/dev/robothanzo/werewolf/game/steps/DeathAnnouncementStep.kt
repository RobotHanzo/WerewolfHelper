package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.game.model.resolveNightActions
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import kotlinx.coroutines.launch
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class DeathAnnouncementStep(
    private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry,
    private val gameActionService: GameActionService,
    private val roleActionExecutor: dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor,
    @param:Lazy
    private val gameSessionService: GameSessionService
) : GameStep {
    override val id = "DEATH_ANNOUNCEMENT"
    override val name = "宣布死訊"

    private val scope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun onStart(session: Session, service: GameStateService) {
        val guildId = session.guildId

        gameSessionService.withLockedSession(guildId) { lockedSession ->
            // Resolve all pending night actions and get deaths
            val resolutionResult = lockedSession.resolveNightActions(roleActionExecutor, roleRegistry)

            // Process deaths - mark players as dead based on resolution result
            val allDeaths = mutableSetOf<Int>()

            for ((deathCause, deaths) in resolutionResult.deaths) {
                for (userId in deaths) {
                    allDeaths.add(userId)
                }
            }

            // Mark players as dead
            for (userId in allDeaths) {
                // For day 1, allow last words so player can speak during death announcement
                gameActionService.markPlayerDead(lockedSession, userId, lockedSession.day == 1)
            }

            // Store tonight's deaths in stateData for frontend display
            lockedSession.stateData.deadPlayers = allDeaths.toList()

            // Create public death announcement (without revealing causes)
            if (allDeaths.isNotEmpty()) {
                val deathList = allDeaths.joinToString("、") { userId ->
                    val player = lockedSession.getPlayer(userId)
                    player?.nickname ?: "玩家 $userId"
                }

                lockedSession.addLog(LogType.SYSTEM, "昨晚 $deathList 死亡")
            } else {
                lockedSession.courtTextChannel?.sendMessage("**:angel: 昨晚是平安夜**")?.queue()
            }

            // Announce good morning on day 2+
            if (lockedSession.day > 1) {
                lockedSession.addLog(LogType.SYSTEM, "早上好，各位玩家")
            }

            gameSessionService.broadcastSessionUpdate(lockedSession)
        }

        // Auto advance after duration
        scope.launch {
            val initialState = id // "DEATH_ANNOUNCEMENT"
            kotlinx.coroutines.delay(getDurationSeconds(session) * 1000L)
            gameSessionService.withLockedSession(guildId) { currentSession ->
                if (currentSession.currentState == initialState) {
                    service.nextStep(currentSession)
                }
            }
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Handle last words if applicable
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }

    override fun getDurationSeconds(session: Session): Int {
        return 10
    }
}
