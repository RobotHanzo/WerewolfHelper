package dev.robothanzo.werewolf.commands;

import dev.robothanzo.jda.interactions.annotations.slash.Command;
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand;
import dev.robothanzo.jda.interactions.annotations.slash.options.Option;
import dev.robothanzo.werewolf.WerewolfApplication;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import dev.robothanzo.werewolf.utils.MsgUtils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

@Command
@Slf4j
public class Player {
    public static Map<Long, TransferPoliceSession> transferPoliceSessions = new HashMap<>(); // key is guild ID

    public static void transferPolice(Session session, Guild guild, Session.Player player,
                                      @Nullable Runnable callback) {
        if (player.isPolice()) {
            assert player.getUserId() != null;
            transferPoliceSessions.put(guild.getIdLong(), TransferPoliceSession.builder()
                    .guildId(guild.getIdLong())
                    .senderId(player.getUserId())
                    .callback(callback)
                    .build());
            EntitySelectMenu.Builder selectMenu = EntitySelectMenu
                    .create("selectNewPolice", EntitySelectMenu.SelectTarget.USER)
                    .setMinValues(1)
                    .setMaxValues(1);
            for (Session.Player p : session.fetchAlivePlayers().values()) {
                assert p.getUserId() != null;
                if (Objects.equals(p.getUserId(), player.getUserId()))
                    continue;
                User user = WerewolfApplication.jda.getUserById(p.getUserId());
                assert user != null;
                transferPoliceSessions.get(guild.getIdLong()).getPossibleRecipientIds().add(p.getUserId());
            }
            Message message = Objects
                    .requireNonNull(guild.getTextChannelById(session.getCourtTextChannelId())).sendMessageEmbeds(
                            new EmbedBuilder()
                                    .setTitle("移交警徽").setColor(MsgUtils.getRandomColor())
                                    .setDescription("請選擇要移交警徽的對象，若要撕掉警徽，請按下撕毀按鈕\n請在30秒內做出選擇，否則警徽將被自動撕毀").build())
                    .setComponents(ActionRow.of(selectMenu.build()),
                            ActionRow.of(Button.success("confirmNewPolice", "移交"),
                                    Button.danger("destroyPolice", "撕毀")))
                    .complete();
            CmdUtils.schedule(() -> {
                if (transferPoliceSessions.remove(guild.getIdLong()) != null) {
                    message.reply("警徽已自動撕毀").queue();
                }
            }, 30000);
        }
        if (callback != null)
            callback.run();
    }

