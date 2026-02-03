package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.RoleActionService
import dev.robothanzo.werewolf.utils.parseLong
import org.springframework.stereotype.Component

@Component
class NightStep(
    private val gameActionService: GameActionService,
    private val roleActionService: RoleActionService,
    private val discordService: DiscordService
) : GameStep {
    override val id = "NIGHT_PHASE"
    override val name = "天黑請閉眼"

    override fun onStart(session: Session, service: GameStateService) {
        // Mute everyone
        gameActionService.muteAll(session.guildId, true)

        // Clear pending actions from previous night
        session.stateData["pendingActions"] = mutableListOf<Map<String, Any>>()

        // Reset actionSubmitted flag for all players
        for ((_, player) in session.players) {
            player.actionSubmitted = false
        }

        // Log night start
        session.addLog(LogType.SYSTEM, "夜晚開始，各職業請準備行動")

        // Send Discord DMs to players with available actions for this night
        val jda = discordService.jda
        for ((_, player) in session.players) {
            if (!player.isAlive || player.userId == null) continue

            try {
                val user = jda.getUserById(player.userId!!)
                if (user != null) {
                    val availableActions = roleActionService.getAvailableActionsForPlayer(
                        session,
                        player.userId!!
                    )

                    if (availableActions.isNotEmpty()) {
                        val messageBuilder = StringBuilder()
                        messageBuilder.append("🌙 **夜晚行動提醒** 🌙\n\n")
                        messageBuilder.append("您在夜晚有以下可用的行動：\n")

                        for (action in availableActions) {
                            messageBuilder.append("• **${action.actionId}** (角色: ${action.roleName})\n")
                            messageBuilder.append("  - 優先級: ${action.priority}\n")
                            messageBuilder.append("  - 目標數: ${action.targetCount}\n")
                        }

                        messageBuilder.append("\n請在儀表板中選擇您的行動。")

                        user.openPrivateChannel().queue { channel ->
                            channel.sendMessage(messageBuilder.toString()).queue()
                        }
                    }
                }
            } catch (e: Exception) {
                // Log but don't fail - Discord DM is non-critical
                println("Failed to send DM to player ${player.userId}: ${e.message}")
            }
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Prepare for sheriff election/death announcement
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val action = input["action"] as? String

        return when (action) {
            "submit_action" -> {
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

    override fun getDurationSeconds(session: Session): Int {
        return 30
    }
}
