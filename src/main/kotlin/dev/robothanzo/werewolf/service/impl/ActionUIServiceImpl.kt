package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.service.ActionUIService
import dev.robothanzo.werewolf.service.NightManager
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ActionUIServiceImpl(
    private val nightManager: NightManager,
    private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry
) : ActionUIService {
    private val log = LoggerFactory.getLogger(ActionUIServiceImpl::class.java)

    override fun promptPlayerForAction(
        guildId: Long,
        session: Session,
        playerId: Int,
        availableActions: List<RoleAction>,
        timeoutSeconds: Int
    ): ActionData? {
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
                dev.robothanzo.werewolf.utils.MsgUtils.spreadButtonsAcrossActionRows(actionButtons)
            )?.complete()

            // Create/Update action data
            val expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L)
            val actionData = session.stateData.actionData.getOrPut(playerId.toString()) {
                ActionData(playerId = playerId, role = player.roles?.firstOrNull() ?: "未知")
            }

            actionData.availableActions =
                availableActions.map { ActionInfo(it.actionId, it.actionName, it.roleName, it.timing) }
            actionData.expiresAt = expiresAt
            actionData.targetPromptId = message?.idLong
            actionData.status = ActionStatus.ACTING

            WerewolfApplication.gameSessionService.saveSession(session)

            // Update dashboard status to ACTING
            WerewolfApplication.roleActionService.updateActionStatus(
                guildId,
                playerId,
                ActionStatus.ACTING,
                actionId = null,
                targetPlayerIds = emptyList(),
                session = session
            )

            actionData
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
    ): GroupActionState? {
        return try {
            val targetPlayers = session.alivePlayers().values
            if (targetPlayers.isEmpty()) return null

            val expiresAt = System.currentTimeMillis() + (durationSeconds * 1000L)
            val groupState = GroupActionState(
                actionId = actionId,
                participants = participants,
                expiresAt = expiresAt
            )

            // Store in session
            session.stateData.groupStates[actionId] = groupState

            val selectMenu = StringSelectMenu.create("group_target_$actionId")
                .setPlaceholder("選擇擊殺目標")
            targetPlayers.forEach {
                selectMenu.addOption(it.nickname, it.id.toString())
            }
            selectMenu.addOption("跳過", SKIP_TARGET_ID.toString())

            val actionText = buildString {
                appendLine("🐺 **狼人投票**")
                appendLine("請選擇要擊殺的目標：")
                appendLine("⏱️ ${durationSeconds} 秒內完成投票")
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
                session.stateData.werewolfVotes.getOrPut(pid.toString()) {
                    WolfVote(voterId = pid, targetId = null)
                }
                // Also add to groupState.votes
                if (groupState.votes.none { it.playerId == pid }) {
                    groupState.votes.add(GroupVote(pid, null))
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
        actionId: String,
        session: Session
    ): ActionData? {
        // Redundant with submitAction's lock but safe as a standalone update if called elsewhere
        return WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val actionData = lockedSession.stateData.actionData[playerId.toString()] ?: return@withLockedSession null
            val action = actionData.availableActions.find { it.actionId == actionId } ?: return@withLockedSession null

            actionData.selectedAction = action

            // Update dashboard status to show selected action
            WerewolfApplication.roleActionService.updateActionStatus(
                guildId,
                playerId,
                ActionStatus.ACTING,
                actionId = actionId,
                targetPlayerIds = emptyList(),
                session = lockedSession
            )

            actionData
        }
    }

    override fun submitTargetSelection(
        guildId: Long,
        playerId: Int,
        targetPlayerId: Int,
        session: Session
    ): Boolean {
        return WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val actionData = lockedSession.stateData.actionData[playerId.toString()] ?: return@withLockedSession false
            if (actionData.selectedAction == null) return@withLockedSession false

            actionData.selectedTargets = listOf(targetPlayerId)

            // Notify NightManager of activity
            nightManager.notifyPhaseUpdate(guildId)
            nightManager.broadcastNightStatus(lockedSession)

            true
        }
    }

    override fun getActionData(session: Session, playerId: Int): ActionData? {
        return session.stateData.actionData[playerId.toString()]
    }

    override fun submitGroupVote(
        player: Player,
        groupStateId: String,
        targetPlayerId: Int
    ): Boolean {
        val guildId = player.session?.guildId ?: return false

        return WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val groupState = lockedSession.stateData.groupStates[groupStateId] ?: return@withLockedSession false

            if (System.currentTimeMillis() > groupState.expiresAt) return@withLockedSession false

            val playerId = player.id
            if (playerId !in groupState.participants) return@withLockedSession false

            groupState.votes.removeIf { it.playerId == playerId }
            groupState.votes.add(GroupVote(playerId, targetPlayerId))

            // Sync to werewolfVotes immediately for dashboard
            val votesMap = lockedSession.stateData.werewolfVotes
            val wolfVote = votesMap.getOrPut(playerId.toString()) {
                dev.robothanzo.werewolf.game.model.WolfVote(voterId = playerId)
            }
            wolfVote.targetId = targetPlayerId

            log.info("Player $playerId voted for target $targetPlayerId in group action $groupStateId")

            nightManager.notifyPhaseUpdate(guildId)
            nightManager.broadcastNightStatus(lockedSession)
            true
        }
    }

    override fun resolveGroupVote(session: Session, groupState: GroupActionState): Int? {
        if (groupState.votes.isEmpty()) return null

        val voteCounts = groupState.votes
            .filter { it.targetPlayerId != null && it.targetPlayerId != SKIP_TARGET_ID }
            .groupingBy { it.targetPlayerId }
            .eachCount()

        if (voteCounts.isEmpty()) return null

        val maxVotes = voteCounts.maxOf { it.value }
        val topTargets = voteCounts.filterValues { it == maxVotes }.keys.toList()
        return topTargets.random()
    }

    override fun getGroupState(session: Session, actionId: String): GroupActionState? {
        return session.stateData.groupStates[actionId]
    }

    override fun clearGroupState(session: Session, actionId: String) {
        session.stateData.groupStates.remove(actionId)
        WerewolfApplication.gameSessionService.saveSession(session)
    }

    override fun cleanupExpiredPrompts(guildId: Long, session: Session?) {
        if (session == null) return
        val now = System.currentTimeMillis()

        val actionDataMap = session.stateData.actionData
        val expiredIds = mutableListOf<String>()

        actionDataMap.forEach { (playerIdStr, data) ->
            if (data.expiresAt != null && data.expiresAt!! <= now + 500) {
                expiredIds.add(playerIdStr)
                session.getPlayer(playerIdStr)?.channel?.sendMessage("⏱️ **時間到！** 你的行動選擇已超時，視為放棄")
                    ?.queue()
            }
        }

        val groupStates = session.stateData.groupStates
        val expiredGroupIds = mutableListOf<String>()
        groupStates.forEach { (actionId, state) ->
            if (state.expiresAt < now) {
                expiredGroupIds.add(actionId)
            }
        }

        if (expiredIds.isNotEmpty() || expiredGroupIds.isNotEmpty()) {
            expiredIds.forEach { playerIdStr ->
                val data = actionDataMap[playerIdStr] ?: return@forEach

                // If there's a mandatory action, pick a random target and submit
                val mandatoryActionId = data.availableActions.find {
                    roleRegistry.getAction(it.actionId)?.isOptional == false
                }?.actionId

                if (mandatoryActionId != null) {
                    val action = roleRegistry.getAction(mandatoryActionId)!!
                    val eligible = action.eligibleTargets(
                        session,
                        data.playerId,
                        session.alivePlayers().values.map { it.id }
                    )
                    val target = eligible.randomOrNull()
                    if (target != null) {
                        log.info("Auto-submitting mandatory action $mandatoryActionId for player $playerIdStr due to timeout")
                        WerewolfApplication.roleActionService.submitAction(
                            guildId,
                            mandatoryActionId,
                            data.playerId,
                            listOf(target),
                            "SYSTEM"
                        )
                        return@forEach
                    }
                }

                // Default skip behavior for optional actions
                data.let {
                    it.availableActions = emptyList()
                    it.selectedAction = null
                    it.selectedTargets = emptyList()
                    it.expiresAt = null
                    it.targetPromptId = null
                    it.status = ActionStatus.SKIPPED
                }

                // Update player marked status
                val player = session.getPlayer(playerIdStr)
                if (player != null) {
                    player.actionSubmitted = true
                    WerewolfApplication.roleActionService.updateActionStatus(
                        guildId,
                        player.id,
                        ActionStatus.SKIPPED,
                        targetPlayerIds = listOf(SKIP_TARGET_ID),
                        session = session
                    )
                }
            }
            expiredGroupIds.forEach { groupStates.remove(it) }
            WerewolfApplication.gameSessionService.saveSession(session)
            log.debug("Cleaned up ${expiredIds.size} action prompts and ${expiredGroupIds.size} group states")
        }
    }

    override fun sendReminders(guildId: Long, session: Session) {
        val now = System.currentTimeMillis()
        val thirtySecondsMs = 30_000L

        val actionDataMap = session.stateData.actionData

        actionDataMap.forEach { (playerIdStr, data) ->
            val expiry = data.expiresAt ?: return@forEach
            val timeUntilExpiry = expiry - now

            if (timeUntilExpiry in (thirtySecondsMs - 1000)..thirtySecondsMs) {
                session.getPlayer(playerIdStr)?.channel?.sendMessage("⚠️ **提醒**: 還剩 **30秒** 需要選擇行動或跳過，否則將視為放棄")
                    ?.queue()
                log.info("Sent 30-second reminder to player in guild $guildId")
            }
        }
    }

    override fun clearPrompt(session: Session, playerId: Int) {
        val data = session.stateData.actionData[playerId.toString()]
        data?.let {
            it.availableActions = emptyList()
            it.selectedAction = null
            it.selectedTargets = emptyList()
            it.expiresAt = null
            it.targetPromptId = null
        }
        WerewolfApplication.gameSessionService.saveSession(session)
    }

}
