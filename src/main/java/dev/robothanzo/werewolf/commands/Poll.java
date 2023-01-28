package dev.robothanzo.werewolf.commands;

import dev.robothanzo.jda.interactions.annotations.slash.Command;
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand;
import dev.robothanzo.werewolf.WerewolfHelper;
import dev.robothanzo.werewolf.audio.Audio;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import dev.robothanzo.werewolf.utils.MsgUtils;
import lombok.Builder;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

@Command
public class Poll {
    public static Map<Long, Map<Integer, Candidate>> expelCandidates = new ConcurrentHashMap<>(); // key is guild id // second key is candidate id

    public static void handleExpelPK(Session session, GuildMessageChannel channel, Message message, List<Candidate> winners) {
        message.reply("平票，請PK").queue();
        Map<Integer, Candidate> newCandidates = new ConcurrentHashMap<>();
        for (Candidate winner : winners) {
            winner.electors.clear();
            winner.setExpelPK(true);
            newCandidates.put(winner.getPlayer().getId(), winner);
        }
        expelCandidates.put(channel.getGuild().getIdLong(), newCandidates);
        Speech.pollSpeech(channel.getGuild(), message, newCandidates.values().stream().map(Candidate::getPlayer).toList(),
                () -> startExpelPoll(session, channel, false));
    }

    public static void startExpelPoll(Session session, GuildMessageChannel channel, boolean allowPK) {
        Audio.play(Audio.Resource.EXPEL_POLL, channel.getGuild().getVoiceChannelById(session.getCourtVoiceChannelId()));
        EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("驅逐投票").setDescription("30秒後立刻計票，請加快手速!\n若要改票可直接按下要改成的對象\n若要改為棄票需按下原本投給的使用者").setColor(MsgUtils.getRandomColor());
        List<Button> buttons = new LinkedList<>();
        for (Candidate player : expelCandidates.get(channel.getGuild().getIdLong()).values()
                .stream().sorted(Candidate.getComparator()).toList()) {
            assert player.getPlayer().getRoles() != null;
            if (player.getPlayer().getRoles().isEmpty()) continue;
            if (player.isQuit()) continue;
            assert player.getPlayer().getUserId() != null;
            Member user = channel.getGuild().getMemberById(player.getPlayer().getUserId());
            assert user != null;
            buttons.add(Button.primary("voteExpel" + player.getPlayer().getId(),
                    "玩家" + player.getPlayer().getId() + " (" + user.getUser().getName() + "#" + user.getUser().getDiscriminator() + ")"));
        }
        Message message = channel.sendMessageEmbeds(embedBuilder.build())
                .setComponents(MsgUtils.spreadButtonsAcrossActionRows(buttons)).complete();
        CmdUtils.schedule(() -> Audio.play(Audio.Resource.POLL_10S_REMAINING, channel.getGuild().getVoiceChannelById(session.getCourtVoiceChannelId())), 20000);
        CmdUtils.schedule(() -> {
            List<Candidate> winners = Candidate.getWinner(expelCandidates.get(channel.getGuild().getIdLong()).values(), session.getPolice());
            if (winners.size() == 0) {
                message.reply("沒有人投票，不驅逐").queue();
                expelCandidates.remove(channel.getGuild().getIdLong());
            }
            if (winners.size() == 1) {
                message.reply("投票已結束，<@!" + winners.get(0).getPlayer().getUserId() + "> 遭到驅逐").queue();

                EmbedBuilder resultEmbed = new EmbedBuilder().setTitle("驅逐投票").setColor(MsgUtils.getRandomColor())
                        .setDescription("遭驅逐玩家: <@!" + winners.get(0).getPlayer().getUserId() + ">");
                sendVoteResult(session, channel, message, resultEmbed, expelCandidates, false);
                expelCandidates.remove(channel.getGuild().getIdLong());
                Player.playerDied(session, channel.getGuild().getMemberById(Objects.requireNonNull(winners.get(0).getPlayer().getUserId())), true, true);
            }
            if (winners.size() > 1) {
                if (allowPK) {
                    EmbedBuilder resultEmbed = new EmbedBuilder().setTitle("驅逐投票").setColor(MsgUtils.getRandomColor())
                            .setDescription("發生平票");
                    sendVoteResult(session, channel, message, resultEmbed, expelCandidates, false);
                    handleExpelPK(session, channel, message, winners);
                } else {
                    message.reply("平票第二次，不驅逐").queue();
                    expelCandidates.remove(channel.getGuild().getIdLong());
                }
            }
        }, 30000);
    }

