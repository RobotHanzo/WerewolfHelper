package dev.robothanzo.werewolf.commands;

import com.mongodb.client.model.Filters;
import dev.robothanzo.jda.interactions.annotations.slash.Command;
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand;
import dev.robothanzo.jda.interactions.annotations.slash.options.Option;
import dev.robothanzo.werewolf.WerewolfHelper;
import dev.robothanzo.werewolf.audio.Audio;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import dev.robothanzo.werewolf.utils.MsgUtils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;

@Slf4j
@Command
public class Speech {
    public static Map<Long, SpeechSession> speechSessions = new HashMap<>();
    public static Map<Long, Thread> timers = new HashMap<>();

    public static void pollSpeech(Guild guild, Message enrollMessage, Collection<Session.Player> players,
                                  @Nullable Runnable callback) {
        speechSessions.put(guild.getIdLong(), SpeechSession.builder()
                .guildId(guild.getIdLong())
                .channelId(enrollMessage.getChannel().getIdLong())
                .session(Session.fetchCollection().find(Filters.eq("guildId", guild.getIdLong())).first())
                .finishedCallback(callback)
                .build());
        Order order = Order.getRandomOrder();
        List<Session.Player> shuffledPlayers = new LinkedList<>(players);
        Collections.shuffle(shuffledPlayers);
        Session.Player target = shuffledPlayers.getFirst();
        enrollMessage.replyEmbeds(new EmbedBuilder().setTitle("隨機抽取投票辯論順序")
                .setDescription("抽到的順序: 玩家" + shuffledPlayers.getFirst().getId() + order.toString())
                .setColor(MsgUtils.getRandomColor()).build()).queue();
        changeOrder(guild, order, players, target);
        speechSessions.get(guild.getIdLong()).next();
    }

    public static void lastWordsSpeech(Guild guild, Channel channel, Session.Player player, @Nullable Runnable callback) {
        List<Session.Player> order = new LinkedList<>(); // must use this to make it modifiable
        order.add(player);
        speechSessions.put(guild.getIdLong(), SpeechSession.builder()
                .guildId(guild.getIdLong())
                .channelId(channel.getIdLong())
                .session(Session.fetchCollection().find(Filters.eq("guildId", guild.getIdLong())).first())
                .order(order)
                .finishedCallback(callback)
                .build());
        speechSessions.get(guild.getIdLong()).next();
    }

    public static void changeOrder(Guild guild, Order order, Collection<Session.Player> playersRaw, Session.Player target) {
        var players = new LinkedList<>(playersRaw);
        Collections.sort(players);
        List<Session.Player> prePolice = new LinkedList<>(); // 1 2 3
        Session.Player police = null; // 4
        List<Session.Player> postPolice = new LinkedList<>(); // 5 6 7
        for (Session.Player player : players) {
            if (player.getId() == target.getId()) {
                police = player;
                continue;
            }
            if (police == null) {
                prePolice.add(player);
            } else {
                postPolice.add(player);
            }
        }
        List<Session.Player> orderList = new LinkedList<>();
        if (order == Order.UP) {
            Collections.reverse(prePolice);
            orderList.addAll(prePolice);
            Collections.reverse(postPolice);
            orderList.addAll(postPolice);
            orderList.add(police);
        } else {
            orderList.addAll(postPolice);
            orderList.addAll(prePolice);
            orderList.add(police);
        }
        speechSessions.get(guild.getIdLong()).setOrder(orderList);
    }

    public static void terminateTimer(ButtonInteractionEvent event) {
        event.deferReply(true).queue();
        if (timers.containsKey(event.getChannel().getIdLong()) && CmdUtils.isAdmin(event)) {
            timers.get(event.getChannel().getIdLong()).interrupt();
            event.getHook().editOriginal(":white_check_mark:").queue();
        } else {
            event.getHook().editOriginal(":x:").queue();
        }
    }

