package dev.robothanzo.werewolf.commands;

import dev.robothanzo.jda.interactions.annotations.slash.Command;
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand;
import dev.robothanzo.werewolf.WerewolfApplication;
import dev.robothanzo.werewolf.audio.Audio;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.model.Candidate;
import dev.robothanzo.werewolf.utils.CmdUtils;
import dev.robothanzo.werewolf.utils.MsgUtils;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Command
@Slf4j
public class Poll {
    public static Map<Long, Map<Integer, Candidate>> expelCandidates = new ConcurrentHashMap<>(); // key is guild id //
    // second key is
    // candidate id

    public static void handleExpelPK(Session session, GuildMessageChannel channel, Message message,
                                     List<Candidate> winners) {
        if (message != null)
            message.reply("平票，請PK").queue();
        Map<Integer, Candidate> newCandidates = new ConcurrentHashMap<>();
        for (Candidate winner : winners) {
            winner.getElectors().clear();
            winner.setExpelPK(true);
            newCandidates.put(winner.getPlayer().getId(), winner);
        }
        expelCandidates.put(channel.getGuild().getIdLong(), newCandidates);
        WerewolfApplication.speechService.startSpeechPoll(channel.getGuild(), message,
                newCandidates.values().stream().map(Candidate::getPlayer).toList(),
                () -> startExpelPoll(session, channel, false));
    }

    public static void startExpelPoll(Session session, GuildMessageChannel channel, boolean allowPK) {
        Audio.play(Audio.Resource.EXPEL_POLL, channel.getGuild().getVoiceChannelById(session.getCourtVoiceChannelId()));
        EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("驅逐投票")
                .setDescription("30秒後立刻計票，請加快手速!\n若要改票可直接按下要改成的對象\n若要改為棄票需按下原本投給的使用者")
                .setColor(MsgUtils.getRandomColor());
        List<Button> buttons = new LinkedList<>();
        for (Candidate player : expelCandidates.get(channel.getGuild().getIdLong()).values()
                .stream().sorted(Candidate.getComparator()).toList()) {
            Member user = channel.getGuild().getMemberById(player.getPlayer().getUserId());
            if (user != null) {
                buttons.add(Button.danger("voteExpel" + player.getPlayer().getId(),
                        player.getPlayer().getNickname() + " (" + user.getUser().getName() + ")"));
            }
        }
        Message message = channel.sendMessageEmbeds(embedBuilder.build())
                .setComponents(MsgUtils.spreadButtonsAcrossActionRows(buttons).toArray(new ActionRow[0])).complete();
        CmdUtils.schedule(() -> Audio.play(Audio.Resource.POLL_10S_REMAINING,
                channel.getGuild().getVoiceChannelById(session.getCourtVoiceChannelId())), 20000);
        CmdUtils.schedule(() -> {
            List<Candidate> winners = Candidate.getWinner(expelCandidates.get(channel.getGuild().getIdLong()).values(),
                    null);
            if (winners.isEmpty()) {
                if (message != null)
                    message.reply("沒有人投票，本次驅逐無人出局").queue();
                expelCandidates.remove(channel.getGuild().getIdLong());
                return;
            }

            if (winners.size() == 1) {
                Candidate winner = winners.getFirst();
                if (message != null)
                    message.reply("投票已結束，正在放逐玩家 <@!" + winner.getPlayer().getUserId() + ">").queue();

                EmbedBuilder resultEmbed = new EmbedBuilder().setTitle("驅逐投票").setColor(MsgUtils.getRandomColor())
                        .setDescription("放逐玩家: <@!" + winner.getPlayer().getUserId() + ">");
                sendVoteResult(session, channel, message, resultEmbed, expelCandidates, false);

                expelCandidates.remove(channel.getGuild().getIdLong());
            } else {
                if (allowPK) {
                    EmbedBuilder resultEmbed = new EmbedBuilder().setTitle("驅逐投票").setColor(MsgUtils.getRandomColor())
                            .setDescription("發生平票");
                    sendVoteResult(session, channel, message, resultEmbed, expelCandidates, false);

                    handleExpelPK(session, channel, message, winners);
                } else {
                    EmbedBuilder resultEmbed = new EmbedBuilder().setTitle("驅逐投票").setColor(MsgUtils.getRandomColor())
                            .setDescription("再次發生平票，本次驅逐無人出局");
                    if (message != null)
                        message.reply("再次平票，無人出局").queue();
                    sendVoteResult(session, channel, message, resultEmbed, expelCandidates, false);
                    expelCandidates.remove(channel.getGuild().getIdLong());
                }
            }
        }, 30000);
    }

