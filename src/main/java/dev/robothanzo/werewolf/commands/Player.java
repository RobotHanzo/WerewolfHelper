package dev.robothanzo.werewolf.commands;

import dev.robothanzo.jda.interactions.annotations.SelectMenu;
import dev.robothanzo.jda.interactions.annotations.slash.Command;
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand;
import dev.robothanzo.jda.interactions.annotations.slash.options.Option;
import dev.robothanzo.werewolf.WerewolfHelper;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import dev.robothanzo.werewolf.utils.MsgUtils;
import lombok.Builder;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

@Command
public class Player {
    public static Map<Long, TransferPoliceSession> transferPoliceSessions = new HashMap<>(); // key is guild ID

    public static void transferPolice(Session session, Guild guild, Session.Player player, @Nullable Runnable callback) {
        if (player.isPolice()) {
            assert player.getUserId() != null;
            transferPoliceSessions.put(guild.getIdLong(), TransferPoliceSession.builder()
                    .guildId(guild.getIdLong())
                    .senderId(player.getUserId())
                    .callback(callback)
                    .build());
            net.dv8tion.jda.api.interactions.components.selections.SelectMenu.Builder selectMenu = net.dv8tion.jda.api.interactions.components.selections.SelectMenu.create("selectNewPolice");
            for (Session.Player p : session.getPlayers().values()) {
                assert p.getUserId() != null;
                if (Objects.equals(p.getUserId(), player.getUserId())) continue;
                User user = WerewolfHelper.jda.getUserById(p.getUserId());
                assert user != null;
                selectMenu.addOption("玩家" + p.getId() + " (" + user.getName() + "#" + user.getDiscriminator() + ")", String.valueOf(p.getId()));
            }
            Message message = Objects.requireNonNull(guild.getTextChannelById(session.getCourtTextChannelId())).sendMessageEmbeds(
                            new EmbedBuilder()
                                    .setTitle("移交警徽").setColor(MsgUtils.getRandomColor())
                                    .setDescription("請選擇要移交警徽的對象，若要撕掉警徽，請按下撕毀按鈕\n請在30秒內做出選擇，否則警徽將被自動撕毀").build())
                    .setActionRows(ActionRow.of(selectMenu.build()), ActionRow.of(
                            Button.success("confirmNewPolice", "移交"),
                            Button.danger("destroyPolice", "撕毀")
                    ))
                    .complete();
            CmdUtils.schedule(() -> {
                if (transferPoliceSessions.remove(guild.getIdLong()) != null) {
                    message.reply("警徽已自動撕毀").queue();
                }
            }, 30000);
        }
        if (callback != null) callback.run();
    }

