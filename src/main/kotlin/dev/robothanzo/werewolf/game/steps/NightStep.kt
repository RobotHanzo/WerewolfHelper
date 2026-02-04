package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.game.model.SKIP_TARGET_ID
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import dev.robothanzo.werewolf.service.*
import dev.robothanzo.werewolf.utils.parseLong
import kotlinx.coroutines.*
import org.springframework.stereotype.Component

@Component
class NightStep(
    private val gameActionService: GameActionService,
    private val roleActionService: RoleActionService,
    private val discordService: DiscordService,
    private val actionUIService: ActionUIService
) : GameStep {
    override val id = "NIGHT_PHASE"
    override val name = "天黑請閉眼"

    private val nightScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        // Send action prompts to players via Discord UI
        // Start with werewolf group action (they discuss together)
        val werewolves = session.players.values
            .filter { it.isAlive && it.roles?.contains("狼人") == true && it.userId != null }
            .mapNotNull { it.userId }

        nightScope.launch {
            if (werewolves.isNotEmpty()) {
                // Wolves get 90 seconds for group discussion
                actionUIService.promptGroupForAction(
                    session.guildId,
                    session,
                    PredefinedRoles.WEREWOLF_KILL,
                    werewolves,
                    durationSeconds = 90
                )
                session.addLog(LogType.SYSTEM, "狼人進行討論投票，時限90秒")

                waitForWerewolfAction(session, werewolves.size, durationSeconds = 90)
            }

            // Send individual action prompts to other players (60 second timeout)
            for ((playerId, player) in session.players) {
                if (!player.isAlive || player.userId == null) continue

                // Skip werewolves during group voting (they get different UI)
                if (werewolves.contains(player.userId)) {
                    continue
                }

                val uid = player.userId ?: continue
                val availableActions = roleActionService.getAvailableActionsForPlayer(
                    session,
                    uid
                )

                if (availableActions.isNotEmpty()) {
                    actionUIService.promptPlayerForAction(
                        session.guildId,
                        session,
                        uid,
                        playerId,
                        availableActions,
                        timeoutSeconds = 60  // 60 second timeout for non-wolves
                    )
                }
            }

            // Schedule reminder at 30 seconds remaining
            scheduleReminder(session)
        }
    }

    private fun scheduleReminder(session: Session) {
        val thread = Thread {
            try {
                // Wait 30 seconds (60 - 30 = 30 for non-wolves)
                Thread.sleep(30_000)
                actionUIService.sendReminders(session.guildId, session)
            } catch (e: InterruptedException) {
                // Task was cancelled
            }
        }
        thread.isDaemon = true
        thread.name = "NightReminder-${session.guildId}"
        thread.start()
    }

    private suspend fun waitForWerewolfAction(session: Session, totalWolves: Int, durationSeconds: Int) {
        val start = System.currentTimeMillis()
        val timeoutMs = durationSeconds * 1000L

        while (System.currentTimeMillis() - start < timeoutMs) {
            val groupState = actionUIService.getGroupState(PredefinedRoles.WEREWOLF_KILL)
            if (groupState == null) {
                return
            }
            if (groupState.votes.size >= totalWolves) {
                break
            }
            delay(1000)
        }

        val groupState = actionUIService.getGroupState(PredefinedRoles.WEREWOLF_KILL) ?: return
        if (groupState.votes.size < groupState.participants.size) {
            val missingVoters = groupState.participants.filter { it !in groupState.votes.keys }
            missingVoters.forEach { voterId ->
                groupState.votes[voterId] = SKIP_TARGET_ID
            }
        }
        val finalTarget = actionUIService.resolveGroupVote(groupState)
        if (finalTarget != null) {
            roleActionService.submitAction(
                session.guildId,
                PredefinedRoles.WEREWOLF_KILL,
                groupState.participants.firstOrNull() ?: 0L,
                listOf(finalTarget),
                "GROUP"
            )
        }
        actionUIService.clearGroupState(PredefinedRoles.WEREWOLF_KILL)
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Clean up expired prompts and send timeout notifications
        actionUIService.cleanupExpiredPrompts(session.guildId, session)
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
        return 120 // Allow extra time for wolf discussion and UI interaction
    }
}