    public static boolean playerDied(Session session, Member user, boolean lastWords, boolean isExpelled) { // returns
        // whether
        // the
        // action
        // succeeded
        Guild guild = Objects.requireNonNull(WerewolfApplication.jda.getGuildById(session.getGuildId()));
        Role spectatorRole = Objects.requireNonNull(guild.getRoleById(session.getSpectatorRoleId()));
        for (Map.Entry<String, Session.Player> player : new LinkedList<>(session.getPlayers().entrySet())) {
            if (Objects.equals(user.getIdLong(), player.getValue().getUserId())) {

                // Check if already fully dead
                if (!player.getValue().isAlive()) {
                    return false;
                }

                assert player.getValue().getRoles() != null;

                // Soft kill logic: Find first alive role and kill it
                List<String> roles = player.getValue().getRoles();
                List<String> deadRoles = player.getValue().getDeadRoles();
                if (deadRoles == null) {
                    deadRoles = new ArrayList<>();
                    player.getValue().setDeadRoles(deadRoles);
                }

                String killedRole = null;
                for (String role : roles) {
                    long totalCount = roles.stream().filter(r -> r.equals(role)).count();
                    long deadCount = deadRoles.stream().filter(r -> r.equals(role)).count();

                    if (deadCount < totalCount) {
                        killedRole = role;
                        deadRoles.add(role);
                        break;
                    }
                }

                // Persist the dead role update immediately to ensure consistency
                Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()),
                        set("players", session.getPlayers()));

                // Log the death
                if (killedRole != null) {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("playerId", player.getValue().getId());
                    metadata.put("playerName", player.getValue().getNickname());
                    metadata.put("killedRole", killedRole);
                    metadata.put("isExpelled", isExpelled);
                    session.addLog(dev.robothanzo.werewolf.database.documents.LogType.PLAYER_DIED,
                            player.getValue().getNickname() + " 的 " + killedRole + " 身份已死亡",
                            metadata);

                    // Send message to court channel
                    TextChannel courtChannel = guild.getTextChannelById(session.getCourtTextChannelId());
                    if (courtChannel != null) {
                        courtChannel
                                .sendMessage("**:skull: " + user.getAsMention() + " 已死亡**")
                                .queue();
                    }
                }

                // Check game ended logic with the newly killed role
                Session.Result result = session.hasEnded(killedRole);
                if (result != Session.Result.NOT_ENDED) {
                    TextChannel channel = guild.getTextChannelById(session.getSpectatorTextChannelId());
                    String judgePing = "<@&" + session.getJudgeRoleId() + "> ";
                    if (channel != null) {
                        if (result == Session.Result.WOLVES_DIED) {
                            channel.sendMessage(judgePing + "遊戲結束，**好**人獲勝，原因：" + result.getReason()).queue();
                        } else {
                            channel.sendMessage(judgePing + "遊戲結束，**狼**人獲勝，原因：" + result.getReason()).queue();
                        }
                        lastWords = false;
                    }
                }

                // Check if player is still alive (has remaining roles)
                if (player.getValue().isAlive()) {
                    // Calculate remaining roles for the message
                    List<String> remainingRoles = new ArrayList<>(player.getValue().getRoles());
                    if (player.getValue().getDeadRoles() != null) {
                        for (String deadRole : player.getValue().getDeadRoles()) {
                            remainingRoles.remove(deadRole);
                        }
                    }
                    String remainingRoleName = remainingRoles.isEmpty() ? "未知" : remainingRoles.getFirst();

                    // Not fully dead, passed out one role
                    Objects.requireNonNull(guild.getTextChannelById(player.getValue().getChannelId()))
                            .sendMessage("因為你死了，所以你的角色變成了 " + remainingRoleName).queue();
                    Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()),
                            set("players", session.getPlayers()));
                    if (lastWords) {
                        WerewolfApplication.speechService.startLastWordsSpeech(guild,
                                session.getCourtTextChannelId(),
                                player.getValue(), null);
                    }
                    return true;
                }

                // Fully dead logic
                String finalKilledRole = killedRole;
                Runnable die = () -> transferPolice(session, guild, player.getValue(), () -> {
                    var newSession = CmdUtils.getSession(guild); // We need to update the session as it may have been
                    // tampered with by transferPolice
                    if (newSession == null)
                        return;

                    // We need to fetch the updated player object from the new session to make sure
                    // we have latest police status etc.
                    // But assume player state is managed by references or we need to re-fetch.
                    // For safety, let's use the object we have but ensure police status is false if
                    // transferred.
                    // Actually transferPolice callback runs AFTER transfer.

                    if (player.getValue().isIdiot() && isExpelled) {
                        player.getValue().getDeadRoles().remove(finalKilledRole);

                        newSession.getPlayers().put(player.getKey(), player.getValue());
                        Session.fetchCollection().updateOne(eq("guildId", newSession.getGuildId()),
                                set("players", newSession.getPlayers()));
                        WerewolfApplication.gameSessionService.broadcastSessionUpdate(newSession);
                        Objects.requireNonNull(guild.getTextChannelById(newSession.getCourtTextChannelId()))
                                .sendMessage(user.getAsMention() + " 是白癡，所以他會待在場上並繼續發言").queue();
                    } else {
                        guild.modifyMemberRoles(user, spectatorRole).queue();
                        Session.fetchCollection().updateOne(eq("guildId", newSession.getGuildId()),
                                set("players", newSession.getPlayers()));
                        player.getValue().updateNickname(user);
                        WerewolfApplication.gameSessionService.broadcastSessionUpdate(newSession);
                    }
                });

                if (lastWords) {
                    WerewolfApplication.speechService.startLastWordsSpeech(guild,
                            session.getCourtTextChannelId(),
                            player.getValue(), die);
                } else {
                    die.run();
                }
                return true;
            }
        }
        guild.addRoleToMember(user, spectatorRole).queue(); // if they aren't found, they will become spectators
        user.modifyNickname("[旁觀] " + user.getEffectiveName()).queue();
        return true;
    }

    public static boolean playerRevived(Session session, Member user, String roleToRevive) {
        Guild guild = Objects.requireNonNull(WerewolfApplication.jda.getGuildById(session.getGuildId()));
        Role spectatorRole = Objects.requireNonNull(guild.getRoleById(session.getSpectatorRoleId()));

        for (Map.Entry<String, Session.Player> player : session.getPlayers().entrySet()) {
            if (Objects.equals(user.getIdLong(), player.getValue().getUserId())) {
                List<String> deadRoles = player.getValue().getDeadRoles();
                if (deadRoles == null || !deadRoles.contains(roleToRevive)) {
                    return false; // Role is not dead or invalid
                }

                // Check if player WAS fully dead before this revival
                boolean wasFullyDead = !player.getValue().isAlive();

                // Revive the role
                deadRoles.remove(roleToRevive);

                // Update session immediately
                Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()),
                        set("players", session.getPlayers()));

                // Log the revival
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("playerId", player.getValue().getId());
                metadata.put("playerName", player.getValue().getNickname());
                metadata.put("revivedRole", roleToRevive);
                session.addLog(dev.robothanzo.werewolf.database.documents.LogType.PLAYER_REVIVED,
                        player.getValue().getNickname() + " 的 " + roleToRevive + " 身份已復活",
                        metadata);

                // Handle transition from Dead -> Alive
                if (wasFullyDead) {
                    guild.removeRoleFromMember(user, spectatorRole).queue();
                }
                player.getValue().updateNickname(user);

                // Calculate remaining roles for message
                List<String> remainingRoles = new ArrayList<>(player.getValue().getRoles());
                for (String deadRole : deadRoles) {
                    remainingRoles.remove(deadRole);
                }
                String currentRoleName = remainingRoles.isEmpty() ? "未知" : remainingRoles.getFirst();

                // Restore Role Logic using Session values
                long roleId = player.getValue().getRoleId();
                if (roleId != 0) {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) {
                        guild.addRoleToMember(user, role).queue();
                    }
                }

                // Send notification
                TextChannel channel = guild.getTextChannelById(player.getValue().getChannelId());
                if (channel != null) {
                    channel.sendMessage("因為你復活了，所以你的角色變成了 " + currentRoleName).queue();
                }

                // Broadcast updates after all changes including nickname
                WerewolfApplication.gameSessionService.broadcastSessionUpdate(session);
                return true;
            }
        }
        return false;
    }

    public static void selectNewPolice(EntitySelectInteractionEvent event) {
        if (transferPoliceSessions.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
            Member target = event.getMentions().getMembers().getFirst();
            TransferPoliceSession session = transferPoliceSessions
                    .get(Objects.requireNonNull(event.getGuild()).getIdLong());
            if (!session.getPossibleRecipientIds().contains(target.getIdLong())) {
                event.reply(":x: 你不能移交警徽給這個人").setEphemeral(true).queue();
            } else {
                if (session.getSenderId() == event.getUser().getIdLong()) {
                    Session guildSession = Session.fetchCollection().find(eq("guildId", event.getGuild().getIdLong()))
                            .first();
                    if (guildSession == null)
                        return;
                    for (var player : guildSession.getPlayers().values()) {
                        if (Objects.requireNonNull(player.getUserId()) == target.getIdLong()) {
                            session.setRecipientId(player.getId());
                            event.reply(":white_check_mark: 請按下移交來完成移交動作").setEphemeral(true).queue();
                            break;
                        }
                    }
                } else {
                    event.reply(":x: 你不是原本的警長").setEphemeral(true).queue();
                }
            }
        }
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public static void confirmNewPolice(ButtonInteractionEvent event) {
        if (transferPoliceSessions.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
            TransferPoliceSession session = transferPoliceSessions
                    .get(Objects.requireNonNull(event.getGuild()).getIdLong());
            if (session.getSenderId() == event.getUser().getIdLong()) {
                if (session.getRecipientId() != null) {
                    Session.fetchCollection().updateOne(eq("guildId", event.getGuild().getIdLong()),
                            set("players." + session.getRecipientId() + ".police", true));
                    log.info("Transferred police to " + session.getRecipientId() + " in guild "
                            + event.getGuild().getIdLong());
                    transferPoliceSessions.remove(event.getGuild().getIdLong());

                    // Update Recipient Nickname
                    Session.Player recipientPlayer = Objects.requireNonNull(CmdUtils.getSession(event)).getPlayers()
                            .get(session.getRecipientId().toString());
                    recipientPlayer.setPolice(true);
                    Long recipientDiscordId = recipientPlayer.getUserId();
                    if (recipientDiscordId != null) {
                        Member recipient = event.getGuild().getMemberById(recipientDiscordId);
                        if (recipient != null) {
                            recipientPlayer.updateNickname(recipient);
                            event.reply(":white_check_mark: 警徽已移交給 " + recipient.getAsMention()).queue();
                        }
                    }

                    // Update Sender Nickname
                    Member sender = event.getGuild().getMemberById(session.getSenderId());
                    Session.fetchCollection()
                            .updateOne(eq("guildId", event.getGuild().getIdLong()),
                                    set("players."
                                            + Objects.requireNonNull(CmdUtils.getSession(event)).getPlayers().entrySet()
                                            .stream()
                                            .filter(e -> Objects.equals(e.getValue().getUserId(),
                                                    session.getSenderId()))
                                            .findFirst().get().getKey()
                                            + ".police", false));

                    if (sender != null) {
                        // We need the player object for sender to correctly regenerate name (e.g. if
                        // they are dead?)
                        // Usually transfer logic happens when dead, but could be alive transfer.
                        var senderEntry = Objects.requireNonNull(CmdUtils.getSession(event)).getPlayers().entrySet()
                                .stream().filter(e -> Objects.equals(e.getValue().getUserId(), session.getSenderId()))
                                .findFirst();
                        if (senderEntry.isPresent()) {
                            Session.Player senderPlayer = senderEntry.get().getValue();
                            senderPlayer.setPolice(false);
                            senderPlayer.updateNickname(sender);
                        }
                    }
                    if (session.getCallback() != null)
                        session.getCallback().run();
                } else {
                    event.reply(":x: 請先選擇要移交警徽的對象").setEphemeral(true).queue();
                }
            } else {
                event.reply(":x: 你不是原本的警長").setEphemeral(true).queue();
            }
        }
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public static void destroyPolice(ButtonInteractionEvent event) {
        if (transferPoliceSessions.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
            TransferPoliceSession session = transferPoliceSessions
                    .get(Objects.requireNonNull(event.getGuild()).getIdLong());
            if (session.getSenderId() == event.getUser().getIdLong()) {
                transferPoliceSessions.remove(event.getGuild().getIdLong());
                event.reply(":white_check_mark: 警徽已撕毀").setEphemeral(false).queue();
                if (session.getCallback() != null)
                    session.getCallback().run();
            } else {
                event.reply(":x: 你不是原本的警長").setEphemeral(true).queue();
            }
        }
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void changeRoleOrder(ButtonInteractionEvent event) {
        if (event.getGuild() == null)
            return;
        event.deferReply().queue();
        Session session = CmdUtils.getSession(event);
        if (session == null)
            return;

        for (Session.Player player : session.getPlayers().values()) {
            if (Objects.equals(event.getUser().getIdLong(), player.getUserId())) {
                try {
                    WerewolfApplication.playerService.switchRoleOrder(event.getGuild().getIdLong(),
                            String.valueOf(player.getId()));
                    event.getHook().editOriginal(":white_check_mark: 交換成功").queue();
                } catch (Exception e) {
                    event.getHook().editOriginal(":x: " + e.getMessage()).queue();
                }
                return;
            }
        }
        event.getHook().editOriginal(":x: 你不是玩家").queue();
    }

    @Subcommand(description = "升官為法官")
    public void judge(SlashCommandInteractionEvent event, @Option(value = "user") User user) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event))
            return;
        Session session = CmdUtils.getSession(event);
        if (session == null)
            return;
        Objects.requireNonNull(event.getGuild()).addRoleToMember(
                Objects.requireNonNull(event.getGuild().getMemberById(user.getId())),
                Objects.requireNonNull(event.getGuild().getRoleById(session.getJudgeRoleId()))).queue();
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Subcommand(description = "貶官為庶民")
    public void demote(SlashCommandInteractionEvent event, @Option(value = "user") User user) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event))
            return;
        Session session = CmdUtils.getSession(event);
        if (session == null)
            return;
        Objects.requireNonNull(event.getGuild()).removeRoleFromMember(
                Objects.requireNonNull(event.getGuild().getMemberById(user.getId())),
                Objects.requireNonNull(event.getGuild().getRoleById(session.getJudgeRoleId()))).queue();
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Subcommand(description = "使玩家成為死人/旁觀者")
    public void died(SlashCommandInteractionEvent event, @Option(value = "user", description = "死掉的使用者") User user,
                     @Option(value = "last_words", description = "是否讓他講遺言 (預設為否) (若為雙身分，只會在兩張牌都死掉的時候啟動)", optional = true) Boolean lastWords) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event))
            return;
        Session session = CmdUtils.getSession(event);
        if (session == null)
            return;
        if (lastWords == null)
            lastWords = false;
        Member member = Objects.requireNonNull(Objects.requireNonNull(event.getGuild()).getMemberById(user.getId()));

        if (playerDied(session, member, lastWords, false)) {
            event.getHook().editOriginal(":white_check_mark:").queue();
        } else {
            event.getHook().editOriginal(":x: 使用者已經死了").queue();
        }
    }

    @Subcommand(description = "指派玩家編號並傳送身分")
    public void assign(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event))
            return;
        Session session = CmdUtils.getSession(event);
        if (session == null)
            return;

        try {
            WerewolfApplication.roleService.assignRoles(event.getGuild().getIdLong(),
                    msg -> log.info("[Assign] " + msg),
                    p -> {
                    });
            event.getHook().editOriginal(":white_check_mark: 身分分配完成！").queue();
        } catch (Exception e) {
            event.getHook().editOriginal(":x: " + e.getMessage()).queue();
        }
    }

    @Subcommand(description = "列出每個玩家的身分資訊")
    public void roles(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event))
            return;
        Session session = CmdUtils.getSession(event);
        if (session == null)
            return;
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("身分列表")
                .setColor(MsgUtils.getRandomColor());
        for (String p : session.getPlayers().keySet().stream().sorted(MsgUtils.getAlphaNumComparator()).toList()) {
            Session.Player player = session.getPlayers().get(p);
            assert player.getRoles() != null;
            embedBuilder.addField(player.getNickname(),
                    String.join("、", player.getRoles()) + (player.isPolice() ? " (警長)" : "") +
                            (player.isJinBaoBao() ? " (金寶寶)" : player.isDuplicated() ? " (複製人)" : ""),
                    true);
        }
        event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();
    }

    @Subcommand(description = "強制某人成為警長 (將會清除舊的警長)")
    public void force_police(SlashCommandInteractionEvent event,
                             @Option(value = "user", description = "要強制成為警長的玩家") User user) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event))
            return;
        if (event.getGuild() == null)
            return;

        try {
            WerewolfApplication.gameActionService.setPolice(event.getGuild().getIdLong(), user.getIdLong());
            event.getHook().editOriginal(":white_check_mark:").queue();
        } catch (Exception e) {
            event.getHook().editOriginal(":x: " + e.getMessage()).queue();
        }
    }

    @Data
    @Builder
    public static class TransferPoliceSession {
        private long guildId;
        private long senderId;
        @Builder.Default
        private List<Long> possibleRecipientIds = new ArrayList<>();
        @Nullable
        private Integer recipientId; // 1 / 2 / 3..etc
        @Nullable
        private Runnable callback;
    }
}
