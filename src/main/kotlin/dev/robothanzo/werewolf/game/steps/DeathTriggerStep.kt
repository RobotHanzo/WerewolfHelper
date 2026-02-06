package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.RoleActionService
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
    private val gameSessionService: GameSessionService
) : GameStep {
    override val id = "DEATH_TRIGGER"
    override val name = "死亡技能觸發"

    override fun onStart(session: Session, service: GameStateService) {
        val guildId = session.guildId

        gameSessionService.withLockedSession(guildId) { lockedSession ->
            // Check if any players have death triggers available
            val playersWithTriggers = lockedSession.players.values
                .filter { roleActionService.hasDeathTriggerAvailable(lockedSession, it.id) }

            if (playersWithTriggers.isEmpty()) {
                // No death triggers available
                lockedSession.addLog(LogType.SYSTEM, "沒有玩家可以觸發死亡技能")
                return@withLockedSession
            }

            // Notify players with available death triggers
            for (player in playersWithTriggers) {
                val roles = player.roles?.filter { it == "獵人" || it == "狼王" } ?: emptyList()
                val roleName = roles.firstOrNull() ?: "未知角色"

                lockedSession.addLog(
                    LogType.SYSTEM,
                    "${player.nickname} 的 $roleName 技能已觸發，可以選擇一名玩家帶走",
                    mapOf("playerId" to player.id, "role" to roleName)
                )

                // Send notification to player channel
                player.channel?.sendMessage("你的 $roleName 已死亡！你可以選擇一名玩家帶走。請在遊戲頻道使用指令選擇目標。")
                    ?.queue()
            }

            gameSessionService.broadcastSessionUpdate(lockedSession)
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {
        val guildId = session.guildId

        gameSessionService.withLockedSession(guildId) { lockedSession ->
            // Execute all pending death trigger actions
            val killedByTriggers = roleActionService.executeDeathTriggers(lockedSession)

            if (killedByTriggers.isNotEmpty()) {
                // Mark triggered deaths
                for (userId in killedByTriggers) {
                    gameActionService.markPlayerDead(lockedSession, userId, false)
                }

                val deathList = killedByTriggers.joinToString("、") { userId ->
                    val player = lockedSession.getPlayer(userId)
                    player?.nickname ?: "玩家 $userId"
                }

                lockedSession.addLog(LogType.SYSTEM, "死亡技能觸發：$deathList 被帶走")
                gameSessionService.broadcastSessionUpdate(lockedSession)
            } else {
                lockedSession.addLog(LogType.SYSTEM, "無人被死亡技能帶走")
            }
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
