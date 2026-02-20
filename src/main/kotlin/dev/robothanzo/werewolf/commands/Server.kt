package dev.robothanzo.werewolf.commands

import com.fasterxml.jackson.databind.ObjectMapper
import dev.robothanzo.jda.interactions.annotations.slash.Command
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand
import dev.robothanzo.jda.interactions.annotations.slash.options.Option
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.utils.CmdUtils
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import org.slf4j.LoggerFactory
import java.awt.Color

@Command
class Server {
    private val log = LoggerFactory.getLogger(Server::class.java)

    companion object {
        val pendingSetups: MutableMap<Long, PendingSetup> = HashMap()
    }

    @Subcommand(description = "建立一個新的狼人殺伺服器")
    fun create(
        event: SlashCommandInteractionEvent,
        @Option(value = "players", description = "玩家數量") players: Int,
        @Option(
            value = "double_identity",
            description = "是否為雙身分模式，預設否",
            optional = true
        ) doubleIdentity: Boolean?
    ) {
        if (!CmdUtils.isServerCreator(event)) return

        val userId = event.user.idLong
        val doubleId = doubleIdentity == true
        val originChannelId = event.channel.idLong
        pendingSetups[userId] = PendingSetup(players, doubleId, originChannelId)

        val inviteUrl = WerewolfApplication.jda.getInviteUrl(Permission.ADMINISTRATOR).replace(
            "scope=bot".toRegex(),
            "scope=bot%20applications.commands"
        )

        val eb = EmbedBuilder()
            .setTitle("狼人殺伺服器建立指南")
            .setDescription("由於 Discord 政策變更，機器人無法再自動建立伺服器。請依照以下步驟完成設定：")
            .addField("1. 建立伺服器", "請手動建立一個新的 Discord 伺服器，名稱建議為「狼人殺伺服器」。", false)
            .addField("2. 邀請機器人", "請點擊下方連結將機器人邀請至新伺服器 (需授予管理員權限)：\n$inviteUrl", false)
            .addField("3. 自動設定", "機器人加入後將會自動偵測並開始設定頻道與身分組。", false)
            .setColor(Color.GREEN)

        event.replyEmbeds(eb.build()).setEphemeral(true).queue()
        log.info(
            "Queued a server setup for user {} with {} players. Pending setups: {}",
            userId,
            players,
            pendingSetups.size
        )
    }

    @Subcommand(description = "列出所在之伺服器")
    fun list(event: SlashCommandInteractionEvent) {
        if (!CmdUtils.isAuthor(event)) return
        val sb = StringBuilder()
        for (guild in WerewolfApplication.jda.guilds) {
            sb.append(guild.name)
                .append(" (").append(guild.id).append(")\n")
        }
        event.reply(sb.toString()).queue()
    }

    @Subcommand
    fun session(
        event: SlashCommandInteractionEvent,
        @Option(value = "guild_id", optional = true) guildId: String?
    ) {
        event.deferReply().queue()
        if (!CmdUtils.isAuthor(event)) return
        val gid: Long
        if (guildId == null) {
            if (event.guild == null) {
                event.hook.editOriginal(":x:").queue()
                return
            }
            gid = event.guild!!.idLong
        } else {
            gid = guildId.toLong()
        }

        val session = WerewolfApplication.gameSessionService.getSession(gid).orElse(null)
        if (session == null) {
            event.hook.editOriginal(":x:").queue()
        } else {
            val eb = EmbedBuilder()
                .setTitle("戰局資訊")
                .setDescription(
                    "```json\n"
                            + ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(session)
                            + "\n```"
                )
            event.hook.editOriginalEmbeds(eb.build()).queue()
        }
    }

    @Subcommand(description = "取得管理面板連結")
    fun dashboard(event: SlashCommandInteractionEvent) {
        if (!CmdUtils.isAdmin(event)) return

        val guild = event.guild
        if (guild == null) {
            event.reply(":x: 此指令僅能在伺服器中使用").setEphemeral(true).queue()
            return
        }

        val dashboardUrl = CmdUtils.getSession(event)?.dashboardUrl
            ?: "${Session.DASHBOARD_BASE_URL}/server/${guild.id}"

        event.reply("管理面板連結：$dashboardUrl").setEphemeral(false).queue()
    }

    data class PendingSetup(
        val players: Int,
        val doubleIdentity: Boolean,
        val originChannelId: Long
    )

    @Subcommand
    class Roles {
        @Subcommand(description = "列出角色清單")
        fun list(event: SlashCommandInteractionEvent) {
            event.deferReply().queue()
            val session = CmdUtils.getSession(event) ?: return
            val embedBuilder = EmbedBuilder().setTitle("角色清單").setColor(MsgUtils.randomColor)
            val roles: MutableMap<String, Int> = HashMap()
            var rolesCount = 0
            for (role in session.roles) {
                roles[role] = if (roles.containsKey(role)) roles[role]!! + 1 else 1
            }
            for ((key, value) in roles) {
                embedBuilder.addField(key, "x$value", true)
                rolesCount += value
            }
            if (rolesCount == session.players.size * (if (session.doubleIdentities) 2 else 1)) {
                embedBuilder.setDescription(":white_check_mark: 角色數量正確")
            } else {
                embedBuilder.setDescription(
                    ":x: 角色數量錯誤，應有 *" + session.players.size * (if (session.doubleIdentities) 2 else 1)
                            + "* 個角色，現有 *" + rolesCount + "* 個"
                )
            }
            event.hook.editOriginalEmbeds(embedBuilder.build()).queue()
        }
    }
}
