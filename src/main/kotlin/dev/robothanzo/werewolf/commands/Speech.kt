package dev.robothanzo.werewolf.commands

import dev.robothanzo.jda.interactions.annotations.Button
import dev.robothanzo.jda.interactions.annotations.select.StringSelectMenu
import dev.robothanzo.jda.interactions.annotations.slash.Command
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand
import dev.robothanzo.jda.interactions.annotations.slash.options.Option
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.utils.CmdUtils
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent
import org.slf4j.LoggerFactory
import java.time.Duration

@Command
class Speech {
    private val log = LoggerFactory.getLogger(Speech::class.java)

    @Subcommand(description = "開始自動發言流程")
    fun auto(event: SlashCommandInteractionEvent) {
        event.deferReply(false).queue()
        if (!CmdUtils.isAdmin(event)) return

        CmdUtils.getSession(event) ?: return

        WerewolfApplication.speechService.startAutoSpeechFlow(
            event.guild!!.idLong,
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

    // Interactions
    @StringSelectMenu
    fun selectOrder(event: StringSelectInteractionEvent) {
        WerewolfApplication.speechService.handleOrderSelection(event)
    }

    @Button
    fun confirmOrder(event: ButtonInteractionEvent) {
        WerewolfApplication.speechService.confirmOrder(event)
    }

    @Button
    fun skipSpeech(event: ButtonInteractionEvent) {
        WerewolfApplication.speechService.skipSpeech(event)
    }

    @Button
    fun interruptSpeech(event: ButtonInteractionEvent) {
        WerewolfApplication.speechService.interruptSpeech(event)
    }

    @Button
    fun terminateTimer(event: ButtonInteractionEvent) {
        event.deferReply(true).queue()
        if (CmdUtils.isAdmin(event)) {
            try {
                WerewolfApplication.speechService.stopTimer(event.channel.idLong)
                event.hook.editOriginal(":white_check_mark:").queue()
            } catch (e: Exception) {
                event.hook.editOriginal(":x:").queue()
            }
        } else {
            event.hook.editOriginal(":x:").queue()
        }
    }
}
