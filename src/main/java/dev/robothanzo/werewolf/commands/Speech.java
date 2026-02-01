package dev.robothanzo.werewolf.commands;

import dev.robothanzo.jda.interactions.annotations.slash.Command;
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand;
import dev.robothanzo.jda.interactions.annotations.slash.options.Option;
import dev.robothanzo.werewolf.WerewolfApplication;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;

@Slf4j
@Command
public class Speech {

    @Subcommand(description = "開始自動發言流程")
    public void auto(SlashCommandInteractionEvent event) {
        event.deferReply(false).queue();
        if (!CmdUtils.isAdmin(event))
            return;

        Session session = CmdUtils.getSession(event);
        if (session == null)
            return;

        WerewolfApplication.speechService.startAutoSpeechFlow(event.getGuild().getIdLong(),
                event.getChannel().getIdLong());
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Subcommand(description = "開始新的計時 (不是自動發言流程)")
    public void start(SlashCommandInteractionEvent event,
                      @Option(value = "time", description = "計時時間(m為分鐘s為秒數，例: 10m、10s、1m30s)") Duration time) {
        if (!CmdUtils.isAdmin(event))
            return;
        event.reply(":white_check_mark:").setEphemeral(true).queue();

        @NotNull
        var member = Objects.requireNonNull(event.getMember());
        @NotNull
        var voiceState = Objects.requireNonNull(member.getVoiceState());
        if (voiceState.getChannel() == null) {
            return;
        }

        WerewolfApplication.speechService.startTimer(
                event.getGuild().getIdLong(),
                event.getChannel().getIdLong(),
                voiceState.getChannel().getIdLong(),
                (int) time.getSeconds());
    }

    @Subcommand(description = "強制終止自動發言流程 (不是終止目前使用者發言) (通常用在狼自爆的時候)")
    public void interrupt(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event))
            return;

        WerewolfApplication.speechService.interruptSession(event.getGuild().getIdLong());
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Subcommand(description = "解除所有人的靜音")
    public void unmute_all(SlashCommandInteractionEvent event) {
        if (!CmdUtils.isAdmin(event))
            return;
        WerewolfApplication.speechService.setAllMute(event.getGuild().getIdLong(), false);
        event.reply(":white_check_mark:").queue();
    }

    @Subcommand(description = "靜音所有人")
    public void mute_all(SlashCommandInteractionEvent event) {
        if (!CmdUtils.isAdmin(event))
            return;
        WerewolfApplication.speechService.setAllMute(event.getGuild().getIdLong(), true);
        event.reply(":white_check_mark:").queue();
    }

    // Interactions
    @dev.robothanzo.jda.interactions.annotations.select.StringSelectMenu
    public void selectOrder(StringSelectInteractionEvent event) {
        WerewolfApplication.speechService.handleOrderSelection(event);
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void confirmOrder(ButtonInteractionEvent event) {
        WerewolfApplication.speechService.confirmOrder(event);
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void skipSpeech(ButtonInteractionEvent event) {
        WerewolfApplication.speechService.skipSpeech(event);
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void interruptSpeech(ButtonInteractionEvent event) {
        WerewolfApplication.speechService.interruptSpeech(event);
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void terminateTimer(ButtonInteractionEvent event) {
        event.deferReply(true).queue();
        if (CmdUtils.isAdmin(event)) {
            try {
                WerewolfApplication.speechService.stopTimer(event.getChannel().getIdLong());
                event.getHook().editOriginal(":white_check_mark:").queue();
            } catch (Exception e) {
                event.getHook().editOriginal(":x:").queue();
            }
        } else {
            event.getHook().editOriginal(":x:").queue();
        }
    }
}
