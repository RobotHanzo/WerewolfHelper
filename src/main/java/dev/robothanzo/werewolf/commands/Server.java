package dev.robothanzo.werewolf.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.robothanzo.jda.interactions.annotations.slash.Command;
import dev.robothanzo.jda.interactions.annotations.slash.Subcommand;
import dev.robothanzo.jda.interactions.annotations.slash.options.AutoCompleter;
import dev.robothanzo.jda.interactions.annotations.slash.options.Option;
import dev.robothanzo.werewolf.WerewolfHelper;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import dev.robothanzo.werewolf.utils.MsgUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.pushEach;
import static com.mongodb.client.model.Updates.set;

@Slf4j
@Command
public class Server {
    public static Map<Long, PendingSetup> pendingSetups = new HashMap<>();

    @Subcommand(description = "建立一個新的狼人殺伺服器")
    @SneakyThrows
    public void create(SlashCommandInteractionEvent event,
                       @Option(value = "players", description = "玩家數量") Long players,
                       @Option(value = "double_identity", description = "是否為雙身分模式，預設否", optional = true) Boolean doubleIdentity) {
        if (!CmdUtils.isServerCreator(event)) return;

        long userId = event.getUser().getIdLong();
        boolean doubleId = doubleIdentity != null && doubleIdentity;
        // store the channel where user invoked the command so we can send the invite back there later
        long originChannelId = event.getChannel().getIdLong();
        pendingSetups.put(userId, new PendingSetup(Math.toIntExact(players), doubleId, originChannelId));

        String inviteUrl = WerewolfHelper.jda.getInviteUrl(Permission.ADMINISTRATOR).replaceAll("scope=bot", "scope=bot%20applications.commands");

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("狼人殺伺服器建立指南")
                .setDescription("由於 Discord 政策變更，機器人無法再自動建立伺服器。請依照以下步驟完成設定：")
                .addField("1. 建立伺服器", "請手動建立一個新的 Discord 伺服器，名稱建議為「狼人殺伺服器」。", false)
                .addField("2. 邀請機器人", "請點擊下方連結將機器人邀請至新伺服器 (需授予管理員權限)：\n" + inviteUrl, false)
                .addField("3. 自動設定", "機器人加入後將會自動偵測並開始設定頻道與身分組。", false)
                .setColor(Color.GREEN);

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
        log.info("Queued a server setup for user {} with {} players", userId, players);
    }

    @Subcommand(description = "刪除所在之伺服器(僅可在狼人殺伺服器內使用)")
    public void delete(SlashCommandInteractionEvent event, @Option(value = "guild_id", optional = true) String guildId) {
        try {
            if (!CmdUtils.isServerCreator(event)) return;
            if (guildId == null) {
                Objects.requireNonNull(event.getGuild()).leave().queue();
                Session.fetchCollection().deleteOne(eq("guildId", event.getGuild().getIdLong()));
                Speech.speechSessions.remove(event.getGuild().getIdLong());
            } else {
                Objects.requireNonNull(WerewolfHelper.jda.getGuildById(guildId)).leave().queue();
                Session.fetchCollection().deleteOne(eq("guildId", guildId));
                Speech.speechSessions.remove(Objects.requireNonNull(WerewolfHelper.jda.getGuildById(guildId)).getIdLong());
            }
            event.reply(":white_check_mark:").queue();
        } catch (Exception e) {
            event.reply(":x:").queue();
        }
    }

    @Subcommand(description = "列出所在之伺服器")
    public void list(SlashCommandInteractionEvent event) {
        if (!CmdUtils.isAuthor(event)) return;
        StringBuilder sb = new StringBuilder();
        for (Guild guild : WerewolfHelper.jda.getGuilds()) {
            sb.append(guild.getName())
                    .append(" (").append(guild.getId()).append(")\n");
        }
        event.reply(sb.toString()).queue();
    }

    @Subcommand(description = "列出所在之伺服器")
    public void lists(SlashCommandInteractionEvent event) {
        if (!CmdUtils.isAuthor(event)) return;
        StringBuilder sb = new StringBuilder();
        for (Guild guild : WerewolfHelper.jda.getGuilds()) {
            sb.append(guild.getName())
                    .append(" (").append(guild.getId()).append(")\n");
        }
        event.reply(sb.toString()).queue();
    }

