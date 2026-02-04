package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.game.model.SKIP_TARGET_ID
import dev.robothanzo.werewolf.service.ActionUIService
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.RoleActionService
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Handles Discord select menu interactions for action selection and targeting
 * Triggered when players select actions or targets from the UI
 */
@Component
class ActionSelectionListener(
    private val sessionRepository: SessionRepository,
    @param:Lazy
    private val actionUIService: ActionUIService,
    @param:Lazy
    private val roleActionService: RoleActionService,
    @param:Lazy
    private val gameActionService: GameActionService,
    @param:Lazy
    private val gameSessionService: GameSessionService
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(ActionSelectionListener::class.java)


    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        val componentId = event.componentId
        val userId = event.user.idLong
        val guildId = event.guild?.idLong ?: return

        try {
            when {
                // Player selecting their action: action_select_<playerId>
                componentId.startsWith("action_select_") -> {
                    handleActionSelection(event, userId, guildId, componentId)
                }

                // Player selecting target for their action: action_target_<playerId>
                componentId.startsWith("action_target_") -> {
                    handleTargetSelection(event, userId, guildId, componentId)
                }

                // Group (wolf) voting for kill target: group_target_<actionId>
                componentId.startsWith("group_target_") -> {
                    handleGroupTargetSelection(event, userId, guildId, componentId)
                }
            }
        } catch (e: Exception) {
            log.error("Error handling select interaction: ${event.componentId}", e)
            event.reply("❌ 發生錯誤，請稍後重試").setEphemeral(true).queue()
        }
    }

    private fun handleActionSelection(
        event: StringSelectInteractionEvent,
        userId: Long,
        guildId: Long,
        componentId: String
    ) {
        val playerId = componentId.removePrefix("action_select_")
        val selectedValue = event.selectedOptions.firstOrNull()?.value ?: return

        log.info("Player $playerId ($userId) selected action: $selectedValue")

        // Get session to verify ownership
        val sessionOpt = sessionRepository.findByGuildId(guildId)
        if (!sessionOpt.isPresent) {
            event.reply("❌ 找不到遊戲Session").setEphemeral(true).queue()
            return
        }
        val session = sessionOpt.get()

        val player = session.players[playerId] ?: run {
            event.reply("❌ 找不到玩家").setEphemeral(true).queue()
            return
        }

        if (player.userId != userId) {
            event.reply("❌ 這不是你的選擇").setEphemeral(true).queue()
            return
        }

        // Store selection in UI service
        val prompt = actionUIService.updateActionSelection(guildId, playerId, selectedValue, session)

        if (prompt == null) {
            event.reply("❌ 無法更新選擇").setEphemeral(true).queue()
            return
        }

        event.reply("✅ 已選擇行動: **$selectedValue**\n\n請在下一條消息選擇目標").setEphemeral(true).queue()
    }

    private fun handleTargetSelection(
        event: StringSelectInteractionEvent,
        userId: Long,
        guildId: Long,
        componentId: String
    ) {
        val playerId = componentId.removePrefix("action_target_")
        val targetUserIdStr = event.selectedOptions.firstOrNull()?.value ?: return
        val targetUserId = targetUserIdStr.toLongOrNull() ?: return

        log.info("Player $playerId ($userId) selected target: $targetUserId")

        // Get session
        val sessionOpt = sessionRepository.findByGuildId(guildId)
        if (!sessionOpt.isPresent) {
            event.reply("❌ 找不到遊戲Session").setEphemeral(true).queue()
            return
        }
        val session = sessionOpt.get()

        // Submit target selection
        val success = actionUIService.submitTargetSelection(
            guildId,
            playerId,
            userId,
            targetUserId,
            session
        )

        if (!success) {
            event.reply("❌ 無法提交選擇").setEphemeral(true).queue()
            return
        }

        event.reply("✅ 已選擇目標").setEphemeral(true).queue()

        // Submit action if prompt is complete, then clear prompt and advance night if no prompts remain
        val prompt = actionUIService.getPrompt(playerId)
        if (prompt != null && prompt.selectedAction != null && !prompt.selectedTargets.isNullOrEmpty()) {
            // submit the action as PLAYER
            roleActionService.submitAction(
                guildId,
                prompt.selectedAction!!.actionId,
                prompt.userId,
                prompt.selectedTargets ?: emptyList(),
                "PLAYER"
            )
        }

        // Remove prompt from active set and session state
        actionUIService.clearPrompt(playerId)
        @Suppress("UNCHECKED_CAST")
        val promptsMap =
            (session.stateData.getOrDefault("actionPrompts", mutableMapOf<String, Any>()) as MutableMap<String, Any>)
        promptsMap.remove(playerId)
        session.stateData["actionPrompts"] = promptsMap
        sessionRepository.save(session)

        // If no more active prompts remain, resolve night actions
        if (promptsMap.isEmpty()) {
            try {
                val resolution = roleActionService.resolveNightActions(session)
                // Apply deaths
                for ((cause, uids) in resolution.deaths) {
                    for (uid in uids) {
                        gameActionService.markPlayerDead(session, uid, false, cause)
                    }
                }

                // Broadcast update
                gameSessionService.broadcastUpdate(guildId)
            } catch (e: Exception) {
                log.error("Failed to resolve night actions: {}", e.message, e)
            }
        } else {
            // Otherwise, optionally notify next players or do nothing since their prompts are independent
            // Optionally, you could trigger reminders or send the next prompt explicitly here
        }
    }

    private fun handleGroupTargetSelection(
        event: StringSelectInteractionEvent,
        userId: Long,
        guildId: Long,
        componentId: String
    ) {
        val actionId = componentId.removePrefix("group_target_")
        val targetUserIdStr = event.selectedOptions.firstOrNull()?.value ?: return
        val targetUserId = targetUserIdStr.toLongOrNull() ?: return

        log.info("Wolf $userId voted for target: $targetUserId in group action: $actionId")

        // Get session
        val sessionOpt = sessionRepository.findByGuildId(guildId)
        if (!sessionOpt.isPresent) {
            event.reply("❌ 找不到遊戲Session").setEphemeral(true).queue()
            return
        }
        val session = sessionOpt.get()

        // Record the vote
        val success = actionUIService.submitGroupVote(
            guildId,
            actionId,
            userId,
            targetUserId,
            session
        )

        if (!success) {
            event.reply("❌ 無法記錄投票").setEphemeral(true).queue()
            return
        }

        val groupState = actionUIService.getGroupState(actionId) ?: return

        // Get the player name for feedback
        val targetName = if (targetUserId == SKIP_TARGET_ID) {
            "跳過"
        } else {
            val targetPlayer = session.players.values.firstOrNull { it.userId == targetUserId }
            targetPlayer?.nickname ?: "玩家 $targetUserId"
        }

        event.reply("✅ 你投票支持擊殺: **$targetName**").setEphemeral(true).queue()

        // Broadcast real-time tally to wolves
        val tallyMessage = buildWolfTallyMessage(session, groupState.votes, groupState.participants.size)
        session.players.values
            .filter { it.userId != null && it.userId in groupState.participants }
            .forEach { player ->
                player.send(tallyMessage)
            }

        // End early when all wolves have voted
        if (groupState.votes.size >= groupState.participants.size) {
            val finalTarget = actionUIService.resolveGroupVote(groupState)
            if (finalTarget != null) {
                roleActionService.submitAction(
                    guildId,
                    actionId,
                    userId,
                    listOf(finalTarget),
                    "GROUP"
                )
            }
            actionUIService.clearGroupState(actionId)
        }
    }

    private fun buildWolfTallyMessage(
        session: dev.robothanzo.werewolf.database.documents.Session,
        votes: Map<Long, Long>,
        totalVoters: Int
    ): String {
        val voteCounts = votes.values.groupingBy { it }.eachCount()
        val lines = mutableListOf<String>()
        lines.add("📊 **狼人投票即時統計**")
        lines.add("已投票: ${votes.size}/$totalVoters")

        val sortedTargets = voteCounts.entries.sortedByDescending { it.value }
        if (sortedTargets.isEmpty()) {
            lines.add("尚未有人投票")
        } else {
            for ((targetUserId, count) in sortedTargets) {
                val label = if (targetUserId == SKIP_TARGET_ID) {
                    "跳過"
                } else {
                    val targetPlayer = session.players.values.firstOrNull { it.userId == targetUserId }
                    targetPlayer?.nickname ?: "玩家 $targetUserId"
                }
                lines.add("• $label: $count")
            }
        }

        return lines.joinToString("\n")
    }
}