    public static void sendVoteResult(Session session, GuildMessageChannel channel, Message message,
                                      EmbedBuilder resultEmbed,
                                      Map<Long, Map<Integer, Candidate>> candidates, boolean police) {
        List<Long> voted = new LinkedList<>();
        for (Candidate candidate : candidates.get(channel.getGuild().getIdLong()).values()) {
            User user = WerewolfApplication.jda.getUserById(candidate.getPlayer().getUserId());
            assert user != null;
            voted.addAll(candidate.getElectors());
            resultEmbed.addField(candidate.getPlayer().getNickname() + " (" + user.getName() + ")",
                    String.join("、", candidate.getElectorsAsMention()), false);
        }
        List<String> discarded = new LinkedList<>();
        for (Session.Player player : session.fetchAlivePlayers().values()) {
            if (!voted.contains(player.getUserId())) {
                discarded.add("<@!" + player.getUserId() + ">");
            }
        }
        resultEmbed.addField("棄票", discarded.isEmpty() ? "無" : String.join("、", discarded), false);
        if (message != null)
            message.getChannel().sendMessageEmbeds(resultEmbed.build()).queue();
        else
            channel.sendMessageEmbeds(resultEmbed.build()).queue();
    }

    @Subcommand(description = "啟動驅逐投票")
    public void expel(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event))
            return;
        Session session = CmdUtils.getSession(Objects.requireNonNull(event.getGuild()));
        if (session == null)
            return;

        Map<Integer, Candidate> candidates = new ConcurrentHashMap<>();
        for (Session.Player p : session.getPlayers().values()) {
            if (p.isAlive()) {
                candidates.put(p.getId(), Candidate.builder().player(p).expelPK(true).build());
            }
        }
        expelCandidates.put(event.getGuild().getIdLong(), candidates);
        startExpelPoll(session, (GuildMessageChannel) event.getChannel(), true);
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Subcommand
    public static class Police {
        @Subcommand(description = "啟動警長參選投票")
        public void enroll(SlashCommandInteractionEvent event) {
            event.deferReply().queue();
            if (!CmdUtils.isAdmin(event))
                return;
            Session session = CmdUtils.getSession(Objects.requireNonNull(event.getGuild()));
            if (session == null)
                return;

            if (WerewolfApplication.policeService.getSessions().containsKey(event.getGuild().getIdLong())) {
                event.getHook().editOriginal(":x: 警長選舉已在進行中").queue();
                return;
            }

            WerewolfApplication.policeService.startEnrollment(session, (GuildMessageChannel) event.getChannel(), null);
            event.getHook().editOriginal(":white_check_mark:").queue();
        }

        @Subcommand(description = "啟動警長投票 (會自動開始，請只在出問題時使用)")
        public void start(SlashCommandInteractionEvent event) {
            event.deferReply().queue();
            if (!CmdUtils.isAdmin(event))
                return;

            WerewolfApplication.policeService.forceStartVoting(event.getGuild().getIdLong());
            event.getHook().editOriginal(":white_check_mark:").queue();
        }

        @dev.robothanzo.jda.interactions.annotations.Button()
        public void enrollPolice(ButtonInteractionEvent event) {
            WerewolfApplication.policeService.enrollPolice(event);
        }
    }
}