    @dev.robothanzo.jda.interactions.annotations.select.StringSelectMenu
    public void selectOrder(StringSelectInteractionEvent event) {
        event.deferReply(true).queue();
        Session session = CmdUtils.getSession(Objects.requireNonNull(event.getGuild()));
        Order order = Order.valueOf(event.getSelectedOptions().getFirst().getValue().toUpperCase(Locale.ROOT));
        if (session == null) return;
        if (!speechSessions.containsKey(event.getGuild().getIdLong())) {
            event.getHook().editOriginal("法官尚未開始發言流程").queue();
            return;
        }
        Session.Player target = null;
        for (Session.Player player : session.fetchAlivePlayers().values()) { // selectOrder
            assert player.getUserId() != null;
            if (player.getUserId() == event.getUser().getIdLong()) {
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
        changeOrder(event.getGuild(), order, session.fetchAlivePlayers().values(), target);
        event.getHook().editOriginal(":white_check_mark: 請按下確認以開始發言流程").queue();
        event.getMessage().editMessageEmbeds(new EmbedBuilder(event.getInteraction().getMessage().getEmbeds().getFirst())
                .setDescription("警長已選擇 " + order.toEmoji().getName() + " " + order + "\n請按下確認").build()).queue();
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void confirmOrder(ButtonInteractionEvent event) {
        event.deferReply(true).queue();
        Session session = CmdUtils.getSession(Objects.requireNonNull(event.getGuild()));
        if (session == null) return;
        if (!speechSessions.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
            event.getHook().editOriginal(":x: 法官尚未開始發言流程").queue();
            return;
        }
        SpeechSession speechSession = speechSessions.get(Objects.requireNonNull(event.getGuild()).getIdLong());
        boolean check = false;
        for (Session.Player player : session.fetchAlivePlayers().values()) {
            assert player.getUserId() != null;
            if (player.getUserId() == event.getUser().getIdLong()) {
                if (player.isPolice()) {
                    check = true;
                    break;
                } else {
                    event.getHook().editOriginal(":x: 你不是警長").queue();
                    return;
                }
            }
        }
        if (!check) {
            event.getHook().editOriginal(":x: 你不是警長").queue();
        } else {
            if (speechSession.getOrder().isEmpty()) {
                event.getHook().editOriginal(":x: 請先選取往上或往下").queue();
            } else {
                speechSession.next();
                event.getHook().editOriginal(":white_check_mark: 確認完成").queue();
            }
        }
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void skipSpeech(ButtonInteractionEvent event) {
        event.deferReply().queue();
        if (event.getGuild() != null && speechSessions.containsKey(event.getGuild().getIdLong())) {
            SpeechSession session = speechSessions.get(event.getGuild().getIdLong());
            if (session.getLastSpeaker() != null && event.getUser().getIdLong() != session.getLastSpeaker()) {
                event.getHook().setEphemeral(true).editOriginal(":x: 你不是發言者").queue();
            } else {
                event.getHook().editOriginal(":white_check_mark: 發言已跳過").queue();
                session.next();
            }
        } else {
            event.getHook().setEphemeral(true).editOriginal(":x: 法官尚未開始發言流程").queue();
        }
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void interruptSpeech(ButtonInteractionEvent event) {
        event.deferReply(true).queue();
        if (event.getGuild() != null && speechSessions.containsKey(event.getGuild().getIdLong())) {
            SpeechSession session = speechSessions.get(event.getGuild().getIdLong());
            if (session.getLastSpeaker() != null && !Objects.requireNonNull(event.getMember()).hasPermission(Permission.ADMINISTRATOR)) {
                if (event.getUser().getIdLong() == session.getLastSpeaker()) {
                    event.getHook().editOriginal(":x: 若要跳過發言請按左邊的跳過按鈕").queue();
                } else {
                    Session gameSession = Objects.requireNonNull(CmdUtils.getSession(event));
                    if (event.getMember().getRoles().contains(event.getGuild().getRoleById(gameSession.getSpectatorRoleId()))) {
                        event.getHook().editOriginal(":x: 旁觀者不得投票").queue();
                    } else {
                        if (session.getInterruptVotes().contains(event.getUser().getIdLong())) {
                            event.getHook().editOriginal(":white_check_mark: 成功取消下台投票，距離該玩家下台還缺" +
                                    (gameSession.fetchAlivePlayers().size() / 2 - session.getInterruptVotes().size()) + "票").queue();
                        } else {
                            event.getHook().editOriginal(":white_check_mark: 下台投票成功，距離該玩家下台還缺" +
                                    (gameSession.fetchAlivePlayers().size() / 2 - session.getInterruptVotes().size()) + "票").queue();
                            session.getInterruptVotes().add(event.getUser().getIdLong());
                            WerewolfHelper.webServer.broadcastSessionUpdate(gameSession);
                            if (session.getInterruptVotes().size() > (gameSession.fetchAlivePlayers().size() / 2)) {
                                List<String> voterMentions = new LinkedList<>();
                                for (long voter : session.getInterruptVotes()) {
                                    voterMentions.add("<@!" + voter + ">");
                                }
                                event.getMessage().reply("人民的法槌已強制該玩家下台，有投票的有: " + String.join("、", voterMentions)).queue();
                                session.next();
                            }
                        }
                    }
                }
            } else {
                event.getHook().editOriginal(":white_check_mark: 成功強制下台").queue();
                event.getMessage().reply("法官已強制該玩家下台").queue();
                session.next();
            }
        } else {
            event.getHook().editOriginal(":x: 法官尚未開始發言流程").queue();
        }
    }

    @Subcommand(description = "開始新的計時 (不是自動發言流程)")
    public void start(SlashCommandInteractionEvent event, @Option(value = "time", description = "計時時間(m為分鐘s為秒數，例: 10m、10s、1m30s)") Duration time) {
        if (!CmdUtils.isAdmin(event)) return;
        event.reply(":white_check_mark:").setEphemeral(true).queue();
        startTimer(event.getGuild(), event.getChannel().asTextChannel(),
                Objects.requireNonNull(Objects.requireNonNull(event.getMember()).getVoiceState()).getChannel(),
                (int) time.getSeconds());
    }

    public static void startTimer(Guild guild, TextChannel textChannel, AudioChannel voiceChannel, int seconds) {
        Thread thread = new Thread(() -> {
            Message message = textChannel.sendMessage(seconds + "秒的計時開始，" + TimeFormat.TIME_LONG.after(Duration.ofSeconds(seconds)) + "後結束")
                    .setComponents(ActionRow.of(Button.danger("terminateTimer", "強制結束計時"))).complete();
            try {
                if (seconds > 30) {
                    Thread.sleep((seconds - 30) * 1000L);
                    try {
                        if (voiceChannel != null) Audio.play(Audio.Resource.TIMER_30S_REMAINING, voiceChannel);
                    } catch (Exception ignored) {
                    }
                    Thread.sleep(30000);
                } else {
                    Thread.sleep(seconds * 1000L);
                }
                try {
                    if (voiceChannel != null) Audio.play(Audio.Resource.TIMER_ENDED, voiceChannel);
                } catch (Exception ignored) {
                }
                message.editMessage(message.getContentRaw() + " (已結束)").queue();
                message.reply("計時結束").queue();
            } catch (InterruptedException e) {
                message.reply("計時被終止").queue();
            }
        });
        thread.start();
        timers.put(textChannel.getIdLong(), thread);
    }

    @Subcommand(description = "開始自動發言流程")
    public void auto(SlashCommandInteractionEvent event) {
        event.deferReply(false).queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        if (speechSessions.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
            event.getHook().editOriginal("已經在發言流程中，請先終止上一個流程再繼續").queue();
            return;
        }
        startAutoSpeech(event.getGuild(), event.getChannel().asTextChannel(), session);
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    public static void startAutoSpeech(Guild guild, TextChannel channel, Session session) {
        if (speechSessions.containsKey(guild.getIdLong())) return;
        speechSessions.put(guild.getIdLong(), SpeechSession.builder()
                .guildId(guild.getIdLong())
                .channelId(channel.getIdLong())
                .session(session)
                .build());

        for (Session.Player player : session.fetchAlivePlayers().values()) {
            assert player.getUserId() != null;
            try {
                if (session.isMuteAfterSpeech())
                    Objects.requireNonNull(guild.getMemberById(player.getUserId())).mute(true).queue();
            } catch (Exception ignored) {
            }
            if (player.isPolice()) {
                channel.sendMessageEmbeds(new EmbedBuilder().setTitle("警長請選擇發言順序")
                                .setDescription("警長尚未選擇順序")
                                .setColor(MsgUtils.getRandomColor()).build())
                        .setComponents(ActionRow.of(StringSelectMenu.create("selectOrder")
                                .addOption(Order.UP.toString(), "up", Order.UP.toEmoji())
                                .addOption(Order.DOWN.toString(), "down", Order.DOWN.toEmoji())
                                .setPlaceholder("請警長按此選擇發言順序").build()
                        ), ActionRow.of(Button.success("confirmOrder", "確認選取"))).queue();
                return;
            }
        }

        List<Session.Player> shuffledPlayers = new LinkedList<>(session.fetchAlivePlayers().values());
        Collections.shuffle(shuffledPlayers);
        Order order = Order.getRandomOrder();
        changeOrder(guild, order, session.fetchAlivePlayers().values(), shuffledPlayers.getFirst());
        channel.sendMessageEmbeds(new EmbedBuilder().setTitle("找不到警長，自動抽籤發言順序")
                .setDescription("抽到的順序: 玩家" + shuffledPlayers.getFirst().getId() + order.toString())
                .setColor(MsgUtils.getRandomColor()).build()).queue();
        speechSessions.get(guild.getIdLong()).next();

        for (TextChannel c : guild.getTextChannels()) {
            c.sendMessage("⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯我是白天分隔線⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯⎯").queue();
        }
    }

    @Subcommand(description = "強制終止自動發言流程 (不是終止目前使用者發言) (通常用在狼自爆的時候)")
    public void interrupt(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        if (!speechSessions.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
            event.getHook().editOriginal("不在發言流程中").queue();
        } else {
            interrupt(event.getGuild().getIdLong());
            event.getHook().editOriginal(":white_check_mark:").queue();
        }
    }

    public static void interrupt(long guildId) {
        if (speechSessions.containsKey(guildId)) {
            SpeechSession speechSession = speechSessions.get(guildId);
            Guild guild = WerewolfHelper.jda.getGuildById(guildId);
            if (guild != null) {
                TextChannel channel = guild.getTextChannelById(speechSession.getChannelId());
                if (channel != null) {
                    channel.sendMessage("法官已強制終止發言流程").queue();
                }
            }
            speechSession.getOrder().clear();
            speechSession.interrupt();
        }
    }

    public static void skip(long guildId) {
        if (speechSessions.containsKey(guildId)) {
            SpeechSession speechSession = speechSessions.get(guildId);
            Guild guild = WerewolfHelper.jda.getGuildById(guildId);
            if (guild != null) {
                TextChannel channel = guild.getTextChannelById(speechSession.getChannelId());
                if (channel != null) {
                    channel.sendMessage("法官已強制該玩家下台").queue();
                }
            }
            speechSession.next();
        }
    }

    @Subcommand(description = "解除所有人的靜音")
    public void unmute_all(SlashCommandInteractionEvent event) {
        if (!CmdUtils.isAdmin(event)) return;
        unmuteAll(event.getGuild());
        event.reply(":white_check_mark:").queue();
    }

    public static void unmuteAll(Guild guild) {
        for (Member member : guild.getMembers()) {
            try {
                member.mute(false).queue();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    @Subcommand(description = "靜音所有人")
    public void mute_all(SlashCommandInteractionEvent event) {
        if (!CmdUtils.isAdmin(event)) return;
        muteAll(event.getGuild());
        event.reply(":white_check_mark:").queue();
    }

    public static void muteAll(Guild guild) {
        for (Member member : guild.getMembers()) {
            try {
                if (member.getPermissions().contains(Permission.ADMINISTRATOR)) continue;
                member.mute(true).queue();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public enum Order {
        UP, DOWN;

        public static Order getRandomOrder() {
            return Order.values()[(int) (Math.random() * Order.values().length)];
        }

        public String toString() {
            if (UP.equals(this)) return "往上";
            else return "往下";
        }

        public Emoji toEmoji() {
            if (UP.equals(this)) return Emoji.fromUnicode("U+2b06");
            else return Emoji.fromUnicode("U+2b07");
        }
    }

    @Data
    @Builder
    public static class SpeechSession {
        private long guildId;
        private long channelId;
        private Session session;
        @Builder.Default
        private List<Long> interruptVotes = new LinkedList<>();
        @Builder.Default
        private List<Session.Player> order = new LinkedList<>();
        @Nullable
        private Thread speakingThread;
        @Nullable
        private Long lastSpeaker;
        @Nullable
        private Runnable finishedCallback;
        @Builder.Default
        private long currentSpeechEndTime = 0;
        @Builder.Default
        private int totalSpeechTime = 0;

        public void interrupt() {
            if (speakingThread != null) {
                speakingThread.interrupt();
            }
            speechSessions.remove(guildId);
            WerewolfHelper.webServer.broadcastSessionUpdate(session);
        }

        public void next() {
            interruptVotes.clear();
            if (speakingThread != null) {
                speakingThread.interrupt();
            }
            Guild guild = Objects.requireNonNull(WerewolfHelper.jda.getGuildById(guildId));
            if (lastSpeaker != null) {
                Member member = guild.getMemberById(lastSpeaker);
                if (member == null) {
                    guild.retrieveMemberById(lastSpeaker).queue(m -> {
                        try {
                            if (session.isMuteAfterSpeech())
                                m.mute(true).queue();
                        } catch (IllegalStateException ignored) {
                        }
                    });
                } else {
                    try {
                        if (session.isMuteAfterSpeech())
                            member.mute(true).queue();
                    } catch (IllegalStateException ignored) {
                    }
                }
            }
            if (order.isEmpty()) {
                Objects.requireNonNull(guild.getTextChannelById(channelId)).sendMessage("發言流程結束").queue();
                interrupt();
                if (finishedCallback != null) finishedCallback.run();
                return;
            }
            final Session.Player player = order.getFirst();
            lastSpeaker = player.getUserId();
            int time = player.isPolice() ? 210 : 180;
            totalSpeechTime = time;
            currentSpeechEndTime = System.currentTimeMillis() + (time * 1000L);
            order.removeFirst();
            WerewolfHelper.webServer.broadcastSessionUpdate(session);

            speakingThread = new Thread(() -> {
                assert lastSpeaker != null;
                try {
                    Objects.requireNonNull(guild.getMemberById(lastSpeaker)).mute(false).queue();
                } catch (IllegalStateException ignored) {
                }
                Message message = Objects.requireNonNull(guild.getTextChannelById(channelId))
                        .sendMessage("<@!" + player.getUserId() + "> 你有" + time + "秒可以發言\n")
                        .setComponents(ActionRow.of(
                                Button.danger("skipSpeech", "跳過 (發言者按)").withEmoji(Emoji.fromUnicode("U+23ed")),
                                Button.danger("interruptSpeech", "下台 (玩家或法官按)").withEmoji(Emoji.fromUnicode("U+1f5d1"))
                        )).complete();
                AudioChannel channel = guild.getVoiceChannelById(session.getCourtVoiceChannelId());
                try {
                    Thread.sleep((time - 30) * 1000L);
                    Audio.play(Audio.Resource.TIMER_30S_REMAINING, channel);
                    Thread.sleep(35000); // 5 extra seconds to allocate space for latency and notification sounds
                    Audio.play(Audio.Resource.TIMER_ENDED, channel);
                    message.reply("計時結束" + (order.isEmpty() ? "，下面一位" : "")).queue();
                    next();
                } catch (InterruptedException ignored) {
                    Audio.play(Audio.Resource.TIMER_ENDED, channel);
//                    message.reply("計時被終止" + (order.size() == 0 ? "，下面一位" : "")).queue();
                } catch (NullPointerException ignored) {
                    Audio.play(Audio.Resource.TIMER_ENDED, channel);
                    message.reply("發言者已離開語音或機器人出錯" + (order.isEmpty() ? "，下面一位" : "")).queue();
                    next();
                }
            });
            speakingThread.start();
        }
    }
}
