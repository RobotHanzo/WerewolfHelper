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
import net.dv8tion.jda.api.entities.TextChannel;
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
import java.util.Random;

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
                selectMenu.addOption("??????" + p.getId() + " (" + user.getName() + "#" + user.getDiscriminator() + ")", String.valueOf(p.getId()));
            }
            Message message = Objects.requireNonNull(guild.getTextChannelById(session.getCourtTextChannelId())).sendMessageEmbeds(
                            new EmbedBuilder()
                                    .setTitle("????????????").setColor(MsgUtils.getRandomColor())
                                    .setDescription("??????????????????????????????????????????????????????????????????????????????\n??????30???????????????????????????????????????????????????").build())
                    .setActionRows(ActionRow.of(selectMenu.build()), ActionRow.of(
                            Button.success("confirmNewPolice", "??????"),
                            Button.danger("destroyPolice", "??????")
                    ))
                    .complete();
            CmdUtils.schedule(() -> {
                if (transferPoliceSessions.remove(guild.getIdLong()) != null) {
                    message.reply("?????????????????????").queue();
                }
            }, 30000);
        }
        if (callback != null) callback.run();
    }

    public static boolean playerDied(Session session, Member user, boolean lastWords, boolean isExpelled) { // returns whether the action succeeded
        Guild guild = Objects.requireNonNull(WerewolfHelper.jda.getGuildById(session.getGuildId()));
        Role spectatorRole = Objects.requireNonNull(guild.getRoleById(session.getSpectatorRoleId()));
        for (Map.Entry<String, Session.Player> player : new LinkedList<>(session.getPlayers().entrySet())) {
            if (Objects.equals(user.getIdLong(), player.getValue().getUserId())) {
                assert player.getValue().getRoles() != null;
                if (player.getValue().getRoles().size() == 0) {
                    return false;
                }
                Session.Result result = session.hasEnded(player.getValue().getRoles().get(0));
                if (result != Session.Result.NOT_ENDED) {
                    TextChannel channel = guild.getTextChannelById(session.getSpectatorTextChannelId());
                    String judgePing = "<@&" + session.getJudgeRoleId() + "> ";
                    if (channel!=null) {
                        if (result == Session.Result.WOLVES_DIED) {
                            channel.sendMessage(judgePing + "???????????????**???**?????????????????????" + result.getReason()).queue();
                        } else {
                            channel.sendMessage(judgePing + "???????????????**???**?????????????????????" + result.getReason()).queue();
                        }
                        lastWords = false;
                    }
                }
                if (player.getValue().getRoles().size() == 2) {
                    player.getValue().getRoles().remove(0);
                    Objects.requireNonNull(guild.getTextChannelById(player.getValue().getChannelId()))
                            .sendMessage("????????????????????????????????????????????? " + player.getValue().getRoles().get(0)).queue();
                    Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()), set("players", session.getPlayers()));
                    if (lastWords) {
                        Speech.lastWordsSpeech(guild, Objects.requireNonNull(guild.getTextChannelById(session.getCourtTextChannelId())), player.getValue(), null);
                    }
                    return true;
                }
                if (player.getValue().getRoles().size() == 1) {
                    Runnable die = () -> transferPolice(session, guild, player.getValue(), () -> {
                        if (player.getValue().isIdiot()&&isExpelled) {
                            player.getValue().getRoles().remove(0);
                            session.getPlayers().put(player.getKey(), player.getValue());
                            Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()), set("players", session.getPlayers()));
                            Objects.requireNonNull(guild.getTextChannelById(session.getCourtTextChannelId())).sendMessage(user.getAsMention() + " ???????????????????????????????????????????????????").queue();
                        } else {
                            for (Role role : user.getRoles()) {
                                guild.removeRoleFromMember(user, role).queue(v ->
                                        guild.addRoleToMember(user, spectatorRole).queue());
                            }
                            session.getPlayers().remove(player.getKey());
                            Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()), set("players", session.getPlayers()));
                        }
                    });
                    if (lastWords) {
                        Speech.lastWordsSpeech(guild, Objects.requireNonNull(guild.getTextChannelById(session.getCourtTextChannelId())), player.getValue(), die);
                    } else {
                        die.run();
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
                event.reply(":white_check_mark: ????????????????????????????????????").setEphemeral(true).queue();
            } else {
                event.reply(":x: ????????????????????????").setEphemeral(true).queue();
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
                    transferPoliceSessions.remove(event.getGuild().getIdLong());
                    event.reply(":white_check_mark: ?????????????????? <@!" +
                            Objects.requireNonNull(CmdUtils.getSession(event)).getPlayers().get(session.getRecipientId().toString()).getUserId() + ">").queue();
                } else {
                    event.reply(":x: ????????????????????????????????????").setEphemeral(true).queue();
                }
            } else {
                event.reply(":x: ????????????????????????").setEphemeral(true).queue();
            }
        }
    }

    @dev.robothanzo.jda.interactions.annotations.Button
    public void destroyPolice(ButtonInteractionEvent event) {
        if (transferPoliceSessions.containsKey(Objects.requireNonNull(event.getGuild()).getIdLong())) {
            TransferPoliceSession session = transferPoliceSessions.get(Objects.requireNonNull(event.getGuild()).getIdLong());
            if (session.getSenderId() == event.getUser().getIdLong()) {
                transferPoliceSessions.remove(event.getGuild().getIdLong());
                event.reply(":white_check_mark: ???????????????").setEphemeral(false).queue();
            } else {
                event.reply(":x: ????????????????????????").setEphemeral(true).queue();
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
                    event.getHook().editOriginal(":x: ??????????????????????????????").queue();
                    return;
                }
                Collections.reverse(player.getRoles());
                event.reply(":white_check_mark: ??????????????????: " + String.join("???", player.getRoles())).queue();
                Session.fetchCollection().updateOne(eq("guildId", Objects.requireNonNull(event.getGuild()).getIdLong()),
                        set("players", session.getPlayers()));
                return;
            }
        }
        event.reply(":x:").queue();
    }

    @Subcommand(description = "???????????????")
    public void judge(SlashCommandInteractionEvent event, @Option(value = "user") User user) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        Objects.requireNonNull(event.getGuild()).addRoleToMember(
                Objects.requireNonNull(event.getGuild().getMemberById(user.getId())), Objects.requireNonNull(event.getGuild().getRoleById(session.getJudgeRoleId()))).queue();
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Subcommand(description = "???????????????")
    public void demote(SlashCommandInteractionEvent event, @Option(value = "user") User user) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        Objects.requireNonNull(event.getGuild()).removeRoleFromMember(
                Objects.requireNonNull(event.getGuild().getMemberById(user.getId())), Objects.requireNonNull(event.getGuild().getRoleById(session.getJudgeRoleId()))).queue();
        event.getHook().editOriginal(":white_check_mark:").queue();
    }

    @Subcommand(description = "?????????????????????/?????????")
    public void died(SlashCommandInteractionEvent event, @Option(value = "user", description = "??????????????????") User user,
                     @Option(value = "last_words", description = "????????????????????? (????????????) (????????????????????????????????????????????????????????????)", optional = true) Boolean lastWords) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        if (lastWords == null) lastWords = false;
        Member member = Objects.requireNonNull(Objects.requireNonNull(event.getGuild()).getMemberById(user.getId()));

        if (playerDied(session, member, lastWords, false)) {
            event.getHook().editOriginal(":white_check_mark:").queue();
        } else {
            event.getHook().editOriginal(":x: ?????????????????????").queue();
        }
    }

    @Subcommand(description = "?????????????????????????????????")
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
        Collections.shuffle(pending, new Random());
        if (pending.size() != session.getPlayers().size()) {
            event.getHook().editOriginal(
                    ":x: ?????????????????????????????????????????????????????????????????????????????????(??????`/player died`)??????????????????`/server set players`????????????????????????").queue();
            return;
        }
        if (pending.size() != (session.getRoles().size() / (session.isDoubleIdentities() ? 2 : 1))) {
            event.getHook().editOriginal(
                    ":x: ?????????????????????????????????????????????????????????????????????/?????????????????????(??????`/server set double_identities`)????????????????????????????????????(??????`/server roles list`??????)").queue();
            return;
        }
        List<String> roles = session.getRoles();
        Collections.shuffle(roles);
        boolean gaveJinBaoBao = false;
        for (Session.Player player : session.getPlayers().values()) {
            event.getGuild().addRoleToMember(pending.get(player.getId() - 1),
                    Objects.requireNonNull(event.getGuild().getRoleById(player.getRoleId()))).queue();
            event.getGuild().modifyNickname(pending.get(player.getId() - 1), "??????" + player.getId()).queue();
            player.setUserId(pending.get(player.getId() - 1).getIdLong());
            List<String> rs = new LinkedList<>();
            // at least one jin bao bao in a double identities game
            boolean isJinBaoBao = false;
            rs.add(roles.remove(0));
            if (rs.get(0).equals("??????")) {
                player.setIdiot(true);
            }
            if (rs.get(0).equals("??????") && !gaveJinBaoBao && session.isDoubleIdentities()) {
                rs = List.of("??????", "??????");
                roles.remove("??????");
                gaveJinBaoBao = true;
                isJinBaoBao = true;
            } else if (session.isDoubleIdentities()) {
                rs.add(roles.get(0));
                if (rs.contains("?????????")) {
                    player.setDuplicated(true);
                    if (rs.get(0).equals("?????????")) {
                        rs.set(0, rs.get(1));
                    } else {
                        rs.set(1, rs.get(0));
                    }
                }
                if (rs.get(0).equals("??????") && rs.get(1).equals("??????")) {
                    isJinBaoBao = true;
                }
                if (rs.get(0).contains("???")) {
                    Collections.reverse(rs);
                }
                roles.remove(0);
            }
            player.setJinBaoBao(isJinBaoBao && session.isDoubleIdentities());
            player.setRoles(rs);
            MessageAction action = Objects.requireNonNull(event.getGuild().getTextChannelById(player.getChannelId())).sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("????????????????????? (?????????????????????????????????????????????????????????????????????????????????)")
                    .setDescription(String.join("???", rs) + (player.isJinBaoBao() ? " (?????????)" : "") +
                            (player.isDuplicated() ? " (?????????)" : ""))
                    .setColor(MsgUtils.getRandomColor()).build());
            if (session.isDoubleIdentities()) {
                action.setActionRow(Button.primary("changeRoleOrder", "?????????????????? (??????????????????????????????????????????????????????)"));
                CmdUtils.schedule(() -> {
                    Session.fetchCollection().updateOne(eq("guildId", event.getGuild().getIdLong()),
                            set("players." + player.getId() + ".rolePositionLocked", true));
                    Objects.requireNonNull(event.getGuild().getTextChannelById(player.getChannelId())).sendMessage("?????????????????????").queue();
                }, 120000);
            }
            action.queue();
            Session.fetchCollection().updateOne(eq("guildId", event.getGuild().getIdLong()),
                    set("players", session.getPlayers()));
            event.getHook().editOriginal(":white_check_mark:").queue();
        }
    }

    @Subcommand(description = "?????????????????????????????????")
    public void roles(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        if (!CmdUtils.isAdmin(event)) return;
        Session session = CmdUtils.getSession(event);
        if (session == null) return;
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("????????????")
                .setColor(MsgUtils.getRandomColor());
        for (String p : session.getPlayers().keySet().stream().sorted(MsgUtils.getAlphaNumComparator()).toList()) {
            Session.Player player = session.getPlayers().get(p);
            assert player.getRoles() != null;
            embedBuilder.addField("??????" + p,
                    String.join("???", player.getRoles()) + (player.isPolice() ? " (??????)" : "") +
                            (player.isJinBaoBao() ? " (?????????)" : "" + (player.isDuplicated() ? " (?????????)" : "")), true);
        }
        event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();
    }

    @Subcommand(description = "???????????????????????? (????????????????????????)")
    public void force_police(SlashCommandInteractionEvent
                                     event, @Option(value = "user", description = "??????????????????????????????") User user) {
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
