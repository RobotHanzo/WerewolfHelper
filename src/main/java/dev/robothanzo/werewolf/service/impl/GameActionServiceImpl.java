package dev.robothanzo.werewolf.service.impl;

import dev.robothanzo.werewolf.security.SessionRepository;
import dev.robothanzo.werewolf.service.DiscordService;
import dev.robothanzo.werewolf.service.GameActionService;
import dev.robothanzo.werewolf.service.GameSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameActionServiceImpl implements GameActionService {

    private final GameSessionService gameSessionService;
    private final DiscordService discordService;
    private final SessionRepository sessionRepository;

    @Override
    public void resetGame(long guildId, Consumer<String> statusCallback, Consumer<Integer> progressCallback)
            throws Exception {
        var session = sessionRepository.findByGuildId(guildId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (progressCallback != null)
            progressCallback.accept(0);
        if (statusCallback != null)
            statusCallback.accept("正在連線至 Discord...");

        var jda = discordService.getJDA();
        var guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        if (progressCallback != null)
            progressCallback.accept(5);
        if (statusCallback != null)
            statusCallback.accept("正在掃描需要清理的身分組...");

        java.util.List<dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask> tasks = new java.util.ArrayList<>();
        var spectatorRole = guild.getRoleById(session.getSpectatorRoleId());

        for (var player : session.getPlayers().values()) {
            Long currentUserId = player.getUserId();

            player.setUserId(null);
            player.setRoles(new java.util.ArrayList<>());
            player.setDeadRoles(new java.util.ArrayList<>());
            player.setPolice(false);
            player.setIdiot(false);
            player.setJinBaoBao(false);
            player.setDuplicated(false);
            player.setRolePositionLocked(false);

            if (currentUserId != null) {
                var member = guild.getMemberById(currentUserId);
                if (member != null) {
                    if (spectatorRole != null) {
                        tasks.add(new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(
                                guild.removeRoleFromMember(member, spectatorRole),
                                "已移除 " + member.getEffectiveName() + " 的旁觀者身分組"));
                    }

                    var playerRole = guild.getRoleById(player.getRoleId());
                    if (playerRole != null) {
                        tasks.add(new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(
                                guild.removeRoleFromMember(member, playerRole),
                                "已移除玩家 " + player.getId() + " (" + member.getEffectiveName() + ") 的玩家身分組"));
                    }

                    // Nickname Reset
                    if (member.isOwner()) {
                        if (statusCallback != null)
                            statusCallback.accept("  - [資訊] 跳過群主 " + member.getEffectiveName() + " 的暱稱重置");
                    } else if (!guild.getSelfMember().canInteract(member)) {
                        if (statusCallback != null)
                            statusCallback.accept("  - [資訊] 無法重置 " + member.getEffectiveName() + " 的暱稱 (機器人權限不足)");
                    } else if (member.getNickname() != null) {
                        tasks.add(new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(
                                member.modifyNickname(null),
                                "已重置 " + member.getEffectiveName() + " 的暱稱"));
                    }
                }
            }
        }

        if (!tasks.isEmpty()) {
            if (statusCallback != null)
                statusCallback.accept("正在執行 Discord 變更 (共 " + tasks.size() + " 項)...");
            dev.robothanzo.werewolf.utils.DiscordActionRunner.runActions(tasks, statusCallback, progressCallback, 5, 90,
                    30);
        } else {
            if (statusCallback != null)
                statusCallback.accept("沒有偵測到需要清理的 Discord 變更。");
            if (progressCallback != null)
                progressCallback.accept(90);
        }

        if (statusCallback != null)
            statusCallback.accept("正在更新資料庫並清理日誌...");

        session.setLogs(new java.util.ArrayList<>());
        session.setHasAssignedRoles(false);
        session.addLog(dev.robothanzo.werewolf.database.documents.LogType.GAME_RESET, "遊戲已重置", null);

        sessionRepository.save(session);

        if (progressCallback != null)
            progressCallback.accept(100);
        if (statusCallback != null)
            statusCallback.accept("操作完成。");

        gameSessionService.broadcastUpdate(guildId);
    }

    @Override
    public void markPlayerDead(long guildId, long userId, boolean allowLastWords) {
        try {
            var session = sessionRepository.findByGuildId(guildId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            var jda = discordService.getJDA();
            var guild = jda.getGuildById(guildId);
            if (guild == null)
                throw new Exception("Guild not found");

            var member = guild.getMemberById(userId);
            if (member == null) {
                member = guild.retrieveMemberById(userId).complete();
            }

            boolean success = dev.robothanzo.werewolf.commands.Player.playerDied(session, member, allowLastWords,
                    false);
            if (!success)
                throw new Exception("Failed to mark player dead");

            sessionRepository.save(session);
            gameSessionService.broadcastUpdate(guildId);
        } catch (Exception e) {
            log.error("Failed to mark player dead: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to mark player dead", e);
        }
    }

    @Override
    public void revivePlayer(long guildId, long userId) {
        try {
            var session = sessionRepository.findByGuildId(guildId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            var jda = discordService.getJDA();
            var guild = jda.getGuildById(guildId);
            if (guild == null)
                throw new Exception("Guild not found");

            var member = guild.getMemberById(userId);
            if (member == null) {
                member = guild.retrieveMemberById(userId).complete();
            }

            dev.robothanzo.werewolf.database.documents.Session.Player targetPlayer = null;
            for (var p : session.getPlayers().values()) {
                if (p.getUserId() != null && p.getUserId() == userId) {
                    targetPlayer = p;
                    break;
                }
            }

            if (targetPlayer == null || targetPlayer.getDeadRoles() == null || targetPlayer.getDeadRoles().isEmpty()) {
                throw new Exception("Player has no dead roles to revive");
            }

            var rolesToRevive = new java.util.ArrayList<>(targetPlayer.getDeadRoles());
            for (String role : rolesToRevive) {
                dev.robothanzo.werewolf.commands.Player.playerRevived(session, member, role);
            }

            targetPlayer.updateNickname(member);
            sessionRepository.save(session);
            gameSessionService.broadcastUpdate(guildId);
        } catch (Exception e) {
            log.error("Failed to revive player: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to revive player", e);
        }
    }

    @Override
    public void reviveRole(long guildId, long userId, String role) {
        try {
            var session = sessionRepository.findByGuildId(guildId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            var jda = discordService.getJDA();
            var guild = jda.getGuildById(guildId);
            if (guild == null)
                throw new Exception("Guild not found");

            var member = guild.getMemberById(userId);
            if (member == null) {
                member = guild.retrieveMemberById(userId).complete();
            }

            boolean success = dev.robothanzo.werewolf.commands.Player.playerRevived(session, member, role);
            if (!success)
                throw new Exception("Failed to revive role");

            sessionRepository.save(session);
            gameSessionService.broadcastUpdate(guildId);
        } catch (Exception e) {
            log.error("Failed to revive player role: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to revive player role", e);
        }
    }

    @Override
    public void setPolice(long guildId, long userId) {
        try {
            var session = sessionRepository.findByGuildId(guildId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            var jda = discordService.getJDA();
            var guild = jda.getGuildById(guildId);
            if (guild == null)
                throw new Exception("Guild not found");

            for (var player : session.getPlayers().values()) {
                if (player.isPolice()) {
                    player.setPolice(false);
                    if (player.getUserId() != null) {
                        var member = guild.getMemberById(player.getUserId());
                        if (member != null)
                            player.updateNickname(member);
                    }
                }
            }

            dev.robothanzo.werewolf.database.documents.Session.Player targetPlayer = null;
            for (var player : session.getPlayers().values()) {
                if (player.getUserId() != null && player.getUserId() == userId) {
                    player.setPolice(true);
                    targetPlayer = player;
                    break;
                }
            }

            if (targetPlayer == null)
                throw new Exception("Player not found");

            if (targetPlayer.getUserId() != null) {
                var member = guild.getMemberById(targetPlayer.getUserId());
                if (member != null) {
                    targetPlayer.updateNickname(member);
                    var courtChannel = guild.getTextChannelById(session.getCourtTextChannelId());
                    if (courtChannel != null) {
                        courtChannel.sendMessage(":white_check_mark: 警徽已移交給 " + member.getAsMention()).queue();
                    }
                }
            }

            sessionRepository.save(session);
            gameSessionService.broadcastUpdate(guildId);
        } catch (Exception e) {
            log.error("Failed to set police: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to set police", e);
        }
    }

    @Override
    public void broadcastProgress(long guildId, String message, Integer percent) {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("guildId", String.valueOf(guildId));
        if (message != null)
            data.put("message", message);
        if (percent != null)
            data.put("percent", percent);
        gameSessionService.broadcastEvent("PROGRESS", data);
    }
}
