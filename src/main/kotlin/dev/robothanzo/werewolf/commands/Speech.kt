package dev.robothanzo.werewolf.commands

import dev.robothanzo.jda.interactions.annotations.slash.Command
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand
import dev.robothanzo.jda.interactions.annotations.slash.options.Option
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.utils.CmdUtils
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import java.time.Duration

@Command
class Speech {
    @Subcommand(description = "開始自動發言流程")
    fun auto(event: SlashCommandInteractionEvent) {
        event.deferReply(false).queue()
        if (!CmdUtils.isAdmin(event)) return

        val session = CmdUtils.getSession(event) ?: return

        WerewolfApplication.speechService.startAutoSpeechFlow(
            session,
            event.channel.idLong
        )
        event.hook.editOriginal(":white_check_mark:").queue()
    }

    @Subcommand(description = "開始新的計時 (不是自動發言流程)")
    fun start(
        event: SlashCommandInteractionEvent,
        @Option(value = "time", description = "計時時間(m為分鐘s為秒數，例: 10m、10s、1m30s)") time: Duration
    ) {
        if (!CmdUtils.isAdmin(event)) return
        event.reply(":white_check_mark:").setEphemeral(true).queue()

        val member = event.member!!
        val voiceState = member.voiceState!!
        if (voiceState.channel == null) {
            return
        }

        WerewolfApplication.speechService.startTimer(
            event.guild!!.idLong,
            event.channel.idLong,
            voiceState.channel!!.idLong,
            time.seconds.toInt()
        )
    }

    @Subcommand(description = "強制終止自動發言流程 (不是終止目前使用者發言) (通常用在狼自爆的時候)")
    fun interrupt(event: SlashCommandInteractionEvent) {
        event.deferReply().queue()
        if (!CmdUtils.isAdmin(event)) return

        WerewolfApplication.speechService.interruptSession(event.guild!!.idLong)
        event.hook.editOriginal(":white_check_mark:").queue()
    }

    @Subcommand(description = "解除所有人的靜音")
    fun unmute_all(event: SlashCommandInteractionEvent) {
        if (!CmdUtils.isAdmin(event)) return
        WerewolfApplication.speechService.setAllMute(event.guild!!.idLong, false)
        event.reply(":white_check_mark:").queue()
    }

    @Subcommand(description = "靜音所有人")
    fun mute_all(event: SlashCommandInteractionEvent) {
        if (!CmdUtils.isAdmin(event)) return
        WerewolfApplication.speechService.setAllMute(event.guild!!.idLong, true)
        event.reply(":white_check_mark:").queue()
    }
}
