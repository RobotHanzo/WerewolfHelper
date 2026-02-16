package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.service.ActionUIService
import dev.robothanzo.werewolf.service.NightManager
import dev.robothanzo.werewolf.service.RoleEventService
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ActionUIServiceImpl(
    private val nightManager: NightManager,
    private val roleRegistry: RoleRegistry,
    private val roleActionExecutor: dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor,
    private val roleEventService: RoleEventService
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
                appendLine(player.member?.asMention)
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

            // Only look for non-finalized actions to reuse
            var actionInstance = session.stateData.submittedActions.find {
                it.actor == playerId && !it.status.executed
            }

            if (actionInstance == null) {
                actionInstance = RoleActionInstance(
                    actor = playerId,
                    actorRole = player.roles.firstOrNull() ?: "Unknown",
                    actionDefinitionId = null,
                    targets = mutableListOf(),
                    submittedBy = ActionSubmissionSource.PLAYER,
                    status = ActionStatus.PENDING,
                    actionPromptId = message?.idLong
                )
                session.stateData.submittedActions.add(actionInstance)
            } else {
                actionInstance.status = ActionStatus.PENDING
                actionInstance.actionPromptId = message?.idLong
                actionInstance.targets.clear()
            }

            WerewolfApplication.gameSessionService.saveSession(session)
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

            val actionDefId = ActionDefinitionId.fromString(actionId)
            val actionDef = actionDefId?.let { roleRegistry.getAction(it) }
            val eligibleTargetIds = if (actionDef != null) {
                val allAliveIds = targetPlayers.map { it.id }
                actionDef.eligibleTargets(session, participants.firstOrNull() ?: 0, allAliveIds)
            } else {
                targetPlayers.map { it.id }
            }

            targetPlayers.filter { it.id in eligibleTargetIds }.forEach {
                selectMenu.addOption(it.nickname, it.id.toString())
            }

            if (actionDef?.isOptional != false) {
                selectMenu.addOption("跳過", SKIP_TARGET_ID.toString())
            }

            val actionText = buildString {
                appendLine("🐺 **狼人投票**")
                appendLine("請選擇要擊殺的目標：")
                appendLine("⏱️ $durationSeconds 秒內完成投票")
            }

            session.players.values
                .filter { it.id in participants }
                .forEach { player ->
                    val message = player.channel?.sendMessage(actionText)?.setComponents(
                        ActionRow.of(selectMenu.build())
                    )?.complete()

                    if (message != null) {
                        groupState.promptMessageIds[player.id] = message.idLong
                    }
                }

            // Initialize empty votes for all electorates so dashboard shows them as "Not Voted"
            // and allWolvesVoted() knows to wait for them.
            groupState.electorates.forEach { pid ->
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
            actionInstance.targets.clear()
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
            actionInstance.actionPromptId = null
            actionInstance.targetPromptId = null

            // Execute immediate actions (e.g., Nightmare, Seer)
            val actionDef = roleRegistry.getAction(actionInstance.actionDefinitionId!!)
            if (actionDef?.isImmediate == true) {
                roleActionExecutor.executeActionInstance(lockedSession, actionInstance)
                actionInstance.status = ActionStatus.PROCESSED

                // Add to history if necessary (e.g. for replay or death triggers)
                val currentDay = lockedSession.day
                val history = lockedSession.stateData.executedActions.getOrPut(currentDay) { mutableListOf() }
                if (!history.contains(actionInstance)) {
                    history.add(actionInstance)
                }
            }

            // Notify NightManager of activity
            nightManager.notifyPhaseUpdate(guildId)

            // Notify ActionProcessed event for callback-based step advancement
            roleEventService.notifyListeners(
                lockedSession, RoleEventType.ACTION_PROCESSED, mapOf(
                    "playerId" to playerId,
                    "actionId" to actionInstance.actionDefinitionId!!
                )
            )

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

            // Validate target eligibility
            if (targetPlayerId != SKIP_TARGET_ID) {
                val actionDefId = ActionDefinitionId.fromString(groupStateId)
                val actionDef = actionDefId?.let { roleRegistry.getAction(it) }
                if (actionDef != null) {
                    val allAliveIds = lockedSession.alivePlayers().values.map { it.id }
                    val eligible = actionDef.eligibleTargets(lockedSession, playerId, allAliveIds)
                    if (targetPlayerId !in eligible) {
                        log.warn("Player $playerId tried to vote for ineligible target $targetPlayerId in action $groupStateId")
                        return@withLockedSession false
                    }
                }
            }

            groupState.votes.removeIf { it.voterId == playerId }
            groupState.votes.add(WolfVote(voterId = playerId, targetId = targetPlayerId))

            // Clear prompt ID to prevent replay attacks as requested
            groupState.promptMessageIds.remove(playerId)

            // Explicitly re-put into the map to ensure persistence detection
            lockedSession.stateData.wolfStates[groupStateId] = groupState

            log.info("Vote recorded: Player $playerId -> Target $targetPlayerId in group action $groupStateId")

            nightManager.notifyPhaseUpdate(guildId)

            // Notify ActionProcessed event for callback-based step advancement
            roleEventService.notifyListeners(
                lockedSession, RoleEventType.ACTION_PROCESSED, mapOf(
                    "playerId" to playerId,
                    "groupActionId" to groupStateId
                )
            )

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
            val phaseType = session.stateData.phaseType
            val pendingActions =
                session.stateData.submittedActions.filter {
                    (it.status == ActionStatus.PENDING || it.status == ActionStatus.ACTING)
                }.filter { action ->
                    // Filter based on phase to avoid expiring unrelated actions
                    when (phaseType) {
                        NightPhase.NIGHTMARE_ACTION -> {
                            val player = session.getPlayer(action.actor)
                            player?.roles?.contains("夢魘") == true
                        }

                        NightPhase.WOLF_YOUNGER_BROTHER_ACTION -> {
                            val player = session.getPlayer(action.actor)
                            player?.roles?.contains("狼弟") == true
                        }

                        NightPhase.WEREWOLF_VOTING -> false // Handled separately by group vote logic
                        else -> true // ROLE_ACTIONS or generic cleanup
                    }
                }

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
                            session.getPlayer(playerId)?.channel?.sendMessage("⏱️ **時間到！** 已為你隨機選擇目標 **玩家$target**。")
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

                        // Notify ActionProcessed event for callback-based step advancement
                        // (even for timeouts/skips)
                        roleEventService.notifyListeners(
                            session, RoleEventType.ACTION_PROCESSED, mapOf(
                                "playerId" to playerId,
                                "actionId" to (it.actionDefinitionId ?: "UNKNOWN"),
                                "isTimeout" to true
                            )
                        )
                    }

                    if (player != null) {
                        player.actionSubmitted = true
                        action.status = ActionStatus.SKIPPED
                        action.targets.clear()
                        action.targets.add(SKIP_TARGET_ID)
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
            val phaseType = session.stateData.phaseType
            val pendingActions =
                session.stateData.submittedActions.filter {
                    (it.status == ActionStatus.PENDING || it.status == ActionStatus.ACTING)
                }.filter { action ->
                    // Filter based on phase to avoid reminding unrelated actors
                    when (phaseType) {
                        NightPhase.NIGHTMARE_ACTION -> {
                            val player = session.getPlayer(action.actor)
                            player?.roles?.contains("夢魘") == true
                        }

                        NightPhase.WOLF_YOUNGER_BROTHER_ACTION -> {
                            val player = session.getPlayer(action.actor)
                            player?.roles?.contains("狼弟") == true
                        }

                        NightPhase.WEREWOLF_VOTING -> false // Handled manually or via separate logic
                        else -> true // ROLE_ACTIONS or generic cleanup
                    }
                }
            pendingActions.forEach { action ->
                val availableActions = session.getAvailableActionsForPlayer(action.actor, roleRegistry)
                val isWolfBrotherAction =
                    availableActions.any { it.actionId == ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL }
                val isNightmareAction =
                    availableActions.any { it.actionId == ActionDefinitionId.NIGHTMARE_FEAR }

                val msg = when {
                    isWolfBrotherAction -> "⚠️ **提醒**: 還剩 **30秒**！若未發動攻擊，你將會 **飲恨自殺**！"
                    isNightmareAction -> "⚠️ **提醒**: 還剩 **30秒**！若未選擇目標，系統將為你 **隨機選擇** 一名玩家進行恐懼！"
                    else -> "⚠️ **提醒**: 還剩 **30秒** 需要選擇行動或跳過，否則將視為放棄"
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
            actionInstance.actionPromptId = null
            actionInstance.targetPromptId = null
            if (actionInstance.status != ActionStatus.SKIPPED) {
                actionInstance.status = ActionStatus.SUBMITTED
            }
            WerewolfApplication.gameSessionService.saveSession(session)
        }
    }
}