    @SneakyThrows
    @Subcommand
    public void session(SlashCommandInteractionEvent event, @Option(value = "guild_id", optional = true) String guildId) {
        event.deferReply().queue();
        if (!CmdUtils.isAuthor(event)) return;
        long gid;
        if (guildId == null) {
            if (event.getGuild() == null) {
                event.getHook().editOriginal(":x:").queue();
                return;
            }
            gid = event.getGuild().getIdLong();
        } else {
            gid = Long.parseLong(guildId);
        }

        Session session = Session.fetchCollection().find(eq("guildId", gid)).first();
        if (session == null) {
            event.getHook().editOriginal(":x:").queue();
        } else {
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("戰局資訊")
                    .setDescription("```json\n" + new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(session) + "\n```");
            event.getHook().editOriginalEmbeds(eb.build()).queue();
        }
    }

    public record PendingSetup(int players, boolean doubleIdentity, long originChannelId) {
    }

    @Subcommand
    public static class Roles {
        public static List<String> expandStringToList(String s, int amount) {
            List<String> list = new LinkedList<>();
            for (int i = 0; i < amount; i++) {
                list.add(s);
            }
            return list;
        }

        @AutoCompleter
        public void role(CommandAutoCompleteInteractionEvent event) {
            event.replyChoices(WerewolfHelper.ROLES.stream()
                    .filter(s -> s.startsWith(event.getFocusedOption().getValue()))
                    .limit(25)
                    .map(s -> new Choice(s, s))
                    .toList()).queue();
        }

        @AutoCompleter
        public void existingRole(CommandAutoCompleteInteractionEvent event) {
            List<net.dv8tion.jda.api.interactions.commands.Command.Choice> choices = new LinkedList<>();
            Session session = CmdUtils.getSession(event.getGuild());
            if (session == null) return;
            for (String role : session.getRoles()) {
                if (role.startsWith(event.getFocusedOption().getValue())) {
                    choices.add(new Choice(role, role));
                }
            }
            event.replyChoices(choices).queue();
        }

        @Subcommand(description = "列出角色清單")
        public void list(SlashCommandInteractionEvent event) {
            event.deferReply().queue();
            Session session = CmdUtils.getSession(event);
            if (session == null) return;
            EmbedBuilder embedBuilder = new EmbedBuilder().setTitle("角色清單").setColor(MsgUtils.getRandomColor());
            Map<String, Integer> roles = new HashMap<>();
            int rolesCount = 0;
            for (String role : session.getRoles()) {
                roles.put(role, roles.containsKey(role) ? roles.get(role) + 1 : 1);
            }
            for (Map.Entry<String, Integer> entry : roles.entrySet()) {
                embedBuilder.addField(entry.getKey(), "x" + entry.getValue(), true);
                rolesCount += entry.getValue();
            }
            if (rolesCount == session.getPlayers().size() * (session.isDoubleIdentities() ? 2 : 1)) {
                embedBuilder.setDescription(":white_check_mark: 角色數量正確");
            } else {
                embedBuilder.setDescription(":x: 角色數量錯誤，應有 *" + session.getPlayers().size() * (session.isDoubleIdentities() ? 2 : 1) + "* 個角色，現有 *" + rolesCount + "* 個");
            }
            event.getHook().editOriginalEmbeds(embedBuilder.build()).queue();
        }

