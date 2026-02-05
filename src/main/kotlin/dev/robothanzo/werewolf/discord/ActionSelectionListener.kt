package dev.robothanzo.werewolf.discord

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.GroupVote
import dev.robothanzo.werewolf.game.model.SKIP_TARGET_ID
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
    private val sessionRepository: SessionRepository,
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
        val targetUserId = event.selectedOptions.firstOrNull()?.value?.toLongOrNull() ?: return
        val session = sessionRepository.findByGuildId(guildId).getOrNull() ?: return
        val player = session.getPlayerByChannel(event.channelIdLong) ?: return
        if (player.user?.idLong != userId && event.member?.isAdmin() != true) {
            event.reply("❌ 這不是你的投票").setEphemeral(true).queue()
            return
        }

        if (!actionUIService.submitGroupVote(player, actionId, targetUserId)) {
            event.reply("❌ 無法紀錄投票").setEphemeral(true).queue()
            return
        }
        log.info("Wolf $userId voted for target: $targetUserId in group action: $actionId")

        // Fetch fresh session to get updated votes for the tally
        val freshSession = dev.robothanzo.werewolf.utils.CmdUtils.getSession(event.guild) ?: return
        val groupState = actionUIService.getGroupState(freshSession, actionId) ?: return

        // Get the player name for feedback
        val targetName = if (targetUserId == SKIP_TARGET_ID) {
            "跳過"
        } else {
            val targetPlayer = session.players.values.firstOrNull { it.user?.idLong == targetUserId }
            targetPlayer?.nickname ?: "玩家 $targetUserId"
        }

        event.reply("✅ 你投票支持擊殺: **$targetName**").setEphemeral(true).queue()

        // Broadcast real-time tally to wolves
        val tallyMessage = buildWolfTallyMessage(session, groupState.votes, groupState.participants.size)
        session.players.values
            .filter { it.user != null && it.user?.idLong in groupState.participants }
            .forEach { player ->
                player.channel?.sendMessage(tallyMessage)?.queue()
            }

        // Broadcasting real-time tally is enough. 
        // NightStep will detect when all participants have voted via notifyPhaseUpdate.
    }

    private fun buildWolfTallyMessage(
        session: Session,
        votes: List<GroupVote>,
        totalVoters: Int
    ): String {
        val voteCounts = votes.groupingBy { it.targetId }.eachCount()
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
                    val targetPlayer = session.players.values.firstOrNull { it.user?.idLong == targetUserId }
                    targetPlayer?.nickname ?: "玩家 $targetUserId"
                }
                lines.add("• $label: $count")
            }
        }

        return lines.joinToString("\n")
    }
}
