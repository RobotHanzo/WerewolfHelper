package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import org.springframework.stereotype.Component

@Component
class JudgeDecisionStep : GameStep {
    override val id = "JUDGE_DECISION"
    override val name = "法官裁決"

    override fun onStart(session: Session, service: GameStateService) {
        val result = session.hasEnded(null)
        val reason = result.reason

        val embed = EmbedBuilder()
            .setTitle("⚖️ 遊戲結束判定")
            .setDescription(
                """
                系統偵測到遊戲可能已經結束。
                **判定結果**: $reason

                請法官選擇下一步行動：
                • **結束遊戲**: 停止流程，開放所有麥克風與頻道權限。
                • **繼續遊戲**: 忽略此判定，繼續進行下一階段 (${session.stateData.pendingNextStep})。
                """.trimIndent()
            )
            .setColor(MsgUtils.randomColor)
            .build()

        // This is a prompt for the JUDGE
        val buttons = ActionRow.of(
            Button.danger("end_game_confirm", "結束遊戲"),
            Button.success("continue_game", "繼續遊戲")
        )

        session.judgeTextChannel?.sendMessageEmbeds(embed)?.setComponents(buttons)?.queue()
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Nothing specific to clean up
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val action = input["action"] as? String ?: return mapOf("success" to false)

        when (action) {
            "end_game_confirm" -> {
                performEndGame(session)
                return mapOf("success" to true)
            }

            "continue_game" -> {
                val nextStepId = session.stateData.pendingNextStep
                if (nextStepId != null) {
                    session.addLog(LogType.SYSTEM, "法官選擇繼續遊戲，進入下一階段: $nextStepId")
                    session.stateData.pendingNextStep = null
                    session.stateData.gameEndReason = null
                    WerewolfApplication.gameStateService.startStep(session, nextStepId)
                } else {
                    // Fallback if lost
                    WerewolfApplication.gameStateService.nextStep(session)
                }
                return mapOf("success" to true)
            }
        }
        return mapOf("success" to false)
    }

    private fun performEndGame(session: Session) {
        session.addLog(LogType.SYSTEM, "法官確認結束遊戲。")
        session.courtTextChannel?.sendMessage("✅ 遊戲結束，正在開放權限...")?.queue()

        val guild = session.guild ?: return
        val courtVoice = session.courtVoiceChannel
        val courtText = session.courtTextChannel
        val spectatorText = session.spectatorTextChannel

        // 1. Open all mics in Court Voice
        courtVoice?.let { vc ->
            // Unmute everyone currently connected
            vc.members.forEach { member ->
                try {
                    guild.mute(member, false).queue()
                } catch (_: Exception) {
                }
            }

            // Update permission to allow speaking for @everyone or the player role
            val everyone = guild.publicRole
            vc.upsertPermissionOverride(everyone).grant(Permission.VOICE_SPEAK).queue()
        }

        // 2. Grant Court/Off-court permissions (Read/Write) to everyone
        // "開放法院、場外的發言和閱讀權限給所有人" -> Everyone can read/speak in Court Text and Spectator Text?
        // Usually "Off-court" means Spectator channel.

        val publicRole = guild.publicRole

        courtText?.upsertPermissionOverride(publicRole)
            ?.grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)
            ?.queue()

        spectatorText?.upsertPermissionOverride(publicRole)
            ?.grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)
            ?.queue()

        // 3. "開放死人旁觀語音發言權限" -> Dead players (Spectator Role) can speak.
        // Already covered by granting @everyone VOICE_SPEAK in court voice?
        // Just to be sure, let's explicitly grant it to Spectator Role if exists
        val spectatorRole = session.spectatorRole
        if (spectatorRole != null && courtVoice != null) {
            courtVoice.upsertPermissionOverride(spectatorRole).grant(Permission.VOICE_SPEAK).queue()
        }
    }
}
