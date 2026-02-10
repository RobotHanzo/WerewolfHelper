package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.service.ActionUIService
import dev.robothanzo.werewolf.service.NightManager
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ActionUIServiceImpl(
    private val nightManager: NightManager,
    private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry,
    private val roleActionExecutor: dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
) : ActionUIService {
    private val log = LoggerFactory.getLogger(ActionUIServiceImpl::class.java)

    override fun promptPlayerForAction(
        guildId: Long,
        session: Session,
        playerId: Int,
        availableActions: List<RoleAction>,
        timeoutSeconds: Int
    ): RoleActionInstance? {
        if (availableActions.isEmpty()) return null

        return try {
            val player = session.getPlayer(playerId) ?: return null

            // Send action prompt to player's channel
            val actionText = buildString {
                appendLine("🎭 **選擇行動**")
                appendLine()
                appendLine("⏱️ ${timeoutSeconds}秒內必須選擇，否則視為放棄")
            }
            val actionButtons = availableActions.map { action ->
                Button.primary(
                    "selectAction:${action.actionId}",
                    action.actionName
                )
            }.toMutableList()

            if (availableActions.all { it.isOptional }) {
                actionButtons.add(Button.danger("skipAction", "跳過"))
            }

            val message = player.channel?.sendMessage(actionText)?.setComponents(
                MsgUtils.spreadButtonsAcrossActionRows(actionButtons)
            )?.complete()

            // Create/Update action data in unified submittedActions list
            val actorRole = player.roles?.firstOrNull() ?: "未知"

            // Only look for non-finalized actions to reuse
            var actionInstance = session.stateData.submittedActions.find {
                it.actor == playerId && it.status != ActionStatus.SUBMITTED
            }

            if (actionInstance != null) {
                actionInstance.status = ActionStatus.ACTING
                actionInstance.targetPromptId = message?.idLong
            } else {
                val action = session.stateData.submittedActions.find {
                    it.actor == playerId && it.status != ActionStatus.SUBMITTED
                }
                action?.status = ActionStatus.ACTING
            }

            WerewolfApplication.gameSessionService.saveSession(session)

            // Update dashboard status to ACTING
            session.updateActionStatus(
                playerId,
                ActionStatus.ACTING,
                actionId = null,
                targetPlayerIds = emptyList()
            )

            actionInstance
        } catch (e: Exception) {
            log.error("Error prompting player $playerId for action", e)
            null
        }
    }

    override fun promptGroupForAction(
        guildId: Long,
        session: Session,
        actionId: String,
        participants: List<Int>,
        durationSeconds: Int
    ): WolvesActionState? {
        return try {
            val targetPlayers = session.alivePlayers().values
            if (targetPlayers.isEmpty()) return null

            val groupState = WolvesActionState(
                actionId = actionId,
                electorates = participants,
                finished = false
            )

            // Store in session
            session.stateData.wolfStates[actionId] = groupState

            val selectMenu = StringSelectMenu.create("group_target_$actionId")
                .setPlaceholder("選擇擊殺目標")
            targetPlayers.forEach {
                selectMenu.addOption(it.nickname, it.id.toString())
            }
            selectMenu.addOption("跳過", SKIP_TARGET_ID.toString())

            val actionText = buildString {
                appendLine("🐺 **狼人投票**")
                appendLine("請選擇要擊殺的目標：")
                appendLine("⏱️ $durationSeconds 秒內完成投票")
            }

            session.players.values
                .filter { it.id in participants }
                .forEach { player ->
                    player.channel?.sendMessage(actionText)?.setComponents(
                        ActionRow.of(selectMenu.build())
                    )?.queue()
                }

            // Initialize empty votes for all participants so dashboard shows them as "Not Voted"
            participants.forEach { pid ->
                if (groupState.votes.none { it.voterId == pid }) {
                    groupState.votes.add(WolfVote(voterId = pid, targetId = null))
                }
            }

            WerewolfApplication.gameSessionService.saveSession(session)
            log.info("Group action state created: $actionId with ${participants.size} participants")
            groupState
        } catch (e: Exception) {
            log.error("Error creating group action prompt", e)
            null
        }
    }

    override fun updateActionSelection(
        guildId: Long,
        playerId: Int,
        actionId: ActionDefinitionId,
        session: Session
    ): RoleActionInstance? {
        // Redundant with submitAction's lock but safe as a standalone update if called elsewhere
        return WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            // Only update actions that are currently ACTING or PENDING. Do not touch SUBMITTED actions.
            val actionInstance = lockedSession.stateData.submittedActions.find {
                it.actor == playerId && it.status != ActionStatus.SUBMITTED
            } ?: return@withLockedSession null

            actionInstance.actionDefinitionId = actionId
            actionInstance.status = ActionStatus.ACTING

            // Update dashboard status to show selected action
            lockedSession.updateActionStatus(
                playerId,
                ActionStatus.ACTING,
                actionId = actionId,
                targetPlayerIds = emptyList()
            )

            actionInstance
        }
    }

    override fun submitTargetSelection(
        guildId: Long,
        playerId: Int,
        targetPlayerId: Int,
        session: Session
    ): Boolean {
        return WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            // Only submit if we are in ACTING state.
            val actionInstance = lockedSession.stateData.submittedActions.find {
                it.actor == playerId && it.status == ActionStatus.ACTING
            } ?: return@withLockedSession false

            if (actionInstance.actionDefinitionId == null) return@withLockedSession false

            actionInstance.targets.clear()
            actionInstance.targets.add(targetPlayerId)
            actionInstance.status = ActionStatus.SUBMITTED

            // Notify NightManager of activity
            nightManager.notifyPhaseUpdate(guildId)
            true
        }
    }

    override fun getActionData(session: Session, playerId: Int): RoleActionInstance? {
        return session.stateData.submittedActions.find { it.actor == playerId }
    }

    override fun submitGroupVote(
        player: Player,
        groupStateId: String,
        targetPlayerId: Int
    ): Boolean {
        val guildId = player.session?.guildId ?: return false

        return WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val groupState = lockedSession.stateData.wolfStates[groupStateId] ?: return@withLockedSession false

            // Check if phase is still active via session end time
            if (System.currentTimeMillis() > lockedSession.stateData.phaseEndTime) return@withLockedSession false

            val playerId = player.id
            if (playerId !in groupState.electorates) return@withLockedSession false

            groupState.votes.removeIf { it.voterId == playerId }
            groupState.votes.add(WolfVote(voterId = playerId, targetId = targetPlayerId))

            // Explicitly re-put into the map to ensure persistence detection
            lockedSession.stateData.wolfStates[groupStateId] = groupState

            log.info("Vote recorded: Player $playerId -> Target $targetPlayerId in group action $groupStateId")

            nightManager.notifyPhaseUpdate(guildId)
            WerewolfApplication.gameSessionService.broadcastSessionUpdate(lockedSession)
            true
        }
    }

    override fun resolveGroupVote(session: Session, groupState: WolvesActionState): Int? {
        if (groupState.votes.isEmpty()) return null

        val voteCounts = groupState.votes
            .filter { it.targetId != null && it.targetId != SKIP_TARGET_ID }
            .groupingBy { it.targetId!! }
            .eachCount()

        if (voteCounts.isEmpty()) return null

        val maxVotes = voteCounts.maxOf { it.value }
        val topTargets = voteCounts.filterValues { it == maxVotes }.keys.toList()
        return topTargets.random()
    }

    override fun getGroupState(session: Session, actionId: String): WolvesActionState? {
        return session.stateData.wolfStates[actionId]
    }

    override fun cleanupExpiredPrompts(session: Session) {
        val now = System.currentTimeMillis()

        // Check if the current phase has expired
        if (session.stateData.phaseEndTime > 0 && session.stateData.phaseEndTime <= now + 500) {
            val pendingActions =
                session.stateData.submittedActions.filter { it.status == ActionStatus.PENDING || it.status == ActionStatus.ACTING }

            if (pendingActions.isNotEmpty()) {
                pendingActions.forEach { action ->
                    val playerId = action.actor
                    val player = session.getPlayer(playerId)

                    // Re-fetch available actions to determine if mandatory
                    val availableActions = session.getAvailableActionsForPlayer(playerId, roleRegistry)

                    // Handle mandatory actions
                    val mandatoryAction = availableActions.find { !it.isOptional }
                    if (mandatoryAction != null) {
                        val eligible = mandatoryAction.eligibleTargets(
                            session,
                            playerId,
                            session.alivePlayers().values.map { it.id }
                        )
                        val target = eligible.randomOrNull()
                        if (target != null) {
                            session.getPlayer(playerId)?.channel?.sendMessage("⏱️ **時間到！** 已為你隨機選擇目標。")
                                ?.queue()
                            log.info("Auto-submitting mandatory action ${mandatoryAction.actionId} for player $playerId due to timeout")
                            session.validateAndSubmitAction(
                                mandatoryAction.actionId,
                                playerId,
                                arrayListOf(target),
                                "SYSTEM",
                                roleRegistry,
                                roleActionExecutor
                            )
                            return@forEach
                        }
                    }

                    // Default skip behavior for optional actions or if no valid target found
                    session.getPlayer(playerId)?.channel?.sendMessage("⏱️ **時間到！** 你的行動選擇已超時，視為放棄")
                        ?.queue()
                    session.stateData.submittedActions.filter {
                        it.actor == playerId &&
                                it.actionDefinitionId == action.actionDefinitionId &&
                                (it.status == ActionStatus.PENDING || it.status == ActionStatus.ACTING)
                    }.forEach {
                        it.status = ActionStatus.SKIPPED
                        it.targets.clear()
                        it.targets.add(SKIP_TARGET_ID)
                        it.targetPromptId = null
                    }

                    if (player != null) {
                        player.actionSubmitted = true
                        session.updateActionStatus(
                            player.id,
                            ActionStatus.SKIPPED,
                            targetPlayerIds = listOf(SKIP_TARGET_ID)
                        )
                    }
                }

                WerewolfApplication.gameSessionService.saveSession(session)
                log.debug("Cleaned up ${pendingActions.size} expired action prompts (Safe Mode Active)")
            }
        }
    }

    override fun sendReminders(guildId: Long, session: Session) {
        val now = System.currentTimeMillis()
        val expiry = session.stateData.phaseEndTime
        if (expiry == 0L) return

        val timeUntilExpiry = expiry - now
        if (timeUntilExpiry in 29000..31000) {
            val pendingActions =
                session.stateData.submittedActions.filter { it.status == ActionStatus.PENDING || it.status == ActionStatus.ACTING }
            pendingActions.forEach { action ->
                val availableActions = session.getAvailableActionsForPlayer(action.actor, roleRegistry)
                val isWolfBrotherAction =
                    availableActions.any { it.actionId == ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL }

                val msg = if (isWolfBrotherAction) {
                    "⚠️ **提醒**: 還剩 **30秒**！若未發動攻擊，你將會 **自殺**！"
                } else {
                    "⚠️ **提醒**: 還剩 **30秒** 需要選擇行動或跳過，否則將視為放棄"
                }

                session.getPlayer(action.actor)?.channel?.sendMessage(msg)?.queue()
            }
            log.info("Sent 30-second reminders to ${pendingActions.size} players in guild $guildId")
        }
    }

    override fun clearPrompt(session: Session, playerId: Int) {
        val actionInstance =
            session.stateData.submittedActions.find { it.actor == playerId && it.status != ActionStatus.SUBMITTED }
        if (actionInstance != null) {
            actionInstance.targetPromptId = null
            actionInstance.status = ActionStatus.SUBMITTED // Assuming cleared means settled or handled
            WerewolfApplication.gameSessionService.saveSession(session)
        }
    }

    override fun updateTargetPromptId(session: Session, playerId: Int, promptId: Long) {
        val actionInstance =
            session.stateData.submittedActions.find { it.actor == playerId && it.status != ActionStatus.SUBMITTED }
                ?: return
        actionInstance.targetPromptId = promptId
        // Explicitly save the session as this modifies state
        WerewolfApplication.gameSessionService.saveSession(session)
    }
}
