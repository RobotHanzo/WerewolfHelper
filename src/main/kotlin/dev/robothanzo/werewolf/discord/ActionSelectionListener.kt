package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.SKIP_TARGET_ID
import dev.robothanzo.werewolf.game.model.WolfVote
import dev.robothanzo.werewolf.service.ActionUIService
import dev.robothanzo.werewolf.utils.isAdmin
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

/**
 * Handles Discord select menu interactions for action selection and targeting
 * Triggered when players select actions or targets from the UI
 */
@Component
class ActionSelectionListener(
    private val gameSessionService: dev.robothanzo.werewolf.service.GameSessionService,
    @param:Lazy
    private val actionUIService: ActionUIService
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(ActionSelectionListener::class.java)

    override fun onStringSelectInteraction(event: StringSelectInteractionEvent) {
        val componentId = event.componentId
        val userId = event.user.idLong
        val guildId = event.guild?.idLong ?: return

        try {
            if (componentId.startsWith("group_target_")) {
                handleGroupTargetSelection(event, userId, guildId, componentId)
            }
        } catch (e: Exception) {
            log.error("Error handling select interaction: ${event.componentId}", e)
            event.reply("❌ 發生錯誤，請稍後重試").setEphemeral(true).queue()
        }
    }

    private fun handleGroupTargetSelection(
        event: StringSelectInteractionEvent,
        userId: Long,
        guildId: Long,
        componentId: String
    ) {
        val actionId = componentId.removePrefix("group_target_")
        val targetPlayerId = event.selectedOptions.firstOrNull()?.value?.toIntOrNull() ?: return
        val session = gameSessionService.getSession(guildId).getOrNull() ?: return
        val player = session.getPlayerByChannel(event.channelIdLong) ?: return
        val groupState = actionUIService.getGroupState(session, actionId)
        if (player.user?.idLong != userId && event.member?.isAdmin() != true) {
            event.reply("❌ 這不是你的投票").setEphemeral(true).queue()
            return
        }

        // Verify prompt message ID to prevent clicking old prompts
        if (groupState?.promptMessageIds?.get(player.id) != event.messageIdLong) {
            event.reply("❌ 這是舊的投票按鈕，請使用最新的提示").setEphemeral(true).queue()
            return
        }

        if (!actionUIService.submitGroupVote(player, actionId, targetPlayerId)) {
            event.reply("❌ 無法紀錄投票").setEphemeral(true).queue()
            return
        }
        log.info("Wolf $userId voted for target: $targetPlayerId in group action: $actionId")

        // Fetch fresh session AFTER submission to get updated votes for the tally
        val finalSession = gameSessionService.getSession(guildId).orElse(session)
        val finalGroupState = actionUIService.getGroupState(finalSession, actionId) ?: return

        // Get the player name for feedback
        val targetName = if (targetPlayerId == SKIP_TARGET_ID) {
            "跳過"
        } else {
            val targetPlayer = finalSession.getPlayer(targetPlayerId)
            targetPlayer?.nickname ?: "玩家 $targetPlayerId"
        }

        event.reply("✅ 你投票支持擊殺: **$targetName**").setEphemeral(true).queue()

        // Broadcast real-time tally to wolves
        val tallyMessage = buildWolfTallyMessage(finalSession, finalGroupState.votes, finalGroupState.electorates.size)
        finalSession.players.values
            .filter { it.id in finalGroupState.electorates }
            .forEach { p ->
                p.channel?.sendMessage(tallyMessage)?.queue()
            }

        // Broadcasting real-time tally is enough.
        // NightStep will detect when all participants have voted via notifyPhaseUpdate.
    }

    private fun buildWolfTallyMessage(
        session: Session,
        votes: List<WolfVote>,
        totalVoters: Int
    ): String {
        val voteCounts = votes.groupingBy { it.targetId }.eachCount()
        val lines = mutableListOf<String>()
        lines.add("📊 **狼人投票即時統計 (下方顯示投票擊殺之目標)**")
        val votedCount = votes.count { it.targetId != null }
        lines.add("已投票: $votedCount/$totalVoters")

        val sortedTargets = voteCounts.entries.sortedByDescending { it.value }
        if (sortedTargets.isEmpty()) {
            lines.add("尚未有人投票")
        } else {
            for ((targetPlayerId, count) in sortedTargets) {
                targetPlayerId ?: continue
                val label = if (targetPlayerId == SKIP_TARGET_ID) {
                    "跳過"
                } else {
                    val targetPlayer = session.getPlayer(targetPlayerId)
                    targetPlayer?.nickname ?: "玩家 $targetPlayerId"
                }
                lines.add("• $label: $count")
            }
        }

        return lines.joinToString("\n")
    }
}