        @Subcommand(description = "新增角色")
        public void add(SlashCommandInteractionEvent event, @Option(value = "role", autoComplete = true) String role,
                        @Option(value = "amount", optional = true) Long amount) {
            event.deferReply().queue();
            if (!CmdUtils.isAdmin(event)) return;
            if (amount == null) amount = 1L;
            Session session = CmdUtils.getSession(event);
            if (session == null) return;
            Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()),
                    pushEach("roles", expandStringToList(role, amount.intValue())));
            event.getHook().editOriginal(":white_check_mark:").queue();
        }

        @Subcommand(description = "刪除角色")
        public void delete(SlashCommandInteractionEvent event, @Option(value = "role", autoComplete = true, autoCompleter = "existingRole") String role,
                           @Option(value = "amount", optional = true) Long amount) {
            event.deferReply().queue();
            if (!CmdUtils.isAdmin(event)) return;
            if (amount == null) amount = 1L;
            Session session = CmdUtils.getSession(event);
            if (session == null) return;
            List<String> roles = session.getRoles();
            for (int i = 0; i < amount; i++) {
                roles.remove(role);
            }
            Session.fetchCollection().updateOne(eq("guildId", session.getGuildId()),
                    set("roles", roles));
            event.getHook().editOriginal(":white_check_mark:").queue();
        }
    }

    @Subcommand
    public static class Set {
        @Subcommand(description = "設定是否為雙身分局")
        public void double_identities(SlashCommandInteractionEvent event, @Option(value = "value") Boolean value) {
            event.deferReply().queue();
            if (!CmdUtils.isAdmin(event)) return;
            Session session = CmdUtils.getSession(event);
            if (session == null) return;
            Session.fetchCollection().updateOne(
                    eq("guildId", Objects.requireNonNull(event.getGuild()).getIdLong()),
                    set("doubleIdentities", value));
            event.getHook().editOriginal(":white_check_mark:").queue();
        }

        @Subcommand(description = "設定是否在發言後將玩家靜音")
        public void mute_after_speech(SlashCommandInteractionEvent event, @Option(value = "value") Boolean value) {
            event.deferReply().queue();
            if (!CmdUtils.isAdmin(event)) return;
            Session session = CmdUtils.getSession(event);
            if (session == null) return;
            Session.fetchCollection().updateOne(
                    eq("guildId", Objects.requireNonNull(event.getGuild()).getIdLong()),
                    set("muteAfterSpeech", value));
            event.getHook().editOriginal(":white_check_mark:").queue();
        }

        @Subcommand(description = "設定總玩家數量")
        public void players(SlashCommandInteractionEvent event, @Option(value = "value") Long value) {
            event.deferReply().queue();
            if (!CmdUtils.isAdmin(event)) return;
            Session session = CmdUtils.getSession(event);
            if (session == null) return;
            assert event.getGuild() != null;
            Map<String, Session.Player> players = session.getPlayers();
            try {
                for (Session.Player player : new LinkedList<>(players.values())) {
                    if (player.getId() > value) {
                        players.remove(String.valueOf(player.getId()));
                        Objects.requireNonNull(event.getGuild().getRoleById(player.getRoleId())).delete().queue();
                        Objects.requireNonNull(event.getGuild().getTextChannelById(player.getChannelId())).delete().queue();
                    }
                }
                for (long i = players.size() + 1; i <= value; i++) {
                    Role role = event.getGuild().createRole().setColor(MsgUtils.getRandomColor()).setHoisted(true).setName("玩家" + i).complete();
                    TextChannel channel = event.getGuild().createTextChannel("玩家" + i)
                            .addPermissionOverride(Objects.requireNonNull(event.getGuild().getRoleById(session.getSpectatorRoleId())),
                                    Permission.VIEW_CHANNEL.getRawValue(), Permission.MESSAGE_SEND.getRawValue())
                            .addPermissionOverride(role, List.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), List.of())
                            .addPermissionOverride(event.getGuild().getPublicRole(), List.of(), List.of(Permission.VIEW_CHANNEL,
                                    Permission.MESSAGE_SEND, Permission.USE_APPLICATION_COMMANDS)).complete();
                    players.put(String.valueOf(i), Session.Player.builder()
                            .id((int) i)
                            .roleId(role.getIdLong())
                            .channelId(channel.getIdLong())
                            .build());
                }
                Session.fetchCollection().updateOne(eq("guildId", event.getGuild().getIdLong()), set("players", players));
            } catch (Exception e) {
                log.error("Failed to update player amount", e);
                event.getHook().editOriginal(":x: 因為未知原因而無法更新玩家人數").queue();
            }
            event.getHook().editOriginal(":white_check_mark:").queue();
        }
    }
}