    public static void sendVoteResult(Session session, GuildMessageChannel channel, Message message, EmbedBuilder resultEmbed,
                                      Map<Long, Map<Integer, Candidate>> candidates, boolean police) {
        List<Long> voted = new LinkedList<>();
        for (Candidate candidate : candidates.get(channel.getGuild().getIdLong()).values()) {
            if (candidate.isQuit()) continue;
            assert candidate.getPlayer().getUserId() != null;
            User user = WerewolfHelper.jda.getUserById(candidate.getPlayer().getUserId());
            assert user != null;
            voted.addAll(candidate.getElectors());
            resultEmbed.addField("玩家" + candidate.getPlayer().getId() + " (" + user.getName() + "#" + user.getDiscriminator() + ")",
                    String.join("、", candidate.getElectorsAsMention()), false);
        }
        List<String> discarded = new LinkedList<>();
        for (Session.Player player : session.getPlayers().values()) {
            if ((candidates.get(channel.getGuild().getIdLong()).get(player.getId()) == null || !police) &&
                    !voted.contains(player.getUserId())) {
                discarded.add("<@!" + player.getUserId() + ">");
            }
        }
        resultEmbed.addField("棄票玩家", String.join("、", discarded), false);
        message.editMessageEmbeds(resultEmbed.build()).queue();
    }

