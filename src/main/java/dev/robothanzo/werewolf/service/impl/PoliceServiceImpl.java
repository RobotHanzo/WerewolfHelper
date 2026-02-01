package dev.robothanzo.werewolf.service.impl;

import dev.robothanzo.werewolf.audio.Audio;
import dev.robothanzo.werewolf.commands.Poll;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.model.Candidate;
import dev.robothanzo.werewolf.model.PoliceSession;
import dev.robothanzo.werewolf.security.SessionRepository;
import dev.robothanzo.werewolf.service.DiscordService;
import dev.robothanzo.werewolf.service.GameSessionService;
import dev.robothanzo.werewolf.service.PoliceService;
import dev.robothanzo.werewolf.utils.CmdUtils;
import dev.robothanzo.werewolf.utils.MsgUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PoliceServiceImpl implements PoliceService {

    private final SessionRepository sessionRepository;
    private final DiscordService discordService;
    private final GameSessionService gameSessionService;
    private final dev.robothanzo.werewolf.service.SpeechService speechService;

    private final Map<Long, PoliceSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Map<Long, PoliceSession> getSessions() {
        return sessions;
    }

    @Override
    public void startEnrollment(Session session, GuildMessageChannel channel, Message message) {
        if (sessions.containsKey(session.getGuildId()))
            return;

        PoliceSession policeSession = PoliceSession.builder()
                .guildId(session.getGuildId())
                .channelId(channel.getIdLong())
                .session(session)
                .build();
        sessions.put(session.getGuildId(), policeSession);
        next(session.getGuildId());
    }

    @Override
    public void enrollPolice(ButtonInteractionEvent event) {
        event.deferReply(true).queue();
        Session session = CmdUtils.getSession(Objects.requireNonNull(event.getGuild()));
        if (session == null)
            return;

        PoliceSession policeSession = sessions.get(event.getGuild().getIdLong());
        if (policeSession == null) {
            event.getHook().editOriginal(":x: 無法參選，時間已到").queue();
            return;
        }

        for (Map.Entry<Integer, Candidate> candidate : new LinkedList<>(policeSession.getCandidates().entrySet())) {
            if (Objects.equals(event.getUser().getIdLong(), candidate.getValue().getPlayer().getUserId())) {
                if (policeSession.getState().canEnroll()) { // ENROLLMENT -> Remove completely
                    policeSession.getCandidates().remove(candidate.getKey());
                    event.getHook().editOriginal(":white_check_mark: 已取消參選").queue();

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("playerId", candidate.getValue().getPlayer().getId());
                    metadata.put("playerName", candidate.getValue().getPlayer().getNickname());
                    session.addLog(dev.robothanzo.werewolf.database.documents.LogType.POLICE_UNENROLLED,
                            candidate.getValue().getPlayer().getNickname() + " 已取消參選警長", metadata);
                    gameSessionService.broadcastSessionUpdate(session);

                } else if (policeSession.getState().canQuit()) { // UNENROLLMENT -> Mark quit
                    policeSession.getCandidates().get(candidate.getKey()).setQuit(true);
                    event.getHook().editOriginal(":white_check_mark: 已取消參選").queue();
                    Objects.requireNonNull(event.getGuild().getTextChannelById(session.getCourtTextChannelId()))
                            .sendMessage(event.getUser().getAsMention() + " 已取消參選").queue();

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("playerId", candidate.getValue().getPlayer().getId());
                    metadata.put("playerName", candidate.getValue().getPlayer().getNickname());
                    session.addLog(dev.robothanzo.werewolf.database.documents.LogType.POLICE_UNENROLLED,
                            candidate.getValue().getPlayer().getNickname() + " 已取消參選警長", metadata);
                    gameSessionService.broadcastSessionUpdate(session);
                } else {
                    event.getHook().editOriginal(":x: 無法取消參選，投票已開始").queue();
                }
                return;
            }
        }

        if (!policeSession.getState().canEnroll()) {
            event.getHook().editOriginal(":x: 無法參選，時間已到").queue();
            return;
        }

        for (Session.Player player : session.fetchAlivePlayers().values()) {
            if (Objects.equals(event.getUser().getIdLong(), player.getUserId())) {
                policeSession.getCandidates().put(player.getId(), Candidate.builder().player(player).build());
                event.getHook().editOriginal(":white_check_mark: 已參選").queue();

                java.util.Map<String, Object> metadata = new java.util.HashMap<>();
                metadata.put("playerId", player.getId());
                metadata.put("playerName", player.getNickname());
                session.addLog(dev.robothanzo.werewolf.database.documents.LogType.POLICE_ENROLLED,
                        player.getNickname() + " 已參選警長", metadata);

                gameSessionService.broadcastSessionUpdate(session);
                return;
            }
        }
        event.getHook().editOriginal(":x: 你不是玩家").queue();
    }

    @Override
    public void next(long guildId) {
        PoliceSession policeSession = sessions.get(guildId);
        if (policeSession == null)
            return;

        Guild guild = discordService.getJDA().getGuildById(guildId);
        if (guild == null) {
            interrupt(guildId);
            return;
        }
        var channel = guild.getTextChannelById(policeSession.getChannelId());
        if (channel == null) {
            interrupt(guildId);
            return;
        }

        switch (policeSession.getState()) {
            case NONE -> {
                policeSession.setState(PoliceSession.State.ENROLLMENT);
                policeSession.setStageEndTime(System.currentTimeMillis() + 30000);
                policeSession.getCandidates().clear();

                policeSession.getSession().addLog(
                        dev.robothanzo.werewolf.database.documents.LogType.POLICE_ENROLLMENT_STARTED,
                        "警長參選已開始", null);
                gameSessionService.broadcastSessionUpdate(policeSession.getSession());

                Audio.play(Audio.Resource.POLICE_ENROLL,
                        guild.getVoiceChannelById(policeSession.getSession().getCourtVoiceChannelId()));
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("參選警長").setDescription("30秒後立刻進入辯論，請加快手速!")
                        .setColor(MsgUtils.getRandomColor());

                channel.sendMessageEmbeds(embed.build())
                        .setComponents(ActionRow.of(Button.success("enrollPolice", "參選警長")))
                        .queue(msg -> policeSession.setMessage(msg));

                CmdUtils.schedule(() -> Audio.play(Audio.Resource.ENROLL_10S_REMAINING,
                        guild.getVoiceChannelById(policeSession.getSession().getCourtVoiceChannelId())), 20000);
                CmdUtils.schedule(() -> next(guildId), 30000);
            }
            case ENROLLMENT -> {
                if (policeSession.getCandidates().isEmpty()) {
                    if (policeSession.getMessage() != null)
                        policeSession.getMessage().reply("無人參選，警徽撕毀").queue();
                    interrupt(guildId);
                    return;
                }

                List<String> candidateMentions = new LinkedList<>();
                for (Candidate candidate : policeSession.getCandidates().values().stream()
                        .sorted(Candidate.getComparator())
                        .toList()) {
                    candidateMentions.add("<@!" + candidate.getPlayer().getUserId() + ">");
                }

                if (policeSession.getCandidates().size() == 1) {
                    if (policeSession.getMessage() != null)
                        policeSession.getMessage().reply("只有" + candidateMentions.getFirst() + "參選，直接當選").queue();
                    setPolice(policeSession.getSession(), policeSession.getCandidates().values().iterator().next(),
                            channel);
                    interrupt(guildId);
                    return;
                }

                if (policeSession.getMessage() != null) {
                    policeSession.getMessage().replyEmbeds(new EmbedBuilder().setTitle("參選警長結束")
                            .setDescription(
                                    "參選的有: " + String.join("、", candidateMentions) + "\n備註:你可隨時再按一次按鈕以取消參選")
                            .setColor(MsgUtils.getRandomColor()).build()).queue();
                }

                policeSession.setState(PoliceSession.State.SPEECH);
                gameSessionService.broadcastSessionUpdate(policeSession.getSession());

                // Start speech
                // Start speech
                speechService.startSpeechPoll(guild, policeSession.getMessage(),
                        policeSession.getCandidates().values().stream().map(Candidate::getPlayer).toList(),
                        () -> next(guildId));
            }
            case SPEECH -> {
                policeSession.setState(PoliceSession.State.UNENROLLMENT);
                policeSession.setStageEndTime(System.currentTimeMillis() + 20000);
                gameSessionService.broadcastSessionUpdate(policeSession.getSession());

                if (policeSession.getMessage() != null) {
                    policeSession.getMessage().getChannel().sendMessage("政見發表結束，參選人有20秒進行退選，20秒後將自動開始投票").queue();
                }

                CmdUtils.schedule(() -> next(guildId), 20000);
            }
            case UNENROLLMENT -> {
                policeSession.setState(PoliceSession.State.VOTING);
                policeSession.setStageEndTime(System.currentTimeMillis() + 30000);

                if (policeSession.getCandidates().values().stream().allMatch(Candidate::isQuit)) {
                    if (policeSession.getMessage() != null)
                        policeSession.getMessage().reply("所有人退選，警徽撕毀").queue();
                    interrupt(guildId);
                    return;
                }

                gameSessionService.broadcastSessionUpdate(policeSession.getSession());
                startVoting(channel, false, policeSession);
            }
            case VOTING, FINISHED -> {
                // Logic handled in startVoting callback
            }
        }
    }

    @Override
    public void interrupt(long guildId) {
        PoliceSession policeSession = sessions.remove(guildId);
        if (policeSession != null) {
            gameSessionService.broadcastSessionUpdate(policeSession.getSession());
        }
    }

    @Override
    public void forceStartVoting(long guildId) {
        PoliceSession policeSession = sessions.get(guildId);
        if (policeSession != null) {
            policeSession.setState(PoliceSession.State.UNENROLLMENT); // trick next() to go to VOTING
            next(guildId);
        }
    }

    private void startVoting(GuildMessageChannel channel, boolean allowPK, PoliceSession policeSession) {
        Audio.play(Audio.Resource.POLICE_POLL,
                channel.getGuild().getVoiceChannelById(policeSession.getSession().getCourtVoiceChannelId()));
        EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("警長投票")
                .setDescription("30秒後立刻計票，請加快手速!\n若要改票可直接按下要改成的對象\n若要改為棄票需按下原本投給的使用者")
                .setColor(MsgUtils.getRandomColor());

        List<Button> buttons = new LinkedList<>();
        for (Candidate player : policeSession.getCandidates().values().stream().sorted(Candidate.getComparator())
                .toList()) {
            if (player.isQuit())
                continue;
            Member user = channel.getGuild().getMemberById(player.getPlayer().getUserId());
            if (user != null) {
                buttons.add(Button.primary("votePolice" + player.getPlayer().getId(),
                        player.getPlayer().getNickname() + " (" + user.getUser().getName() + ")"));
            }
        }

        channel.sendMessageEmbeds(embedBuilder.build())
                .setComponents(MsgUtils.spreadButtonsAcrossActionRows(buttons).toArray(new ActionRow[0]))
                .queue();

        CmdUtils.schedule(() -> Audio.play(Audio.Resource.POLL_10S_REMAINING,
                channel.getGuild().getVoiceChannelById(policeSession.getSession().getCourtVoiceChannelId())), 20000);
        CmdUtils.schedule(() -> {
            finishVoting(channel, allowPK, policeSession);
        }, 30000);
    }

    private void finishVoting(GuildMessageChannel channel, boolean allowPK, PoliceSession policeSession) {
        List<Candidate> winners = Candidate.getWinner(policeSession.getCandidates().values(), null);
        if (winners.isEmpty()) {
            if (policeSession.getMessage() != null)
                policeSession.getMessage().reply("沒有人投票，警徽撕毀").queue();
            interrupt(policeSession.getGuildId());
            return;
        }

        if (winners.size() == 1) {
            Candidate winner = winners.getFirst();
            if (policeSession.getMessage() != null)
                policeSession.getMessage().reply("投票已結束，<@!" + winner.getPlayer().getUserId() + "> 獲勝").queue();

            EmbedBuilder resultEmbed = new EmbedBuilder().setTitle("警長投票").setColor(MsgUtils.getRandomColor())
                    .setDescription("獲勝玩家: <@!" + winner.getPlayer().getUserId() + ">");

            Map<Long, Map<Integer, Candidate>> wrapper = new HashMap<>();
            wrapper.put(channel.getGuild().getIdLong(), policeSession.getCandidates());
            Poll.sendVoteResult(policeSession.getSession(), channel, policeSession.getMessage(), resultEmbed, wrapper,
                    true);

            setPolice(policeSession.getSession(), winner, channel);
            interrupt(policeSession.getGuildId());
        } else {
            if (allowPK) {
                EmbedBuilder resultEmbed = new EmbedBuilder().setTitle("警長投票")
                        .setColor(MsgUtils.getRandomColor())
                        .setDescription("發生平票");
                Map<Long, Map<Integer, Candidate>> wrapper = new HashMap<>();
                wrapper.put(channel.getGuild().getIdLong(), policeSession.getCandidates());
                Poll.sendVoteResult(policeSession.getSession(), channel, policeSession.getMessage(), resultEmbed,
                        wrapper, true);

                handlePK(channel, winners, policeSession);
            } else {
                EmbedBuilder resultEmbed = new EmbedBuilder().setTitle("警長投票")
                        .setColor(MsgUtils.getRandomColor())
                        .setDescription("平票第二次，警徽撕毀");
                if (policeSession.getMessage() != null)
                    policeSession.getMessage().reply("平票第二次，警徽撕毀").queue();
                Map<Long, Map<Integer, Candidate>> wrapper = new HashMap<>();
                wrapper.put(channel.getGuild().getIdLong(), policeSession.getCandidates());
                Poll.sendVoteResult(policeSession.getSession(), channel, policeSession.getMessage(), resultEmbed,
                        wrapper, true);
                interrupt(policeSession.getGuildId());
            }
        }
    }

    private void handlePK(GuildMessageChannel channel, List<Candidate> winners, PoliceSession policeSession) {
        if (policeSession.getMessage() != null)
            policeSession.getMessage().reply("平票，請PK").queue();

        // Clear votes and reset candidates to only winners
        Map<Integer, Candidate> newCandidates = new ConcurrentHashMap<>();
        for (Candidate winner : winners) {
            winner.getElectors().clear();
            newCandidates.put(winner.getPlayer().getId(), winner);
        }
        policeSession.setCandidates(newCandidates);

        speechService.startSpeechPoll(channel.getGuild(), policeSession.getMessage(),
                newCandidates.values().stream().map(Candidate::getPlayer).toList(),
                () -> {
                    policeSession.setState(PoliceSession.State.VOTING);
                    policeSession.setStageEndTime(System.currentTimeMillis() + 30000);
                    gameSessionService.broadcastSessionUpdate(policeSession.getSession());
                    startVoting(channel, false, policeSession);
                });
    }

    private void setPolice(Session session, Candidate winner, GuildMessageChannel channel) {
        Member member = channel.getGuild()
                .getMemberById(Objects.requireNonNull(winner.getPlayer().getUserId()));
        if (member != null)
            member.modifyNickname(member.getEffectiveName() + " [警長]").queue();

        Session.Player p = session.getPlayers().get(String.valueOf(winner.getPlayer().getId()));
        if (p != null) {
            p.setPolice(true);
        }
        sessionRepository.save(session);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("playerId", winner.getPlayer().getId());
        metadata.put("playerName", winner.getPlayer().getNickname());
        session.addLog(dev.robothanzo.werewolf.database.documents.LogType.POLICE_ELECTED,
                winner.getPlayer().getNickname() + " 當選警長", metadata);

        gameSessionService.broadcastSessionUpdate(session);
    }
}
