package dev.robothanzo.werewolf.utils;

import dev.robothanzo.werewolf.WerewolfHelper;
import dev.robothanzo.werewolf.commands.Server;
import dev.robothanzo.werewolf.database.documents.Session;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class SetupHelper {
    @SneakyThrows
    public static void setup(Guild guild, Server.PendingSetup config) {
        log.info("Starting setup for guild {}", guild.getId());

        guild.getManager()
                .setDefaultNotificationLevel(Guild.NotificationLevel.MENTIONS_ONLY)
                .setName("狼人殺伺服器")
                .setIcon(Icon.from(Objects.requireNonNull(WerewolfHelper.class.getClassLoader().getResourceAsStream("wolf.png")))).queue();

        // Delete all existing channels before creating new ones
        try {
            guild.getChannels().forEach(ch -> {
                try {
                    ch.delete().queue();
                } catch (Exception e) {
                    log.warn("Failed to delete channel {}: {}", ch.getId(), e.getMessage());
                }
            });
        } catch (Exception e) {
            log.warn("Error while deleting existing channels: {}", e.getMessage());
        }

        // Setup session data
        Session.SessionBuilder sessionBuilder = Session.builder()
                .guildId(guild.getIdLong())
                .doubleIdentities(config.doubleIdentity())
                .owner(guild.getOwnerIdLong());

        if (config.doubleIdentity()) {
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

        // Get public role early
        Role publicRole = guild.getPublicRole();

        // 1. Create Judge Role first
        guild.createRole()
                .setName("法官")
                .setColor(Color.YELLOW)
                .setHoisted(true)
                .setPermissions(Permission.ADMINISTRATOR)
                .queue(judgeRole -> {
                    sessionBuilder.judgeRoleId(judgeRole.getIdLong());

                    // 2. Create Player Roles and Channels (without spectator/dead overrides)
                    createPlayerRolesAndChannels(guild, config.players(), publicRole).thenAccept(players -> {
                        sessionBuilder.players(players);

                        // 3. Create Spectator/Dead Role last among roles
                        guild.createRole()
                                .setName("旁觀者/死人")
                                .setColor(new Color(0x654321))
                                .setHoisted(false)
                                .setPermissions(Permission.VIEW_CHANNEL)
                                .queue(deadRole -> {
                                    sessionBuilder.spectatorRoleId(deadRole.getIdLong());

                                    // Add spectator permission override to existing player channels
                                    try {
                                        for (Session.Player p : players.values()) {
                                            TextChannel ch = guild.getTextChannelById(p.getChannelId());
                                            if (ch != null) {
                                                try {
                                                    long allowBits = Permission.VIEW_CHANNEL.getRawValue() | Permission.MESSAGE_SEND.getRawValue();
                                                    ch.upsertPermissionOverride(deadRole).setPermissions(allowBits, 0L).queue();
                                                } catch (Exception ignore) {
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.warn("Failed to add spectator overrides to player channels: {}", e.getMessage());
                                    }

                                    // 4. Create remaining core channels that rely on spectator role
                                    createChannels(guild, deadRole, publicRole, sessionBuilder).thenAccept(v -> {
                                        // Finalize: persist session and notify
                                        Session.fetchCollection().insertOne(sessionBuilder.build());
                                        log.info("Successfully registered guild as a session: {}", guild.getId());

                                        TextChannel courtChannel = guild.getTextChannelById(sessionBuilder.build().getCourtTextChannelId());
                                        if (courtChannel != null) {
                                            courtChannel.sendMessage("伺服器設定完成！請法官邀請玩家並開始遊戲。").queue(message -> {
                                                try {
                                                    courtChannel.createInvite().setMaxUses(0).setMaxAge(0).queue(invite -> {
                                                        long originChannelId = config.originChannelId();
                                                        TextChannel origin = WerewolfHelper.jda.getTextChannelById(originChannelId);
                                                        if (origin != null) {
                                                            origin.sendMessage("伺服器已設定完成，點此連結前往伺服器： " + invite.getUrl()).queue();
                                                        }
                                                    });
                                                } catch (Exception e) {
                                                    log.warn("Failed to create/send invite: {}", e.getMessage());
                                                }
                                            });
                                        } else {
                                            try {
                                                if (guild.getDefaultChannel() instanceof TextChannel dc) {
                                                    dc.sendMessage("伺服器設定完成！請法官邀請玩家並開始遊戲。").queue();
                                                }
                                            } catch (Exception ignored) {
                                            }
                                        }
                                    });
                                });
                    });
                });
    }

    private static CompletableFuture<Void> createChannels(Guild guild, Role deadRole, Role publicRole, Session.SessionBuilder builder) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // We will execute these somewhat sequentially to ensure we capture IDs
        // Court Text
        guild.createTextChannel("法院")
                .addPermissionOverride(publicRole, List.of(), List.of(Permission.USE_APPLICATION_COMMANDS))
                .addPermissionOverride(deadRole, List.of(Permission.VIEW_CHANNEL), List.of(Permission.MESSAGE_SEND, Permission.MESSAGE_ADD_REACTION))
                .queue(courtText -> {
                    builder.courtTextChannelId(courtText.getIdLong());

                    // Court Voice
                    guild.createVoiceChannel("法院")
                            .addPermissionOverride(publicRole, List.of(Permission.VOICE_SPEAK), List.of(Permission.USE_EMBEDDED_ACTIVITIES))
                            .addPermissionOverride(deadRole, List.of(Permission.VIEW_CHANNEL), List.of(Permission.VOICE_SPEAK))
                            .queue(courtVoice -> {
                                builder.courtVoiceChannelId(courtVoice.getIdLong());

                                // Spectator Text
                                guild.createTextChannel("場外")
                                        .addPermissionOverride(deadRole, List.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), List.of())
                                        .addPermissionOverride(publicRole, List.of(), List.of(Permission.VIEW_CHANNEL, Permission.USE_APPLICATION_COMMANDS))
                                        .queue(specText -> {
                                            builder.spectatorTextChannelId(specText.getIdLong());

                                            // Spectator Voice
                                            guild.createVoiceChannel("場外")
                                                    .addPermissionOverride(deadRole, List.of(Permission.VIEW_CHANNEL, Permission.VOICE_SPEAK), List.of())
                                                    .addPermissionOverride(publicRole, List.of(), List.of(Permission.VIEW_CHANNEL))
                                                    .queue(specVoice -> {

                                                        // Judge Text
                                                        guild.createTextChannel("法官")
                                                                .addPermissionOverride(deadRole, List.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), List.of()) // Judge can see
                                                                .addPermissionOverride(publicRole, List.of(), List.of(Permission.VIEW_CHANNEL, Permission.USE_APPLICATION_COMMANDS))
                                                                .queue(judgeText -> {
                                                                    builder.judgeTextChannelId(judgeText.getIdLong());
                                                                    future.complete(null);
                                                                });
                                                    });
                                        });
                            });
                });
        return future;
    }

    private static CompletableFuture<Map<String, Session.Player>> createPlayerRolesAndChannels(Guild guild, int playerCount, Role publicRole) {
        CompletableFuture<Map<String, Session.Player>> future = new CompletableFuture<>();
        Map<String, Session.Player> players = new HashMap<>();

        // We need to do this recursively or iteratively with futures to ensure all are done before completing
        createPlayerRecursively(guild, playerCount, 1, publicRole, players, future);

        return future;
    }

    private static void createPlayerRecursively(Guild guild, int total, int current, Role publicRole, Map<String, Session.Player> players, CompletableFuture<Map<String, Session.Player>> future) {
        if (current > total) {
            future.complete(players);
            return;
        }

        guild.createRole()
                .setName("玩家" + current)
                .setColor(MsgUtils.getRandomColor())
                .setHoisted(true)
                .queue(playerRole ->
                        guild.createTextChannel("玩家" + current)
                                // Do not add spectator/dead overrides here (spectator is created later)
                                .addPermissionOverride(playerRole, List.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), List.of())
                                .addPermissionOverride(publicRole, List.of(), List.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.USE_APPLICATION_COMMANDS))
                                .queue(playerChannel -> {
                                    players.put(String.valueOf(current), Session.Player.builder()
                                            .id(current)
                                            .roleId(playerRole.getIdLong())
                                            .channelId(playerChannel.getIdLong())
                                            .build());
                                    createPlayerRecursively(guild, total, current + 1, publicRole, players, future);
                                }));
    }
}
