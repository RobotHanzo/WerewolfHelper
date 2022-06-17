package dev.robothanzo.werewolf.listeners;

import dev.robothanzo.werewolf.commands.Server;
import dev.robothanzo.werewolf.database.documents.Session;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class GuildJoinListener extends ListenerAdapter {
    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        if (event.getGuild().getName().equals("狼人殺伺服器")) {
            Session.SessionBuilder sessionBuilder = Session.builder()
                    .guildId(event.getGuild().getIdLong())
                    .doubleIdentities(Server.newServerDoubleIdentities);
            if (Server.newServerDoubleIdentities) {
                sessionBuilder.roles(List.of(
                        "狼人", "狼人", "狼兄",
                        "女巫", "獵人", "預言家", "守墓人", "騎士", "複製人",
                        "平民", "平民", "平民", "平民", "平民", "平民", "平民"));
            } else {
                sessionBuilder.roles(List.of(
                        "狼人", "狼人", "狼人",
                        "女巫", "獵人", "預言家",
                        "平民", "平民", "平民"
                ));
            }
            Map<Integer, Long> playerChannels = new HashMap<>();
            Map<String, Session.Player> players = new HashMap<>();
            TextChannel inviteChannel = null;
            for (GuildChannel channel : event.getGuild().getChannels()) {
                if (channel.getName().equals("法院")) {
                    if (channel.getType() == ChannelType.TEXT) {
                        sessionBuilder.courtTextChannelId(channel.getIdLong());
                        inviteChannel = (TextChannel) channel;
                    }
                    if (channel.getType() == ChannelType.VOICE) {
                        sessionBuilder.courtVoiceChannelId(channel.getIdLong());
                    }
                }
                if (channel.getName().equals("場外")) {
                    if (channel.getType() == ChannelType.TEXT) {
                        sessionBuilder.spectatorTextChannelId(channel.getIdLong());
                    }
                }
                if (channel.getName().equals("法官")) {
                    sessionBuilder.judgeTextChannelId(channel.getIdLong());
                }
                if (channel.getName().startsWith("玩家")) {
                    playerChannels.put(Integer.parseInt(channel.getName().replaceAll("玩家", "")), channel.getIdLong());
                }
            }
            for (Role role : event.getGuild().getRoles()) {
                if (role.getName().equals("法官")) {
                    sessionBuilder.judgeRoleId(role.getIdLong());
                }
                if (role.getName().equals("旁觀者/死人")) {
                    sessionBuilder.spectatorRoleId(role.getIdLong());
                }
                if (role.getName().startsWith("玩家")) {
                    int id = Integer.parseInt(role.getName().replaceAll("玩家", ""));
                    players.put(String.valueOf(id),
                            Session.Player.builder()
                                    .id(id)
                                    .roleId(role.getIdLong())
                                    .channelId(playerChannels.get(id)).build());
                }
            }
            Objects.requireNonNull(inviteChannel).sendMessage(
                    "法官須將機器人重新邀請，打開以下連結: https://discord.com/api/oauth2/authorize?client_id=804332838559809616&scope=applications.commands").queue();
            sessionBuilder.players(players);
            Session.fetchCollection().insertOne(sessionBuilder.build());
            log.info("Successfully registered guild as a session: " + event.getGuild().getId());
            Server.newServerHook.editOriginal("狼人殺伺服器已建立，請至 " + inviteChannel.createInvite().complete().getUrl() + " 參與遊戲").queue();
            Server.serverCreationLock.unlock();
        }
    }
}
