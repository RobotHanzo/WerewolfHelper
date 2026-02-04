package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.*
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Step for handling death trigger actions (Hunter revenge, Wolf King revenge)
 * This step gives players a chance to execute their death-triggered abilities
 * before moving to the speech phase
 */
@Component
class DeathTriggerStep(
    private val roleActionService: RoleActionService,
    private val gameActionService: GameActionService,
    @param:Lazy
    private val gameSessionService: GameSessionService,
    private val discordService: DiscordService
) : GameStep {
    override val id = "DEATH_TRIGGER"
    override val name = "死亡技能觸發"

    override fun onStart(session: Session, service: GameStateService) {
        // Check if any players have death triggers available
        val playersWithTriggers = session.players.values
            .mapNotNull { it.userId }
            .filter { userId -> roleActionService.hasDeathTriggerAvailable(session, userId) }

        if (playersWithTriggers.isEmpty()) {
            // No death triggers available, skip to next phase
            session.addLog(LogType.SYSTEM, "沒有玩家可以觸發死亡技能")
            gameSessionService.saveSession(session)
            // Auto-advance will be handled by timer
            return
        }

        // Notify players with available death triggers
        val guild = discordService.jda.getGuildById(session.guildId)
        for (userId in playersWithTriggers) {
            val player = session.players.values.find { it.userId == userId }
            if (player != null) {
                val roles = player.roles?.filter { it == "獵人" || it == "狼王" } ?: emptyList()
                val roleName = roles.firstOrNull() ?: "未知角色"

                session.addLog(
                    LogType.SYSTEM,
                    "${player.nickname} 的 $roleName 技能已觸發，可以選擇一名玩家帶走",
                    mapOf("userId" to userId, "role" to roleName)
                )

                // Send notification to player channel
                val player = session.players[userId.toString()]
                if (player != null) {
                    player.send("你的 $roleName 已死亡！你可以選擇一名玩家帶走。請在遊戲頻道使用指令選擇目標。")
                }
            }
        }

        gameSessionService.saveSession(session)
        gameSessionService.broadcastSessionUpdate(session)
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Execute all pending death trigger actions
        val killedByTriggers = roleActionService.executeDeathTriggers(session)

        if (killedByTriggers.isNotEmpty()) {
            // Mark triggered deaths
            for (userId in killedByTriggers) {
                gameActionService.markPlayerDead(session, userId, false)
            }

            // Reload session and announce deaths
            val updatedSession = gameSessionService.getSession(session.guildId)
            if (updatedSession.isPresent) {
                val currentSession = updatedSession.get()

                val deathList = killedByTriggers.joinToString("、") { userId ->
                    val player = currentSession.players.values.find { it.userId == userId }
                    player?.nickname ?: "玩家 $userId"
                }

                currentSession.addLog(LogType.SYSTEM, "死亡技能觸發：$deathList 被帶走")
                gameSessionService.saveSession(currentSession)
                gameSessionService.broadcastSessionUpdate(currentSession)
            }
        } else {
            session.addLog(LogType.SYSTEM, "無人被死亡技能帶走")
            gameSessionService.saveSession(session)
        }
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }

    override fun getDurationSeconds(session: Session): Int {
        // Give 30 seconds for death trigger decisions
        return 30
    }
}
