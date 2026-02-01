package dev.robothanzo.werewolf.service.impl;

import dev.robothanzo.werewolf.audio.Audio;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.model.SpeechOrder;
import dev.robothanzo.werewolf.model.SpeechSession;
import dev.robothanzo.werewolf.security.SessionRepository;
import dev.robothanzo.werewolf.service.DiscordService;
import dev.robothanzo.werewolf.service.GameSessionService;
import dev.robothanzo.werewolf.service.SpeechService;
import dev.robothanzo.werewolf.utils.MsgUtils;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SpeechServiceImpl implements SpeechService {

    private final SessionRepository sessionRepository;
    private final DiscordService discordService;
    private final GameSessionService gameSessionService;

    private final Map<Long, SpeechSession> speechSessions = new ConcurrentHashMap<>();
    private final Map<Long, Thread> timers = new ConcurrentHashMap<>();

    public SpeechServiceImpl(SessionRepository sessionRepository, DiscordService discordService,
                             @Lazy GameSessionService gameSessionService) {
        this.sessionRepository = sessionRepository;
        this.discordService = discordService;
        this.gameSessionService = gameSessionService;
    }

    @Override
    public SpeechSession getSpeechSession(long guildId) {
        return speechSessions.get(guildId);
    }

    @Override
    public void startSpeechPoll(Guild guild, Message enrollMessage, Collection<Session.Player> players,
                                Runnable callback) {
        var speechSession = SpeechSession.builder()
                .guildId(guild.getIdLong())
                .channelId(enrollMessage.getChannel().getIdLong())
                .session(sessionRepository.findByGuildId(guild.getIdLong()).orElse(null))
                .finishedCallback(callback)
                .build();
        speechSessions.put(guild.getIdLong(), speechSession);

        SpeechOrder order = SpeechOrder.getRandomOrder();
        List<Session.Player> shuffledPlayers = new LinkedList<>(players);
        Collections.shuffle(shuffledPlayers);
        Session.Player target = shuffledPlayers.getFirst();

        enrollMessage.replyEmbeds(new EmbedBuilder()
                .setTitle("隨機抽取投票辯論順序")
                .setDescription("抽到的順序: 玩家" + target.getId() + order.toString())
                .setColor(MsgUtils.getRandomColor()).build()).queue();

        changeOrder(guild.getIdLong(), order, players, target);
        nextSpeaker(guild.getIdLong());
    }

    @Override
    public void startLastWordsSpeech(Guild guild, long channelId, Session.Player player, Runnable callback) {
        List<Session.Player> orderList = new LinkedList<>();
        orderList.add(player);

        var speechSession = SpeechSession.builder()
                .guildId(guild.getIdLong())
                .channelId(channelId)
                .session(sessionRepository.findByGuildId(guild.getIdLong()).orElse(null))
                .order(orderList)
                .finishedCallback(callback)
                .build();
        speechSessions.put(guild.getIdLong(), speechSession);
        nextSpeaker(guild.getIdLong());
    }

    @Override
    public void setSpeechOrder(long guildId, dev.robothanzo.werewolf.model.SpeechOrder order) {
        var session = sessionRepository.findByGuildId(guildId).orElse(null);
        if (session == null)
            return;

        Session.Player target = null;
        for (Session.Player player : session.fetchAlivePlayers().values()) {
            if (player.isPolice()) {
                target = player;
                break;
            }
        }
        if (target == null && !session.fetchAlivePlayers().isEmpty()) {
            target = session.fetchAlivePlayers().values().iterator().next();
        }

        if (target != null) {
            changeOrder(guildId, order, session.fetchAlivePlayers().values(), target);
        }
    }

    @Override
    public void confirmSpeechOrder(long guildId) {
        nextSpeaker(guildId);
    }

    @Override
    public void handleOrderSelection(StringSelectInteractionEvent event) {
        event.deferReply(true).queue();
        long guildId = event.getGuild().getIdLong();
        var session = sessionRepository.findByGuildId(guildId).orElse(null);
        if (session == null)
            return;

        SpeechOrder order = SpeechOrder.fromString(event.getSelectedOptions().getFirst().getValue());
        if (!speechSessions.containsKey(guildId)) {
            event.getHook().editOriginal("法官尚未開始發言流程").queue();
            return;
        }

        Session.Player target = null;
        for (Session.Player player : session.fetchAlivePlayers().values()) {
            if (player.getUserId() != null && player.getUserId() == event.getUser().getIdLong()) {
                if (player.isPolice()) {
                    target = player;
                    break;
                } else {
                    event.getHook().editOriginal(":x: 你不是警長").queue();
                    return;
                }
            }
        }

        if (target == null) {
            event.getHook().editOriginal(":x: 你不是警長").queue();
            return;
        }

        changeOrder(guildId, order, session.fetchAlivePlayers().values(), target);
        event.getHook().editOriginal(":white_check_mark: 請按下確認以開始發言流程").queue();
        event.getMessage().editMessageEmbeds(new EmbedBuilder(event.getMessage().getEmbeds().getFirst())
                        .setDescription("警長已選擇 " + order.toEmoji().getName() + " " + order + "\n請按下確認").build())
                .queue();
        gameSessionService.broadcastUpdate(guildId);
    }

    @Override
    public void confirmOrder(ButtonInteractionEvent event) {
        event.deferReply(true).queue();
        long guildId = event.getGuild().getIdLong();
        var session = sessionRepository.findByGuildId(guildId).orElse(null);
        if (session == null)
            return;

        var speechSession = speechSessions.get(guildId);
        if (speechSession == null) {
            event.getHook().editOriginal(":x: 法官尚未開始發言流程").queue();
            return;
        }

        boolean isPolice = false;
        for (Session.Player player : session.fetchAlivePlayers().values()) {
            if (player.getUserId() != null && player.getUserId() == event.getUser().getIdLong()) {
                if (player.isPolice()) {
                    isPolice = true;
                    break;
                }
            }
        }

        if (!isPolice) {
            event.getHook().editOriginal(":x: 你不是警長").queue();
        } else {
            if (speechSession.getOrder().isEmpty()) {
                event.getHook().editOriginal(":x: 請先選取往上或往下").queue();
            } else {
                nextSpeaker(guildId);
                event.getHook().editOriginal(":white_check_mark: 確認完成").queue();
            }
        }
    }

    @Override
    public void skipSpeech(ButtonInteractionEvent event) {
        event.deferReply().queue();
        long guildId = event.getGuild().getIdLong();
        var speechSession = speechSessions.get(guildId);

        if (speechSession != null) {
            if (speechSession.getLastSpeaker() != null
                    && event.getUser().getIdLong() != speechSession.getLastSpeaker()) {
                event.getHook().setEphemeral(true).editOriginal(":x: 你不是發言者").queue();
            } else {
                event.getHook().editOriginal(":white_check_mark: 發言已跳過").queue();
                nextSpeaker(guildId);
            }
        } else {
            event.getHook().setEphemeral(true).editOriginal(":x: 法官尚未開始發言流程").queue();
        }
    }

    @Override
    public void interruptSpeech(ButtonInteractionEvent event) {
        event.deferReply(true).queue();
        long guildId = event.getGuild().getIdLong();
        var speechSession = speechSessions.get(guildId);

        if (speechSession != null) {
            if (speechSession.getLastSpeaker() != null && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                if (event.getUser().getIdLong() == speechSession.getLastSpeaker()) {
                    event.getHook().editOriginal(":x: 若要跳過發言請按左邊的跳過按鈕").queue();
                } else {
                    var session = speechSession.getSession();
                    if (event.getMember().getRoles()
                            .contains(event.getGuild().getRoleById(session.getSpectatorRoleId()))) {
                        event.getHook().editOriginal(":x: 旁觀者不得投票").queue();
                    } else {
                        if (speechSession.getInterruptVotes().contains(event.getUser().getIdLong())) {
                            speechSession.getInterruptVotes().remove(event.getUser().getIdLong());
                            event.getHook().editOriginal(":white_check_mark: 成功取消下台投票，距離該玩家下台還缺" +
                                    (session.fetchAlivePlayers().size() / 2 + 1
                                            - speechSession.getInterruptVotes().size())
                                    + "票").queue();
                        } else {
                            speechSession.getInterruptVotes().add(event.getUser().getIdLong());
                            event.getHook().editOriginal(":white_check_mark: 下台投票成功，距離該玩家下台還缺" +
                                    (session.fetchAlivePlayers().size() / 2 + 1
                                            - speechSession.getInterruptVotes().size())
                                    + "票").queue();

                            gameSessionService.broadcastSessionUpdate(session);
                            if (speechSession.getInterruptVotes().size() > (session.fetchAlivePlayers().size() / 2)) {
                                List<String> voterMentions = new LinkedList<>();
                                for (long voter : speechSession.getInterruptVotes()) {
                                    voterMentions.add("<@!" + voter + ">");
                                }
                                event.getMessage().reply("人民的法槌已強制該玩家下台，有投票的有: " + String.join("、", voterMentions))
                                        .queue();
                                nextSpeaker(guildId);
                            }
                        }
                    }
                }
            } else {
                event.getHook().editOriginal(":white_check_mark: 成功強制下台").queue();
                event.getMessage().reply("法官已強制該玩家下台").queue();
                nextSpeaker(guildId);
            }
        } else {
            event.getHook().editOriginal(":x: 法官尚未開始發言流程").queue();
        }
    }

    @Override
    public void startAutoSpeechFlow(long guildId, long channelId) {
        if (speechSessions.containsKey(guildId))
            return;

        var session = sessionRepository.findByGuildId(guildId).orElse(null);
        if (session == null)
            return;

        var speechSession = SpeechSession.builder()
                .guildId(guildId)
                .channelId(channelId)
                .session(session)
                .build();
        speechSessions.put(guildId, speechSession);

        var guild = discordService.getGuild(guildId);
        var channel = guild.getTextChannelById(channelId);

        for (Session.Player player : session.fetchAlivePlayers().values()) {
            if (player.getUserId() != null) {
                try {
                    if (session.isMuteAfterSpeech())
                        guild.getMemberById(player.getUserId()).mute(true).queue();
                } catch (Exception ignored) {
                }
            }
            if (player.isPolice()) {
                if (channel != null) {
                    channel.sendMessageEmbeds(new EmbedBuilder()
                                    .setTitle("警長請選擇發言順序")
                                    .setDescription("警長尚未選擇順序")
                                    .setColor(MsgUtils.getRandomColor()).build())
                            .setComponents(ActionRow.of(StringSelectMenu.create("selectOrder")
                                            .addOption(SpeechOrder.UP.toString(), "up", SpeechOrder.UP.toEmoji())
                                            .addOption(SpeechOrder.DOWN.toString(), "down", SpeechOrder.DOWN.toEmoji())
                                            .setPlaceholder("請警長按此選擇發言順序").build()),
                                    ActionRow.of(Button.success("confirmOrder", "確認選取")))
                            .queue();
                }
                gameSessionService.broadcastUpdate(guildId);
                return;
            }
        }

        // No police found, auto random
        List<Session.Player> shuffled = new LinkedList<>(session.fetchAlivePlayers().values());
        Collections.shuffle(shuffled);
        SpeechOrder randOrder = SpeechOrder.getRandomOrder();
        changeOrder(guildId, randOrder, session.fetchAlivePlayers().values(), shuffled.getFirst());
        if (channel != null) {
            channel.sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("找不到警長，自動抽籤發言順序")
                    .setDescription("抽到的順序: 玩家" + shuffled.getFirst().getId() + randOrder.toString())
                    .setColor(MsgUtils.getRandomColor()).build()).queue();

            for (TextChannel c : guild.getTextChannels()) {
                c.sendMessage("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯我是白天分隔線⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯").queue();
            }
        }
        nextSpeaker(guildId);
    }

    @Override
    public void startTimer(long guildId, long channelId, long voiceChannelId, int seconds) {
        var guild = discordService.getGuild(guildId);
        var textChannel = guild.getTextChannelById(channelId);
        var voiceChannel = guild.getVoiceChannelById(voiceChannelId);

        Thread thread = new Thread(() -> {
            Message message = textChannel
                    .sendMessage(seconds + "秒的計時開始，" + TimeFormat.TIME_LONG.after(Duration.ofSeconds(seconds)) + "後結束")
                    .setComponents(ActionRow.of(Button.danger("terminateTimer", "強制結束計時"))).complete();
            try {
                if (seconds > 30) {
                    Thread.sleep((seconds - 30) * 1000L);
                    if (voiceChannel != null)
                        Audio.play(Audio.Resource.TIMER_30S_REMAINING, voiceChannel);
                    Thread.sleep(30000);
                } else {
                    Thread.sleep(seconds * 1000L);
                }
                if (voiceChannel != null)
                    Audio.play(Audio.Resource.TIMER_ENDED, voiceChannel);
                message.editMessage(message.getContentRaw() + " (已結束)").queue();
                message.reply("計時結束").queue();
            } catch (InterruptedException e) {
                message.reply("計時被終止").queue();
            }
        });
        thread.start();
        timers.put(channelId, thread);
    }

    @Override
    public void stopTimer(long channelId) {
        if (timers.containsKey(channelId)) {
            timers.get(channelId).interrupt();
            timers.remove(channelId);
        } else {
            throw new RuntimeException("Timer not found");
        }
    }

    @Override
    public void interruptSession(long guildId) {
        var speechSession = speechSessions.get(guildId);
        if (speechSession != null) {
            var guild = discordService.getGuild(guildId);
            var channel = guild.getTextChannelById(speechSession.getChannelId());
            if (channel != null)
                channel.sendMessage("法官已強制終止發言流程").queue();

            speechSession.getOrder().clear();
            stopCurrentSpeaker(speechSession);
            speechSessions.remove(guildId);
        }
    }

    @Override
    public void skipToNext(long guildId) {
        var speechSession = speechSessions.get(guildId);
        if (speechSession != null) {
            var guild = discordService.getGuild(guildId);
            var channel = guild.getTextChannelById(speechSession.getChannelId());
            if (channel != null)
                channel.sendMessage("法官已強制該玩家下台").queue();
            nextSpeaker(guildId);
        }
    }

    @Override
    public void setAllMute(long guildId, boolean mute) {
        var guild = discordService.getGuild(guildId);
        if (guild == null)
            return;
        for (Member member : guild.getMembers()) {
            if (member.hasPermission(Permission.ADMINISTRATOR))
                continue;
            try {
                member.mute(mute).queue();
            } catch (Exception ignored) {
            }
        }
    }

    private void changeOrder(long guildId, SpeechOrder order, Collection<Session.Player> playersRaw,
                             Session.Player target) {
        var speechSession = speechSessions.get(guildId);
        if (speechSession == null)
            return;

        var players = new LinkedList<>(playersRaw);
        Collections.sort(players);

        List<Session.Player> prePolice = new LinkedList<>();
        Session.Player police = null;
        List<Session.Player> postPolice = new LinkedList<>();

        for (Session.Player player : players) {
            if (player.getId() == target.getId()) {
                police = player;
                continue;
            }
            if (police == null)
                prePolice.add(player);
            else
                postPolice.add(player);
        }

        List<Session.Player> orderList = new LinkedList<>();
        if (order == SpeechOrder.UP) {
            Collections.reverse(prePolice);
            orderList.addAll(prePolice);
            Collections.reverse(postPolice);
            orderList.addAll(postPolice);
        } else {
            orderList.addAll(postPolice);
            orderList.addAll(prePolice);
        }
        orderList.add(police);

        speechSession.setOrder(orderList);
        gameSessionService.broadcastUpdate(guildId);
    }

    private void nextSpeaker(long guildId) {
        var speechSession = speechSessions.get(guildId);
        if (speechSession == null)
            return;

        speechSession.getInterruptVotes().clear();
        stopCurrentSpeaker(speechSession);

        var guild = discordService.getGuild(guildId);
        var session = speechSession.getSession();

        if (speechSession.getLastSpeaker() != null) {
            Member member = guild.getMemberById(speechSession.getLastSpeaker());
            if (member != null) {
                try {
                    if (session.isMuteAfterSpeech())
                        member.mute(true).queue();
                } catch (Exception ignored) {
                }
            }
        }

        if (speechSession.getOrder().isEmpty()) {
            var channel = guild.getTextChannelById(speechSession.getChannelId());
            if (channel != null)
                channel.sendMessage("發言流程結束").queue();

            speechSessions.remove(guildId);
            gameSessionService.broadcastSessionUpdate(session);
            if (speechSession.getFinishedCallback() != null)
                speechSession.getFinishedCallback().run();
            return;
        }

        final Session.Player player = speechSession.getOrder().removeFirst();
        speechSession.setLastSpeaker(player.getUserId());
        int time = player.isPolice() ? 210 : 180;
        speechSession.setTotalSpeechTime(time);
        speechSession.setCurrentSpeechEndTime(System.currentTimeMillis() + (time * 1000L));

        gameSessionService.broadcastSessionUpdate(session);

        Thread thread = new Thread(() -> {
            if (player.getUserId() == null)
                return;
            try {
                guild.getMemberById(player.getUserId()).mute(false).queue();
            } catch (Exception ignored) {
            }

            var channel = (TextChannel) guild.getTextChannelById(speechSession.getChannelId());
            if (channel == null)
                return;

            Message message = channel.sendMessage("<@!" + player.getUserId() + "> 你有" + time + "秒可以發言\n")
                    .setComponents(ActionRow.of(
                            Button.danger("skipSpeech", "跳過 (發言者按)").withEmoji(Emoji.fromUnicode("U+23ed")),
                            Button.danger("interruptSpeech", "下台 (玩家或法官按)").withEmoji(Emoji.fromUnicode("U+1f5d1"))))
                    .complete();

            var voiceChannel = guild.getVoiceChannelById(session.getCourtVoiceChannelId());
            try {
                Thread.sleep((time - 30) * 1000L);
                if (voiceChannel != null)
                    Audio.play(Audio.Resource.TIMER_30S_REMAINING, voiceChannel);
                Thread.sleep(35000);
                if (voiceChannel != null)
                    Audio.play(Audio.Resource.TIMER_ENDED, voiceChannel);
                message.reply("計時結束").queue();
                nextSpeaker(guildId);
            } catch (InterruptedException ignored) {
                if (voiceChannel != null)
                    Audio.play(Audio.Resource.TIMER_ENDED, voiceChannel);
            } catch (Exception ignored) {
                if (voiceChannel != null)
                    Audio.play(Audio.Resource.TIMER_ENDED, voiceChannel);
                message.reply("發言中斷（可能發言者離開或發生錯誤）").queue();
                nextSpeaker(guildId);
            }
        });
        speechSession.setSpeakingThread(thread);
        thread.start();
    }

    private void stopCurrentSpeaker(SpeechSession session) {
        if (session.getSpeakingThread() != null) {
            session.getSpeakingThread().interrupt();
            session.setSpeakingThread(null);
        }
    }
}
