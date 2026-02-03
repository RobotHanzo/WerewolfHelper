package dev.robothanzo.werewolf.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.pushEach
import com.mongodb.client.model.Updates.set
import dev.robothanzo.jda.interactions.annotations.slash.Command
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand
import dev.robothanzo.jda.interactions.annotations.slash.options.AutoCompleter
import dev.robothanzo.jda.interactions.annotations.slash.options.Option
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.utils.CmdUtils
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*

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
        log.info("Queued a server setup for user {} with {} players", userId, players)
    }

    @Subcommand(description = "刪除所在之伺服器(僅可在狼人殺伺服器內使用)")
    fun delete(
        event: SlashCommandInteractionEvent,
        @Option(value = "guild_id", optional = true) guildId: String?
    ) {
        try {
            if (!CmdUtils.isServerCreator(event)) return
            if (guildId == null) {
                event.guild!!.leave().queue()
                Session.fetchCollection().deleteOne(eq("guildId", event.guild!!.idLong))
                WerewolfApplication.speechService.interruptSession(event.guild!!.idLong)
            } else {
                WerewolfApplication.jda.getGuildById(guildId)!!.leave().queue()
                Session.fetchCollection().deleteOne(eq("guildId", guildId))
                WerewolfApplication.speechService.interruptSession(
                    WerewolfApplication.jda.getGuildById(guildId)!!.idLong
                )
            }
            event.reply(":white_check_mark:").queue()
        } catch (e: Exception) {
            event.reply(":x:").queue()
        }
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

        val session = Session.fetchCollection().find(eq("guildId", gid)).first()
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

        val dashboardUrl = System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173")
        val fullUrl = "$dashboardUrl/server/${guild.id}"

        event.reply("管理面板連結：$fullUrl").setEphemeral(false).queue()
    }

    data class PendingSetup(
        val players: Int,
        val doubleIdentity: Boolean,
        val originChannelId: Long
    )

    @Subcommand
    class Roles {
        companion object {
            fun expandStringToList(s: String, amount: Int): List<String> {
                val list: MutableList<String> = LinkedList()
                for (i in 0 until amount) {
                    list.add(s)
                }
                return list
            }
        }

        @AutoCompleter
        fun role(event: CommandAutoCompleteInteractionEvent) {
            event.replyChoices(
                WerewolfApplication.ROLES.stream()
                .filter { s: String -> s.startsWith(event.focusedOption.value) }
                .limit(25)
                .map { s: String? -> Choice(s!!, s!!) }
                .toList()).queue()
        }

        @AutoCompleter
        fun existingRole(event: CommandAutoCompleteInteractionEvent) {
            val choices: MutableList<Choice> = LinkedList()
            val session = CmdUtils.getSession(event.guild!!) ?: return
            for (role in session.roles) {
                if (role.startsWith(event.focusedOption.value)) {
                    choices.add(Choice(role, role))
                }
            }
            event.replyChoices(choices).queue()
        }

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

        @Subcommand(description = "新增角色")
        fun add(
            event: SlashCommandInteractionEvent,
            @Option(value = "role", autoComplete = true) role: String,
            @Option(value = "amount", optional = true) amount: Int?
        ) {
            event.deferReply().queue()
            if (!CmdUtils.isAdmin(event)) return
            val amt = amount ?: 1
            val session = CmdUtils.getSession(event) ?: return
            Session.fetchCollection().updateOne(
                eq("guildId", session.guildId),
                pushEach("roles", expandStringToList(role, amt))
            )
            event.hook.editOriginal(":white_check_mark:").queue()
        }

        @Subcommand(description = "刪除角色")
        fun delete(
            event: SlashCommandInteractionEvent,
            @Option(
                value = "role",
                autoComplete = true,
                autoCompleter = "existingRole"
            ) role: String?,
            @Option(value = "amount", optional = true) amount: Int?
        ) {
            event.deferReply().queue()
            if (!CmdUtils.isAdmin(event)) return
            val amt = amount ?: 1
            val session = CmdUtils.getSession(event) ?: return
            val roles = session.roles
            for (i in 0 until amt) {
                roles.remove(role)
            }
            Session.fetchCollection().updateOne(
                eq("guildId", session.guildId),
                set("roles", roles)
            )
            event.hook.editOriginal(":white_check_mark:").queue()
        }
    }

    @Subcommand
    class Set {
        private val log = LoggerFactory.getLogger(Set::class.java)

        @Subcommand(description = "設定是否為雙身分局")
        fun double_identities(
            event: SlashCommandInteractionEvent,
            @Option(value = "value") value: Boolean
        ) {
            event.deferReply().queue()
            if (!CmdUtils.isAdmin(event)) return
            val session = CmdUtils.getSession(event) ?: return
            Session.fetchCollection().updateOne(
                eq("guildId", event.guild!!.idLong),
                set("doubleIdentities", value)
            )
            event.hook.editOriginal(":white_check_mark:").queue()
        }

        @Subcommand(description = "設定是否在發言後將玩家靜音")
        fun mute_after_speech(
            event: SlashCommandInteractionEvent,
            @Option(value = "value") value: Boolean
        ) {
            event.deferReply().queue()
            if (!CmdUtils.isAdmin(event)) return
            val session = CmdUtils.getSession(event) ?: return
            Session.fetchCollection().updateOne(
                eq("guildId", event.guild!!.idLong),
                set("muteAfterSpeech", value)
            )
            event.hook.editOriginal(":white_check_mark:").queue()
        }

        @Subcommand(description = "設定總玩家數量")
        fun players(event: SlashCommandInteractionEvent, @Option(value = "value") value: Int) {
            event.deferReply().queue()
            if (!CmdUtils.isAdmin(event)) return
            val session = CmdUtils.getSession(event) ?: return
            assert(event.guild != null)
            val players = session.players
            try {
                for (player in LinkedList(players.values)) {
                    if (player.id > value) {
                        players.remove(player.id.toString())
                        event.guild!!.getRoleById(player.roleId)!!.delete().queue()
                        event.guild!!.getTextChannelById(player.channelId)!!.delete()
                            .queue()
                    }
                }
                for (i in players.size + 1..value) {
                    val role = event.guild!!.createRole().setColor(MsgUtils.randomColor)
                        .setHoisted(true)
                        .setName("玩家" + Session.Player.ID_FORMAT.format(i)).complete()
                    val channel =
                        event.guild!!.createTextChannel("玩家" + Session.Player.ID_FORMAT.format(i))
                            .addPermissionOverride(
                                event.guild!!.getRoleById(session.spectatorRoleId)!!,
                                Permission.VIEW_CHANNEL.rawValue,
                                Permission.MESSAGE_SEND.rawValue
                            )
                            .addPermissionOverride(
                                role,
                                listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND),
                                listOf()
                            )
                            .addPermissionOverride(
                                event.guild!!.publicRole, listOf(),
                                listOf(
                                    Permission.VIEW_CHANNEL,
                                    Permission.MESSAGE_SEND,
                                    Permission.USE_APPLICATION_COMMANDS
                                )
                            )
                            .complete()
                    players[i.toString()] = Session.Player(
                        id = i.toInt(),
                        roleId = role.idLong,
                        channelId = channel.idLong
                    )
                }
                Session.fetchCollection().updateOne(
                    eq("guildId", event.guild!!.idLong),
                    set("players", players)
                )
            } catch (e: Exception) {
                log.error("Failed to update player amount", e)
                event.hook.editOriginal(":x: 因為未知原因而無法更新玩家人數").queue()
            }
            event.hook.editOriginal(":white_check_mark:").queue()
        }
    }
}