    public static boolean playerDied(Session session, Member user, boolean lastWords) { // returns whether the action succeeded
        Guild guild = Objects.requireNonNull(WerewolfHelper.jda.getGuildById(session.getGuildId()));
        Role spectatorRole = Objects.requireNonNull(guild.getRoleById(session.getSpectatorRoleId()));
        for (Map.Entry<String, Session.Player> player : new LinkedList<>(session.getPlayers().entrySet())) {
            if (Objects.equals(user.getIdLong(), player.getValue().getUserId())) {
                assert player.getValue().getRoles() != null;
                if (player.getValue().getRoles().size() == 0) {
                    return false;
                }
                if (player.getValue().getRoles().size() == 2) {
                    player.getValue().getRoles().remove(0);
                    Objects.requireNonNull(guild.getTextChannelById(player.getValue().getChannelId()))
                            .sendMessage("因為你死了，所以你的角色變成了 " + player.getValue().getRoles().get(0)).queue();
                    Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()), set("players", session.getPlayers()));
                    if (lastWords) {
                        Speech.lastWordsSpeech(guild, Objects.requireNonNull(guild.getTextChannelById(session.getCourtTextChannelId())), player.getValue(), null);
                    }
                }
                if (player.getValue().getRoles().size() == 1) {
                    for (Role role : user.getRoles()) {
                        guild.removeRoleFromMember(user, role).queue(v ->
                                guild.addRoleToMember(user, spectatorRole).queue());
                    }
                    if (lastWords) {
                        Speech.lastWordsSpeech(guild, Objects.requireNonNull(guild.getTextChannelById(session.getCourtTextChannelId())), player.getValue(), () -> transferPolice(session, guild, player.getValue(), () -> {
                            session.getPlayers().remove(player.getKey());
                            Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()), set("players", session.getPlayers()));
                        }));
                    } else {
                        transferPolice(session, guild, player.getValue(), () -> {
                            session.getPlayers().remove(player.getKey());
                            Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()), set("players", session.getPlayers()));
                        });
                    }
                }
                return true;
            }
        }
        guild.addRoleToMember(user, spectatorRole).queue(); // if they aren't found, they will become spectators
        return true;
    }

    @SelectMenu
    public void selectNewPolice(SelectMenuInteractionEvent event) {
        if (transferPoliceSessions.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
            TransferPoliceSession session = transferPoliceSessions.get(Objects.requireNonNull(event.getGuild()).getIdLong());
            if (session.getSenderId() == event.getUser().getIdLong()) {
                session.setRecipientId(Integer.parseInt(event.getSelectedOptions().get(0).getValue()));
                event.reply(":white_check_mark: 請按下移交來完成移交動作").setEphemeral(true).queue();
            } else {
                event.reply(":x: 你不是原本的警長").setEphemeral(true).queue();
            }
        }
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void confirmNewPolice(ButtonInteractionEvent event) {
        if (transferPoliceSessions.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
            TransferPoliceSession session = transferPoliceSessions.get(Objects.requireNonNull(event.getGuild()).getIdLong());
            if (session.getSenderId() == event.getUser().getIdLong()) {
                if (session.getRecipientId() != null) {
                    Session.fetchCollection().updateOne(eq("guildId", event.getGuild().getIdLong()), set("players." + session.getRecipientId() + ".police", true));
                    event.reply(":white_check_mark: 警徽已移交給 <@!" +
                            Objects.requireNonNull(CmdUtils.getSession(event)).getPlayers().get(session.getRecipientId().toString()).getUserId() + ">").queue();
                } else {
                    event.reply(":x: 請先選擇要移交警徽的對象").setEphemeral(true).queue();
                }
            } else {
                event.reply(":x: 你不是原本的警長").setEphemeral(true).queue();
            }
        }
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void destroyPolice(ButtonInteractionEvent event) {
        if (transferPoliceSessions.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
            TransferPoliceSession session = transferPoliceSessions.get(Objects.requireNonNull(event.getGuild()).getIdLong());
            if (session.getSenderId() == event.getUser().getIdLong()) {
                event.reply(":white_check_mark: 警徽已撕毀").setEphemeral(false).queue();
                transferPoliceSessions.remove(event.getGuild().getIdLong());
            } else {
                event.reply(":x: 你不是原本的警長").setEphemeral(true).queue();
            }
        }
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void changeRoleOrder(ButtonInteractionEvent event) {
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        for (Session.Player player : session.getPlayers().values()) {
            if (Objects.equals(event.getUser().getIdLong(), player.getUserId())) {
                assert player.getRoles() != null;
                if (player.isRolePositionLocked()) {
                    event.getHook().editOriginal(":x: 你的身分順序已被鎖定").queue();
                    return;
                }
                Collections.reverse(player.getRoles());
                event.reply(":white_check_mark: 你目前的順序: " + String.join("、", player.getRoles())).queue();
                Session.fetchCollection().updateOne(eq("guildId", Objects.requireNonNull(event.getGuild()).getIdLong()),
                        set("players", session.getPlayers()));
                return;
            }
        }
        event.reply(":x:").queue();
    }

    @Subcommand(description = "升官為法官")
    public void judge(SlashCommandInteractionEvent event, @Option(value = "user") User user) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        Objects.requireNonNull(event.getGuild()).addRoleToMember(
                Objects.requireNonNull(event.getGuild().getMemberById(user.getId())), Objects.requireNonNull(event.getGuild().getRoleById(session.getJudgeRoleId()))).queue();
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Subcommand(description = "貶官為庶民")
    public void demote(SlashCommandInteractionEvent event, @Option(value = "user") User user) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        Objects.requireNonNull(event.getGuild()).removeRoleFromMember(
                Objects.requireNonNull(event.getGuild().getMemberById(user.getId())), Objects.requireNonNull(event.getGuild().getRoleById(session.getJudgeRoleId()))).queue();
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Subcommand(description = "使玩家成為死人/旁觀者")
    public void died(SlashCommandInteractionEvent event, @Option(value = "user", description = "死掉的使用者") User user,
                     @Option(value = "last_words", description = "是否讓他講遺言 (預設為否) (若為雙身分，只會在兩張牌都死掉的時候啟動)", optional = true) Boolean lastWords) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        if (lastWords == null) lastWords = false;
        Member member = Objects.requireNonNull(Objects.requireNonNull(event.getGuild()).getMemberById(user.getId()));

        if (playerDied(session, member, lastWords)) {
            event.getHook().editOriginal(":white_check_mark:").queue();
        } else {
            event.getHook().editOriginal(":x: 使用者已經死了").queue();
        }
    }

    @Subcommand(description = "指派玩家編號並傳送身分")
    public void assign(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        List<Member> pending = new LinkedList<>();
        for (Member member : Objects.requireNonNull(event.getGuild()).getMembers()) {
            if ((!member.getUser().isBot()) &&
                    (!member.getRoles().contains(event.getGuild().getRoleById(session.getJudgeRoleId()))) &&
                    (!member.getRoles().contains(event.getGuild().getRoleById(session.getSpectatorRoleId())))) {
                pending.add(member);
            }
        }
        Collections.shuffle(pending);
        if (pending.size() != session.getPlayers().size()) {
            event.getHook().editOriginal(
                    ":x: 玩家數量不符合設定之，請確認是否已給予旁觀者應有之身分(使用`/player died`)，是則請使用`/server set players`來更改總玩家人數").queue();
            return;
        }
        if (pending.size() != (session.getRoles().size() / (session.isDoubleIdentities() ? 2 : 1))) {
            event.getHook().editOriginal(
                    ":x: 玩家身分數量不符合身分數量，請確認是否正確啟用/停用雙身分模式(使用`/server set double_identities`)，並檢查是否正確設定身分(使用`/server roles list`檢查)").queue();
            return;
        }
        List<String> roles = session.getRoles();
        Collections.shuffle(roles);
        boolean gaveJinBaoBao = false;
        for (Session.Player player : session.getPlayers().values()) {
            event.getGuild().addRoleToMember(pending.get(player.getId() - 1),
                    Objects.requireNonNull(event.getGuild().getRoleById(player.getRoleId()))).queue();
            event.getGuild().modifyNickname(pending.get(player.getId() - 1), "玩家" + player.getId()).queue();
            player.setUserId(pending.get(player.getId() - 1).getIdLong());
            List<String> rs = new LinkedList<>();
            // at least one jin bao bao in a double identities game
            boolean isJinBaoBao = false;
            rs.add(roles.get(0));
            roles.remove(0);
            if (rs.get(0).equals("平民") && !gaveJinBaoBao && session.isDoubleIdentities()) {
                rs = List.of("平民", "平民");
                roles.remove("平民");
                gaveJinBaoBao = true;
                isJinBaoBao = true;
            } else if (session.isDoubleIdentities()) {
                if (roles.get(0).equals("複製人")) {
                    roles.set(0, rs.get(0));
                }
                rs.add(roles.get(0));
                if (rs.get(0).equals("平民") && rs.get(1).equals("平民")) {
                    isJinBaoBao = true;
                }
                if (rs.get(0).contains("狼")) {
                    Collections.reverse(rs);
                }
                roles.remove(0);
            }
            player.setJinBaoBao(isJinBaoBao && session.isDoubleIdentities());
            player.setRoles(rs);
            MessageAction action = Objects.requireNonNull(event.getGuild().getTextChannelById(player.getChannelId())).sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("你抽到的身分是 (若為狼人或金寶寶請使用自己的頻道來和隊友討論及確認身分)")
                    .setDescription(String.join("、", rs) + (player.isJinBaoBao() ? " (金寶寶)" : ""))
                    .setColor(MsgUtils.getRandomColor()).build());
            if (session.isDoubleIdentities()) {
                action.setActionRow(Button.primary("changeRoleOrder", "更換身分順序 (請在收到身分後兩分鐘內使用，逾時不候)"));
                CmdUtils.schedule(() -> {
                    Session.fetchCollection().updateOne(eq("guildId", event.getGuild().getIdLong()),
                            set("players." + player.getId() + ".rolePositionLocked", true));
                    Objects.requireNonNull(event.getGuild().getTextChannelById(player.getChannelId())).sendMessage("身分順序已鎖定").queue();
                }, 120000);
            }
            action.queue();
            Session.fetchCollection().updateOne(eq("guildId", event.getGuild().getIdLong()),
                    set("players", session.getPlayers()));
            event.getHook().editOriginal(":white_check_mark:").queue();
        }
    }

    @Subcommand(description = "列出每個玩家的身分資訊")
    public void roles(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("身分列表")
                .setColor(MsgUtils.getRandomColor());
        for (String p : session.getPlayers().keySet().stream().sorted(MsgUtils.getAlphaNumComparator()).toList()) {
            Session.Player player = session.getPlayers().get(p);
            assert player.getRoles() != null;
            embedBuilder.addField("玩家" + p,
                    String.join("、", player.getRoles()) + (player.isPolice() ? " (警長)" : "") +
                            (player.isJinBaoBao() ? " (金寶寶)" : ""), true);
        }
        event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();
    }

    @Subcommand(description = "強制某人成為警長 (將會清除舊的警長)")
    public void force_police(SlashCommandInteractionEvent
                                     event, @Option(value = "user", description = "要強制成為警長的玩家") User user) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        for (Session.Player player : session.getPlayers().values()) {
            if (player.isPolice() && !Objects.equals(player.getUserId(), user.getIdLong())) {
                player.setPolice(false);
                Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()), set("players", session.getPlayers()));
            }
            if (Objects.equals(player.getUserId(), user.getIdLong())) {
                player.setPolice(true);
                Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()), set("players", session.getPlayers()));
            }
        }
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Data
    @Builder
    public static class TransferPoliceSession {
        private long guildId;
        private long senderId;
        @Nullable
        private Integer recipientId; // 1 / 2 / 3..etc
        @Nullable
        private Runnable callback;
    }
}