    @Subcommand(description = "啟動放逐投票")
    public void expel(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(Objects.requireNonNull(event.getGuild()));
        if (session == null) return;

        Map<Integer, Candidate> candidates = new ConcurrentHashMap<>();
        for (Session.Player player : session.getPlayers().values()) {
            candidates.put(player.getId(), Candidate.builder().player(player).build());
        }
        expelCandidates.put(event.getGuild().getIdLong(), candidates);
        startExpelPoll(session, event.getGuildChannel(), true);
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Subcommand
    public static class Police {
        public static Map<Long, Boolean> allowEnroll = new ConcurrentHashMap<>(); // key is guild id
        public static Map<Long, Map<Integer, Candidate>> candidates = new ConcurrentHashMap<>(); // key is guild id // second key is candidate id

        public static void handlePolicePK(Session session, GuildMessageChannel channel, Message message, List<Candidate> winners) {
            message.reply("平票，請PK").queue();
            Map<Integer, Candidate> newCandidates = new ConcurrentHashMap<>();
            for (Candidate winner : winners) {
                winner.electors.clear();
                newCandidates.put(winner.getPlayer().getId(), winner);
            }
            candidates.put(channel.getGuild().getIdLong(), newCandidates);
            Speech.pollSpeech(channel.getGuild(), message, newCandidates.values().stream().map(Candidate::getPlayer).toList(),
                    () -> startPolicePoll(session, channel, false));
        }

        public static void startPolicePoll(Session session, GuildMessageChannel channel, boolean allowPK) {
            Audio.play(Audio.Resource.POLICE_POLL, channel.getGuild().getVoiceChannelById(session.getCourtVoiceChannelId()));
            EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("警長投票").setDescription("30秒後立刻計票，請加快手速!\n若要改票可直接按下要改成的對象\n若要改為棄票需按下原本投給的使用者").setColor(MsgUtils.getRandomColor());
            List<Button> buttons = new LinkedList<>();
            for (Candidate player : candidates.get(channel.getGuild().getIdLong()).values()
                    .stream().sorted(Candidate.getComparator()).toList()) {
                assert player.getPlayer().getRoles() != null;
                if (player.getPlayer().getRoles().isEmpty()) continue;
                if (player.isQuit()) continue;
                assert player.getPlayer().getUserId() != null;
                Member user = channel.getGuild().getMemberById(player.getPlayer().getUserId());
                assert user != null;
                buttons.add(Button.primary("votePolice" + player.getPlayer().getId(),
                        "玩家" + player.getPlayer().getId() + " (" + user.getUser().getName() + "#" + user.getUser().getDiscriminator() + ")"));
            }
            Message message = channel.sendMessageEmbeds(embedBuilder.build())
                    .setComponents(MsgUtils.spreadButtonsAcrossActionRows(buttons)).complete();
            CmdUtils.schedule(() -> Audio.play(Audio.Resource.POLL_10S_REMAINING, channel.getGuild().getVoiceChannelById(session.getCourtVoiceChannelId())), 20000);
            CmdUtils.schedule(() -> {
                List<Candidate> winners = Candidate.getWinner(candidates.get(channel.getGuild().getIdLong()).values(), null);
                if (winners.size() == 0) {
                    message.reply("沒有人投票，警徽撕毀").queue();
                    candidates.remove(channel.getGuild().getIdLong());
                    return;
                }
                if (winners.size() == 1) {
                    message.reply("投票已結束，<@!" + winners.get(0).getPlayer().getUserId() + "> 獲勝").queue();

                    EmbedBuilder resultEmbed = new EmbedBuilder().setTitle("警長投票").setColor(MsgUtils.getRandomColor())
                            .setDescription("獲勝玩家: <@!" + winners.get(0).getPlayer().getUserId() + ">");
                    sendVoteResult(session, channel, message, resultEmbed, candidates, true);
                    candidates.remove(channel.getGuild().getIdLong());
                    Session.fetchCollection().updateOne(eq("guildId", channel.getGuild().getIdLong()),
                            set("players." + winners.get(0).getPlayer().getId() + ".police", true));
                }
                if (winners.size() > 1) {
                    if (allowPK) {
                        EmbedBuilder resultEmbed = new EmbedBuilder().setTitle("警長投票").setColor(MsgUtils.getRandomColor())
                                .setDescription("發生平票");
                        sendVoteResult(session, channel, message, resultEmbed, candidates, true);
                        handlePolicePK(session, channel, message, winners);
                    } else {
                        message.reply("平票第二次，警徽撕毀").queue();
                        candidates.remove(channel.getGuild().getIdLong());
                    }
                }
            }, 30000);
        }

        @dev.robothanzo.jda.interactions.annotations.Button()
        public void enrollPolice(ButtonInteractionEvent event) {
            event.deferReply(true).queue();
            Session session = CmdUtils.getSession(Objects.requireNonNull(event.getGuild()));
            if (session == null) {
                return;
            }
            for (Map.Entry<Integer, Candidate> candidate : new LinkedList<>(candidates.get(event.getGuild().getIdLong()).entrySet())) {
                if (Objects.equals(event.getUser().getIdLong(), candidate.getValue().getPlayer().getUserId())) {
                    if (allowEnroll.get(event.getGuild().getIdLong())) { // The enrollment process hasn't ended yet, so we remove them completely
                        candidates.get(event.getGuild().getIdLong()).remove(candidate.getKey());
                    } else {
                        candidates.get(event.getGuild().getIdLong()).get(candidate.getKey()).setQuit(true);
                    }
                    event.getHook().editOriginal(":white_check_mark: 已取消參選").queue();
                    if (!allowEnroll.get(event.getGuild().getIdLong())) {
                        Objects.requireNonNull(event.getGuild().getTextChannelById(session.getCourtTextChannelId()))
                                .sendMessage(event.getUser().getAsMention() + " 已取消參選").queue();
                    }
                    return;
                }
            }
            if ((!allowEnroll.containsKey(event.getGuild().getIdLong())) || !allowEnroll.get(event.getGuild().getIdLong())) {
                event.getHook().editOriginal(":x: 無法參選，時間已到").queue();
            }
            for (Session.Player player : session.getPlayers().values()) {
                if (Objects.equals(event.getUser().getIdLong(), player.getUserId())) {
                    candidates.get(event.getGuild().getIdLong()).put(player.getId(), Candidate.builder().player(player).build());
                    event.getHook().editOriginal(":white_check_mark: 已參選").queue();
                    return;
                }
            }
            event.getHook().editOriginal(":x: 你不是玩家").queue();
        }

        @Subcommand(description = "啟動警長參選投票")
        public void enroll(SlashCommandInteractionEvent event) {
            event.deferReply().queue();
            if (!CmdUtils.isAdmin(event)) return;
            Session session = CmdUtils.getSession(Objects.requireNonNull(event.getGuild()));
            if (session == null) return;
            candidates.put(event.getGuild().getIdLong(), new ConcurrentHashMap<>());
            allowEnroll.put(event.getGuild().getIdLong(), true);
            Audio.play(Audio.Resource.POLICE_ENROLL, event.getGuild().getVoiceChannelById(session.getCourtVoiceChannelId()));
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("參選警長").setDescription("30秒後立刻進入辯論，請加快手速!").setColor(MsgUtils.getRandomColor());
            Message message = event.getHook().editOriginalEmbeds(embed.build())
                    .setActionRow(Button.success("enrollPolice", "參選警長"))
                    .complete();
            CmdUtils.schedule(() -> Audio.play(Audio.Resource.ENROLL_10S_REMAINING, event.getGuild().getVoiceChannelById(session.getCourtVoiceChannelId())), 20000);
            CmdUtils.schedule(() -> {
                allowEnroll.put(event.getGuild().getIdLong(), false);
                if (candidates.get(event.getGuild().getIdLong()).size() == 0) {
                    candidates.remove(event.getGuild().getIdLong());
                    message.reply("無人參選，警徽撕毀").queue();
                    return;
                }
                List<String> candidateMentions = new LinkedList<>();
                for (Candidate candidate : candidates.get(event.getGuild().getIdLong()).values().stream().sorted(Candidate.getComparator()).toList()) {
                    candidateMentions.add("<@!" + candidate.getPlayer().getUserId() + ">");
                }
                if (candidates.get(event.getGuild().getIdLong()).size() == 1) {
                    message.reply("只有" + candidateMentions.get(0) + "參選，直接當選").queue();
                    Session.fetchCollection().updateOne(eq("guildId", event.getGuild().getIdLong()),
                            set("players." + candidates.get(event.getGuild().getIdLong()).get(0).getPlayer().getId() + ".police", true));
                    candidates.remove(event.getGuild().getIdLong());
                    return;
                }
                message.replyEmbeds(new EmbedBuilder().setTitle("參選警長結束")
                        .setDescription("參選的有: " + String.join("、", candidateMentions) + "\n備註:你可隨時再按一次按鈕以取消參選")
                        .setColor(MsgUtils.getRandomColor()).build()).complete();
                Speech.pollSpeech(event.getGuild(), message, candidates.get(event.getGuild().getIdLong()).values().stream().map(Candidate::getPlayer).toList(),
                        () -> startPolicePoll(session, event.getGuildChannel(), true));
            }, 30000);
            event.getHook().editOriginal(":white_check_mark:").queue();
        }

        @Subcommand(description = "啟動警長投票 (會自動開始，請只在出問題時使用)")
        public void start(SlashCommandInteractionEvent event) {
            event.deferReply().queue();
            if (!CmdUtils.isAdmin(event)) return;
            Session session = CmdUtils.getSession(Objects.requireNonNull(event.getGuild()));
            if (session == null) return;
            startPolicePoll(session, event.getGuildChannel(), true);
        }
    }

    @Data
    @Builder
    public static class Candidate {
        private Session.Player player;
        @Builder.Default
        private boolean expelPK = false;
        @Builder.Default
        private List<Long> electors = new LinkedList<>();
        @Builder.Default
        private boolean quit = false;

        public static Comparator<Candidate> getComparator() {
            return Comparator.comparingInt(o -> o.getPlayer().getId());
        }

        public static List<Candidate> getWinner(Collection<Candidate> candidates, @Nullable Session.Player police) {
            List<Candidate> winners = new LinkedList<>();
            float winningVotes = 0;
            for (Candidate candidate : candidates) {
                float votes = candidate.getVotes(police);
                if (votes <= 0) continue;
                if (votes > winningVotes) {
                    winningVotes = votes;
                    winners.clear();
                    winners.add(candidate);
                } else if (votes == winningVotes) {
                    winners.add(candidate);
                }
            }
            return winners;
        }

        public List<String> getElectorsAsMention() {
            List<String> result = new LinkedList<>();
            for (Long elector : electors) {
                result.add("<@!" + elector + ">");
            }
            return result;
        }

        public float getVotes(@Nullable Session.Player police) {
            boolean hasPolice = police != null;
            if (hasPolice) hasPolice = electors.contains(police.getUserId());
            return (float) ((electors.size() + (hasPolice ? 0.5 : 0)) * (quit ? 0 : 1));
        }
    }
}
