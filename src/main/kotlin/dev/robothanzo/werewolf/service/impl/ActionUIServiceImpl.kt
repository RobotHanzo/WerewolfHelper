package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionPrompt
import dev.robothanzo.werewolf.game.model.GroupActionState
import dev.robothanzo.werewolf.game.model.SKIP_TARGET_ID
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import dev.robothanzo.werewolf.service.ActionUIService
import dev.robothanzo.werewolf.service.DiscordService
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.components.selections.StringSelectMenu
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class ActionUIServiceImpl(
    private val discordService: DiscordService,
    private val sessionRepository: SessionRepository
) : ActionUIService {
    private val log = LoggerFactory.getLogger(ActionUIServiceImpl::class.java)


    // In-memory storage of action prompts (persisted to session stateData for durability)
    private val activePrompts = ConcurrentHashMap<String, ActionPrompt>()
    private val groupStates = ConcurrentHashMap<String, GroupActionState>()

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

            // Send action prompt to player's channel instead of DM
            val actionText = buildString {
                appendLine("🎭 **選擇行動**")
                appendLine()
                appendLine("請選擇你想要執行的行動:")
                appendLine()
                appendLine("⏱️ ${timeoutSeconds}秒內必須選擇，否則視為放棄")
            }
            val actionButtons = availableActions.map { action ->
                Button.primary(
                    "selectAction:${action.actionId}",
                    action.actionName
                )
            }.toMutableList()
            // Add skip button
            actionButtons.add(
                Button.danger(
                    "skipAction:${playerId}",
                    "跳過"
                )
            )
            if (actionButtons.isNotEmpty()) {
                player.send(actionText, queue = false)?.setComponents(
                    ActionRow.of(actionButtons)
                )?.queue()
            }

            // Create and store prompt
            val expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L)
            val prompt = ActionPrompt(
                playerId = playerId,
                userId = userId,
                actions = availableActions,
                expiresAt = expiresAt
            )

            activePrompts[playerId] = prompt

            // Also store in session for persistence
            @Suppress("UNCHECKED_CAST")
            val prompts =
                (session.stateData.getOrPut("actionPrompts") { mutableMapOf<String, Map<String, Any>>() } as MutableMap<String, Map<String, Any>>)

            prompts[playerId] = mapOf(
                "userId" to userId,
                "actions" to availableActions.map { it.actionId },
                "createdAt" to prompt.createdAt,
                "expiresAt" to prompt.expiresAt,
                "timeoutSeconds" to timeoutSeconds
            )

            sessionRepository.save(session)
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
            val jda = discordService.jda ?: return null
            val guild = jda.getGuildById(guildId) ?: return null

            // Get alive players as targets
            val targetPlayers = session.players.values
                .filter { it.isAlive && it.userId != null }
                .mapNotNull { it.userId }

            if (targetPlayers.isEmpty()) return null

            val groupState = GroupActionState(
                actionId = actionId,
                participants = participants,
                expiresAt = System.currentTimeMillis() + (durationSeconds * 1000L)
            )

            groupStates[actionId] = groupState

            val selectMenu = StringSelectMenu.create("group_target_$actionId")
                .setPlaceholder("選擇擊殺目標")
            targetPlayers.forEach { targetUserId ->
                selectMenu.addOption("玩家 $targetUserId", targetUserId.toString())
            }
            selectMenu.addOption("跳過", SKIP_TARGET_ID.toString())

            val actionText = buildString {
                appendLine("🐺 **狼人投票**")
                appendLine("請選擇要擊殺的目標：")
                appendLine("⏱️ ${durationSeconds} 秒內完成投票")
            }

            session.players.values
                .filter { it.userId != null && it.userId in participants }
                .forEach { player ->
                    player.send(actionText, queue = false)?.setComponents(
                        ActionRow.of(selectMenu.build())
                    )?.queue()
                }

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
        val prompt = activePrompts[promptId] ?: return null
        val action = prompt.actions.find { it.actionId == actionId } ?: return null

        val updated = prompt.copy(selectedAction = action)
        activePrompts[promptId] = updated

        return updated
    }

    override fun submitTargetSelection(
        guildId: Long,
        promptId: String,
        userId: Long,
        targetUserId: Long,
        session: Session
    ): Boolean {
        val prompt = activePrompts[promptId] ?: return false
        if (prompt.userId != userId) return false
        if (prompt.selectedAction == null) return false

        val updated = prompt.copy(
            selectedTargets = listOf(targetUserId)
        )
        activePrompts[promptId] = updated

        return true
    }

    override fun submitGroupVote(
        guildId: Long,
        groupStateId: String,
        userId: Long,
        targetUserId: Long,
        session: Session
    ): Boolean {
        val groupState = groupStates[groupStateId] ?: return false
        if (userId !in groupState.participants) return false
        if (System.currentTimeMillis() > groupState.expiresAt) return false

        groupState.votes[userId] = targetUserId
        log.info("Wolf $userId voted for target $targetUserId in group action $groupStateId")

        return true
    }

    override fun resolveGroupVote(groupState: GroupActionState): Long? {
        if (groupState.votes.isEmpty()) return null

        // Count votes and return most voted target
        val voteCounts = groupState.votes.values
            .filter { it != SKIP_TARGET_ID }
            .groupingBy { it }
            .eachCount()
        if (voteCounts.isEmpty()) return null

        val maxVotes = voteCounts.maxOf { it.value }
        val topTargets = voteCounts.filterValues { it == maxVotes }.keys.toList()
        return topTargets.random()
    }

    override fun getGroupState(actionId: String): GroupActionState? {
        return groupStates[actionId]
    }

    override fun clearGroupState(actionId: String) {
        groupStates.remove(actionId)
    }

    override fun cleanupExpiredPrompts(guildId: Long, session: Session?) {
        val now = System.currentTimeMillis()
        val expiredPrompts = mutableListOf<Pair<String, ActionPrompt>>()

        // Identify expired prompts
        activePrompts.forEach { (playerId, prompt) ->
            if (prompt.expiresAt < now) {
                expiredPrompts.add(playerId to prompt)
            }
        }

        // Send timeout messages to players with expired prompts
        if (session != null) {
            expiredPrompts.forEach { (playerId, prompt) ->
                val player = session.players[playerId]
                if (player != null) {
                    player.send("⏱️ **時間到！** 你的行動選擇已超時，視為放棄")
                }
            }
        }

        // Clean up action prompts
        activePrompts.entries.removeIf { it.value.expiresAt < now }

        // Clean group states
        groupStates.entries.removeIf { it.value.expiresAt < now }

        log.debug("Cleaned up ${expiredPrompts.size} expired action prompts and group states")
    }

    override fun sendReminders(guildId: Long, session: Session) {
        val now = System.currentTimeMillis()
        val thirtySecondsMs = 30_000L

        try {
            // Check active prompts for those that are 30s away from expiry
            activePrompts.forEach { (playerId, prompt) ->
                val timeUntilExpiry = prompt.expiresAt - now

                // Send reminder if between 30-31 seconds remaining (to avoid duplicates)
                if (timeUntilExpiry in (thirtySecondsMs - 1000)..thirtySecondsMs) {
                    val player = session.players[playerId] ?: return@forEach
                    player.send("⚠️ **提醒**: 還剩 **30秒** 需要選擇行動或跳過，否則將視為放棄")
                    log.info("Sent 30-second reminder to player in guild $guildId")
                }
            }
        } catch (e: Exception) {
            log.error("Error sending reminders", e)
        }
    }

    override fun clearPrompt(playerId: String) {
        activePrompts.remove(playerId)
    }
}
