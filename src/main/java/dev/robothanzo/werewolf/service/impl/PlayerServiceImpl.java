package dev.robothanzo.werewolf.service.impl;

import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.security.SessionRepository;
import dev.robothanzo.werewolf.service.DiscordService;
import dev.robothanzo.werewolf.service.GameSessionService;
import dev.robothanzo.werewolf.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerServiceImpl implements PlayerService {

    private final SessionRepository sessionRepository;
    private final DiscordService discordService;
    private final GameSessionService gameSessionService;

    @Override
    public List<Map<String, Object>> getPlayersJSON(long guildId) {
        Optional<Session> sessionOpt = sessionRepository.findByGuildId(guildId);
        if (sessionOpt.isEmpty()) {
            throw new RuntimeException("Session not found");
        }
        return gameSessionService.playersToJSON(sessionOpt.get());
    }

    @Override
    public void setPlayerCount(long guildId, int count, java.util.function.Consumer<String> onProgress,
                               java.util.function.Consumer<Integer> onPercent) throws Exception {
        var session = sessionRepository.findByGuildId(guildId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        var jda = discordService.getJDA();
        var guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        if (onPercent != null)
            onPercent.accept(0);
        if (onProgress != null)
            onProgress.accept("開始同步 Discord 狀態...");

        Map<String, Session.Player> players = session.getPlayers();
        List<dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask> deleteTasks = new ArrayList<>();
        List<dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask> createRoleTasks = new ArrayList<>();
        List<dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask> createChannelTasks = new ArrayList<>();

        // Phase 1: Identify deletions
        var existingPlayerIds = new java.util.LinkedList<>(players.keySet());
        for (String idStr : existingPlayerIds) {
            int pid = Integer.parseInt(idStr);
            if (pid > count) {
                var player = players.remove(idStr);
                var role = guild.getRoleById(player.getRoleId());
                if (role != null) {
                    deleteTasks.add(new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(role.delete(),
                            "刪除身分組: " + role.getName()));
                }
                var channel = guild.getTextChannelById(player.getChannelId());
                if (channel != null) {
                    deleteTasks.add(new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(channel.delete(),
                            "刪除頻道: " + channel.getName()));
                }
            }
        }

        // Run Deletions (0% -> 30%)
        if (!deleteTasks.isEmpty()) {
            dev.robothanzo.werewolf.utils.DiscordActionRunner.runActions(deleteTasks, onProgress, onPercent, 0, 30, 60);
        }

        // Phase 2: Create Roles
        var spectatorRole = guild.getRoleById(session.getSpectatorRoleId());
        Map<Integer, net.dv8tion.jda.api.entities.Role> newRolesMap = new java.util.concurrent.ConcurrentHashMap<>();

        for (int i = players.size() + 1; i <= count; i++) {
            int playerId = i;
            String name = "玩家" + dev.robothanzo.werewolf.database.documents.Session.Player.ID_FORMAT.format(i);
            var task = new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(
                    guild.createRole()
                            .setColor(dev.robothanzo.werewolf.utils.MsgUtils.getRandomColor())
                            .setHoisted(true)
                            .setName(name),
                    "創建身分組: " + name);
            task.onSuccess = (obj) -> newRolesMap.put(playerId, (net.dv8tion.jda.api.entities.Role) obj);
            createRoleTasks.add(task);
        }

        if (!createRoleTasks.isEmpty()) {
            dev.robothanzo.werewolf.utils.DiscordActionRunner.runActions(createRoleTasks, onProgress, onPercent, 30, 60,
                    60);
        }

        // Phase 3: Create Channels
        for (var entry : newRolesMap.entrySet()) {
            int playerId = entry.getKey();
            var role = entry.getValue();
            String name = "玩家" + dev.robothanzo.werewolf.database.documents.Session.Player.ID_FORMAT.format(playerId);

            var task = new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(
                    guild.createTextChannel(name)
                            .addPermissionOverride(spectatorRole != null ? spectatorRole : guild.getPublicRole(),
                                    net.dv8tion.jda.api.Permission.VIEW_CHANNEL.getRawValue(),
                                    net.dv8tion.jda.api.Permission.MESSAGE_SEND.getRawValue())
                            .addPermissionOverride(role,
                                    List.of(net.dv8tion.jda.api.Permission.VIEW_CHANNEL,
                                            net.dv8tion.jda.api.Permission.MESSAGE_SEND),
                                    List.of())
                            .addPermissionOverride(guild.getPublicRole(),
                                    List.of(),
                                    List.of(net.dv8tion.jda.api.Permission.VIEW_CHANNEL,
                                            net.dv8tion.jda.api.Permission.MESSAGE_SEND,
                                            net.dv8tion.jda.api.Permission.USE_APPLICATION_COMMANDS)),
                    "創建頻道: " + name);

            task.onSuccess = (obj) -> {
                var channel = (net.dv8tion.jda.api.entities.channel.concrete.TextChannel) obj;
                players.put(String.valueOf(playerId),
                        dev.robothanzo.werewolf.database.documents.Session.Player.builder()
                                .id(playerId)
                                .roleId(role.getIdLong())
                                .channelId(channel.getIdLong())
                                .build());
            };
            createChannelTasks.add(task);
        }

        if (!createChannelTasks.isEmpty()) {
            dev.robothanzo.werewolf.utils.DiscordActionRunner.runActions(createChannelTasks, onProgress, onPercent, 60,
                    95, 120);
        }

        session.setPlayers(players);
        sessionRepository.save(session);
        if (onPercent != null)
            onPercent.accept(100);
        if (onProgress != null)
            onProgress.accept("同步完成！");
        gameSessionService.broadcastUpdate(guildId);
    }

    @Override
    public void updatePlayerRoles(long guildId, String playerId, List<String> roles) {
        try {
            var session = sessionRepository.findByGuildId(guildId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            var player = session.getPlayers().get(playerId);
            if (player == null)
                throw new Exception("Player not found");

            List<String> finalRoles = new ArrayList<>(roles);
            boolean isDuplicated = roles.contains("複製人");
            player.setDuplicated(isDuplicated);
            if (isDuplicated && finalRoles.size() == 2) {
                if (finalRoles.get(0).equals("複製人"))
                    finalRoles.set(0, finalRoles.get(1));
                else if (finalRoles.get(1).equals("複製人"))
                    finalRoles.set(1, finalRoles.get(0));
            }

            player.setIdiot(finalRoles.contains("白癡"));
            boolean isJinBaoBao = session.isDoubleIdentities() && finalRoles.size() == 2 &&
                    finalRoles.get(0).equals("平民") && finalRoles.get(1).equals("平民");
            player.setJinBaoBao(isJinBaoBao);
            player.setRoles(finalRoles);

            sessionRepository.save(session);

            var jda = discordService.getJDA();
            if (jda != null && player.getChannelId() != 0) {
                var guild = jda.getGuildById(guildId);
                if (guild != null) {
                    var channel = guild.getTextChannelById(player.getChannelId());
                    if (channel != null) {
                        channel.sendMessage("法官已將你的身份更改為: " + String.join(", ", roles)).queue();
                    }
                }
            }

            gameSessionService.broadcastUpdate(guildId);
        } catch (Exception e) {
            log.error("Failed to update player roles: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to update player roles", e);
        }
    }

    @Override
    public void switchRoleOrder(long guildId, String playerId) {
        try {
            var session = sessionRepository.findByGuildId(guildId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            var player = session.getPlayers().get(playerId);
            if (player == null)
                throw new Exception("Player not found");

            if (player.isRolePositionLocked())
                throw new Exception("你的身分順序已被鎖定");
            if (player.getRoles() == null || player.getRoles().size() < 2)
                throw new Exception("Not enough roles to switch");

            java.util.Collections.swap(player.getRoles(), 0, 1);
            sessionRepository.save(session);

            var jda = discordService.getJDA();
            if (jda != null && player.getChannelId() != 0) {
                var guild = jda.getGuildById(guildId);
                if (guild != null) {
                    var channel = guild.getTextChannelById(player.getChannelId());
                    if (channel != null) {
                        channel.sendMessage("你已交換了角色順序，現在主要角色為: " + player.getRoles().get(0)).queue();
                    }
                }
            }
            gameSessionService.broadcastUpdate(guildId);
        } catch (Exception e) {
            log.error("Switch role order failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to switch role order", e);
        }
    }

    @Override
    public void setRolePositionLock(long guildId, String playerId, boolean locked) {
        try {
            var session = sessionRepository.findByGuildId(guildId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));
            var player = session.getPlayers().get(playerId);
            if (player == null)
                throw new Exception("Player not found");

            player.setRolePositionLocked(locked);
            sessionRepository.save(session);
            gameSessionService.broadcastUpdate(guildId);
        } catch (Exception e) {
            log.error("Failed to set role position lock: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to set role position lock", e);
        }
    }
}
