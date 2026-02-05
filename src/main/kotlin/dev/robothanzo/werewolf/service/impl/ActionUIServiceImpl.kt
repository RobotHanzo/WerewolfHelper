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
    private val nightManager: NightManager
) : ActionUIService {
    private val log = LoggerFactory.getLogger(ActionUIServiceImpl::class.java)

    override fun promptPlayerForAction(
        guildId: Long,
        session: Session,
        userId: Long,
        playerId: String,
        availableActions: List<RoleAction>,
        timeoutSeconds: Int
    ): ActionPrompt? {
        if (availableActions.isEmpty()) return null

        return try {
            val player = session.players[playerId] ?: return null

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

            actionButtons.add(Button.danger("skipAction", "跳過"))

            player.channel?.sendMessage(actionText)?.setComponents(
                ActionRow.of(actionButtons)
            )?.queue()

            // Create prompt
            val expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L)
            val prompt = ActionPrompt(
                playerId = playerId,
                userId = userId,
                actions = availableActions.map { ActionInfo(it.actionId, it.actionName, it.roleName, it.timing) },
                expiresAt = expiresAt
            )

            // Store in session for persistence
            session.stateData.actionPrompts[playerId] = prompt
            WerewolfApplication.gameSessionService.saveSession(session)

            // Update dashboard status to ACTING
            WerewolfApplication.roleActionService.updateActionStatus(
                guildId,
                userId,
                "ACTING",
                actionId = null,
                targetUserIds = emptyList(),
                session = session
            )

            prompt
        } catch (e: Exception) {
            log.error("Error prompting player $userId for action", e)
            null
        }
    }

    override fun promptGroupForAction(
        guildId: Long,
        session: Session,
        actionId: String,
        participants: List<Long>,
        durationSeconds: Int
    ): GroupActionState? {
        return try {
            val targetPlayers = session.players.values.filter { it.alive && it.user != null }
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
                selectMenu.addOption(it.nickname, it.user?.id ?: "-1")
            }
            selectMenu.addOption("跳過", SKIP_TARGET_ID.toString())

            val actionText = buildString {
                appendLine("🐺 **狼人投票**")
                appendLine("請選擇要擊殺的目標：")
                appendLine("⏱️ ${durationSeconds} 秒內完成投票")
            }

            session.players.values
                .filter { it.user != null && it.user?.idLong in participants }
                .forEach { player ->
                    player.channel?.sendMessage(actionText)?.setComponents(
                        ActionRow.of(selectMenu.build())
                    )?.queue()
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
        promptId: String,
        actionId: String,
        session: Session
    ): ActionPrompt? {
        // Redundant with submitAction's lock but safe as a standalone update if called elsewhere
        return WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val prompt = lockedSession.stateData.actionPrompts[promptId] ?: return@withLockedSession null
            val action = prompt.actions.find { it.actionId == actionId } ?: return@withLockedSession null

            val updated = prompt.copy(selectedAction = action)
            lockedSession.stateData.actionPrompts[promptId] = updated

            // Update dashboard status to show selected action
            WerewolfApplication.roleActionService.updateActionStatus(
                guildId,
                prompt.userId,
                "ACTING",
                actionId = actionId,
                targetUserIds = emptyList(),
                session = lockedSession
            )

            updated
        }
    }

    override fun submitTargetSelection(
        guildId: Long,
        promptId: String,
        userId: Long,
        targetUserId: Long,
        session: Session
    ): Boolean {
        return WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val prompt = lockedSession.stateData.actionPrompts[promptId] ?: return@withLockedSession false
            if (prompt.userId != userId) return@withLockedSession false
            if (prompt.selectedAction == null) return@withLockedSession false

            val updated = prompt.copy(selectedTargets = listOf(targetUserId))

            lockedSession.stateData.actionPrompts[promptId] = updated

            // Notify NightManager of activity
            nightManager.notifyPhaseUpdate(guildId)

            true
        }
    }

    override fun getPrompt(session: Session, promptId: String): ActionPrompt? {
        return session.stateData.actionPrompts[promptId]
    }

    override fun submitGroupVote(
        player: Player,
        groupStateId: String,
        targetUserId: Long
    ): Boolean {
        val guildId = player.session?.guildId ?: return false

        return WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val groupState = lockedSession.stateData.groupStates[groupStateId] ?: return@withLockedSession false

            if (System.currentTimeMillis() > groupState.expiresAt) return@withLockedSession false

            val userId = player.user?.idLong ?: return@withLockedSession false
            if (userId !in groupState.participants) return@withLockedSession false

            groupState.votes.removeIf { it.userId == userId }
            groupState.votes.add(GroupVote(userId, targetUserId))

            log.info("Wolf $userId voted for target $targetUserId in group action $groupStateId")

            nightManager.notifyPhaseUpdate(guildId)
            true
        }
    }

    override fun resolveGroupVote(session: Session, groupState: GroupActionState): Long? {
        if (groupState.votes.isEmpty()) return null

        val voteCounts = groupState.votes
            .filter { it.targetId != SKIP_TARGET_ID }
            .groupingBy { it.targetId }
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

        val prompts = session.stateData.actionPrompts
        val expiredPromptIds = mutableListOf<String>()

        prompts.forEach { (playerId, prompt) ->
            if (prompt.expiresAt < now) {
                expiredPromptIds.add(playerId)
                session.players[playerId]?.channel?.sendMessage("⏱️ **時間到！** 你的行動選擇已超時，視為放棄")?.queue()
            }
        }

        val groupStates = session.stateData.groupStates
        val expiredGroupIds = mutableListOf<String>()
        groupStates.forEach { (actionId, state) ->
            if (state.expiresAt < now) {
                expiredGroupIds.add(actionId)
            }
        }

        if (expiredPromptIds.isNotEmpty() || expiredGroupIds.isNotEmpty()) {
            expiredPromptIds.forEach { playerId ->
                prompts.remove(playerId)
                // Update dashboard status to SKIPPED
                val player = session.players[playerId]
                if (player != null && player.user != null) {
                    WerewolfApplication.roleActionService.updateActionStatus(
                        guildId,
                        player.user!!.idLong,
                        "SKIPPED",
                        targetUserIds = listOf(SKIP_TARGET_ID),
                        session = session
                    )
                }
            }
            expiredGroupIds.forEach { groupStates.remove(it) }
            WerewolfApplication.gameSessionService.saveSession(session)
            log.debug("Cleaned up ${expiredPromptIds.size} action prompts and ${expiredGroupIds.size} group states")
        }
    }

    override fun sendReminders(guildId: Long, session: Session) {
        val now = System.currentTimeMillis()
        val thirtySecondsMs = 30_000L

        val prompts = session.stateData.actionPrompts

        prompts.forEach { (playerId, prompt) ->
            val timeUntilExpiry = prompt.expiresAt - now

            if (timeUntilExpiry in (thirtySecondsMs - 1000)..thirtySecondsMs) {
                session.players[playerId]?.channel?.sendMessage("⚠️ **提醒**: 還剩 **30秒** 需要選擇行動或跳過，否則將視為放棄")
                    ?.queue()
                log.info("Sent 30-second reminder to player in guild $guildId")
            }
        }
    }

    override fun clearPrompt(session: Session, playerId: String) {
        session.stateData.actionPrompts.remove(playerId)
        WerewolfApplication.gameSessionService.saveSession(session)
    }

}
