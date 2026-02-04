package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.*
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class DeathAnnouncementStep(
    private val gameActionService: GameActionService,
    private val roleActionService: RoleActionService,
    private val discordService: DiscordService,
    @param:Lazy
    private val gameSessionService: GameSessionService
) : GameStep {
    override val id = "DEATH_ANNOUNCEMENT"
    override val name = "宣布死訊"

    override fun onStart(session: Session, service: GameStateService) {
        // Reload latest session to capture pending actions submitted during night
        val sessionForResolution = gameSessionService.getSession(session.guildId).orElse(session)
        roleActionService.getPendingActions(sessionForResolution)

        // Resolve all pending night actions and get deaths
        val resolutionResult = roleActionService.resolveNightActions(sessionForResolution)

        // Process deaths - mark players as dead based on resolution result
        val allDeaths = mutableSetOf<Long>()

        for ((deathCause, deaths) in resolutionResult.deaths) {
            for (userId in deaths) {
                allDeaths.add(userId)

                // Log the specific death cause (for admin purposes only - NOT sent to Discord channels)
                sessionForResolution.addLog(
                    LogType.PLAYER_DIED,
                    "玩家 $userId ${deathCause.logMessage}",
                    mapOf(
                        "userId" to userId,
                        "deathCause" to deathCause.name
                    )
                )
            }
        }

        // Mark players as dead - this will handle role assignment, police transfer, last words, etc.
        for (userId in allDeaths) {
            // For day 1, allow last words so player can speak during death announcement
            gameActionService.markPlayerDead(session, userId, session.day == 1)
        }

        // Reload session from database to get updated state from markPlayerDead calls
        val updatedSession = gameSessionService.getSession(session.guildId)
        if (updatedSession.isPresent) {
            val currentSession = updatedSession.get()

            // Create public death announcement (without revealing causes)
            if (allDeaths.isNotEmpty()) {
                val deathList = allDeaths.joinToString("、") { userId ->
                    val player = currentSession.players.values.find { it.userId == userId }
                    player?.nickname ?: "玩家 $userId"
                }

                currentSession.addLog(LogType.SYSTEM, "昨晚，$deathList 死亡")
            } else {
                currentSession.sendToCourt("**:angel: 昨晚是平安夜**")
            }

            // Announce good morning on day 2+
            if (currentSession.day > 1) {
                currentSession.addLog(LogType.SYSTEM, "早上好，各位玩家")
            }

            // Save and broadcast the updated session
            gameSessionService.saveSession(currentSession)
            gameSessionService.broadcastSessionUpdate(currentSession)
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
