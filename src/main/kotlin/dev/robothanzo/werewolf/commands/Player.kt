package dev.robothanzo.werewolf.commands

import dev.robothanzo.jda.interactions.annotations.Button
import dev.robothanzo.jda.interactions.annotations.slash.Command
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand
import dev.robothanzo.jda.interactions.annotations.slash.options.Option
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.utils.CmdUtils
import dev.robothanzo.werewolf.utils.MsgUtils
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent
import org.slf4j.LoggerFactory

@Command
class Player {
    companion object {
        private val log = LoggerFactory.getLogger(Player::class.java)

        // Delegated to PoliceService
        fun selectNewPolice(event: EntitySelectInteractionEvent) {
            WerewolfApplication.policeService.selectNewPolice(event)
        }

        // Delegated to PoliceService
        fun confirmNewPolice(event: ButtonInteractionEvent) {
            WerewolfApplication.policeService.confirmNewPolice(event)
        }

        // Delegated to PoliceService
        fun destroyPolice(event: ButtonInteractionEvent) {
            WerewolfApplication.policeService.destroyPolice(event)
        }
    }

    @Button
    fun changeRoleOrder(event: ButtonInteractionEvent) {
        if (event.guild == null) return
        event.deferReply().queue()
        val session = CmdUtils.getSession(event) ?: return

        for (player in session.players.values) {
            if (event.user.idLong == player.userId) {
                try {
                    WerewolfApplication.playerService.switchRoleOrder(
                        event.guild!!.idLong,
                        player.id.toString()
                    )
                    event.hook.editOriginal(":white_check_mark: 交換成功").queue()
                } catch (e: Exception) {
                    event.hook.editOriginal(":x: " + e.message).queue()
                }
                return
            }
        }
        event.hook.editOriginal(":x: 你不是玩家").queue()
    }

    @Subcommand(description = "升官為法官")
    fun judge(event: SlashCommandInteractionEvent, @Option(value = "user") user: User) {
        event.deferReply().queue()
        if (!CmdUtils.isAdmin(event)) return
        val session = CmdUtils.getSession(event) ?: return
        event.guild!!.addRoleToMember(
            event.guild!!.getMemberById(user.id)!!,
            event.guild!!.getRoleById(session.judgeRoleId)!!
        ).queue()
        event.hook.editOriginal(":white_check_mark:").queue()
    }

    @Subcommand(description = "貶官為庶民")
    fun demote(event: SlashCommandInteractionEvent, @Option(value = "user") user: User) {
        event.deferReply().queue()
        if (!CmdUtils.isAdmin(event)) return
        val session = CmdUtils.getSession(event) ?: return
        event.guild!!.removeRoleFromMember(
            event.guild!!.getMemberById(user.id)!!,
            event.guild!!.getRoleById(session.judgeRoleId)!!
        ).queue()
        event.hook.editOriginal(":white_check_mark:").queue()
    }

    @Subcommand(description = "使玩家成為死人/旁觀者")
    fun died(
        event: SlashCommandInteractionEvent,
        @Option(value = "user", description = "死掉的使用者") user: User,
        @Option(
            value = "last_words",
            description = "是否讓他講遺言 (預設為否) (若為雙身分，只會在兩張牌都死掉的時候啟動)",
            optional = true
        ) lastWords: Boolean?
    ) {
        event.deferReply().queue()
        if (!CmdUtils.isAdmin(event)) return
        val session = CmdUtils.getSession(event) ?: return

        try {
            WerewolfApplication.gameActionService.markPlayerDead(
                session.guildId,
                user.idLong,
                lastWords ?: false
            )
            event.hook.editOriginal(":white_check_mark:").queue()
        } catch (e: Exception) {
            event.hook.editOriginal(":x: 標記玩家死亡失敗: ${e.message}").queue()
        }
    }

    @Subcommand(description = "指派玩家編號並傳送身分")
    fun assign(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        if (!CmdUtils.isAdmin(event)) return
        CmdUtils.getSession(event) ?: return

        try {
            WerewolfApplication.roleService.assignRoles(
                event.guild!!.idLong,
                { msg -> log.info("[Assign] $msg") },
                { }
            )
            event.hook.editOriginal(":white_check_mark: 身分分配完成！").queue()
        } catch (e: Exception) {
            event.hook.editOriginal(":x: " + e.message).queue()
        }
    }

    @Subcommand(description = "列出每個玩家的身分資訊")
    fun roles(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        if (!CmdUtils.isAdmin(event)) return
        val session = CmdUtils.getSession(event) ?: return
        val embedBuilder = EmbedBuilder()
            .setTitle("身分列表")
            .setColor(MsgUtils.randomColor)

        for (p in session.players.keys.sortedWith(MsgUtils.getAlphaNumComparator())) {
            val player = session.players[p]!!
            embedBuilder.addField(
                player.nickname,
                (player.roles?.joinToString("、") ?: "無") + (if (player.police) " (警長)" else "") +
                        (if (player.jinBaoBao) " (金寶寶)" else if (player.duplicated) " (複製人)" else ""),
                true
            )
        }
        event.hook.editOriginalEmbeds(embedBuilder.build()).queue()
    }

    @Subcommand(description = "強制某人成為警長 (將會清除舊的警長)")
    fun force_police(
        event: SlashCommandInteractionEvent,
        @Option(value = "user", description = "要強制成為警長的玩家") user: User
    ) {
        event.deferReply().queue()
        if (!CmdUtils.isAdmin(event)) return
        if (event.guild == null) return

        try {
            WerewolfApplication.gameActionService.setPolice(event.guild!!.idLong, user.idLong)
            event.hook.editOriginal(":white_check_mark:").queue()
        } catch (e: Exception) {
            event.hook.editOriginal(":x: " + e.message).queue()
        }
    }
}
