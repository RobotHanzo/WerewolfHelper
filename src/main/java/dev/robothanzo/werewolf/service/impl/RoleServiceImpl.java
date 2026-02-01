package dev.robothanzo.werewolf.service.impl;

import dev.robothanzo.werewolf.security.SessionRepository;
import dev.robothanzo.werewolf.service.DiscordService;
import dev.robothanzo.werewolf.service.GameSessionService;
import dev.robothanzo.werewolf.service.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SessionRepository sessionRepository;
    private final DiscordService discordService;
    private final GameSessionService gameSessionService;

    @Override
    public void addRole(long guildId, String roleName, int amount) {
        try {
            var session = sessionRepository.findByGuildId(guildId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            List<String> roles = new java.util.ArrayList<>(session.getRoles());
            for (int i = 0; i < amount; i++) {
                roles.add(roleName);
            }
            session.setRoles(roles);
            sessionRepository.save(session);

            gameSessionService.broadcastUpdate(guildId);
        } catch (Exception e) {
            log.error("Failed to add role: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to add role", e);
        }
    }

    @Override
    public void removeRole(long guildId, String roleName, int amount) {
        try {
            var session = sessionRepository.findByGuildId(guildId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            List<String> roles = new java.util.ArrayList<>(session.getRoles());
            for (int i = 0; i < amount; i++) {
                roles.remove(roleName);
            }
            session.setRoles(roles);
            sessionRepository.save(session);

            gameSessionService.broadcastUpdate(guildId);
        } catch (Exception e) {
            log.error("Failed to remove role: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove role", e);
        }
    }

    @Override
    public List<String> getRoles(long guildId) {
        var session = sessionRepository.findByGuildId(guildId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        return session.getRoles();
    }

    @Override
    public void assignRoles(long guildId, Consumer<String> statusLogger, Consumer<Integer> progressCallback) {
        try {
            var session = sessionRepository.findByGuildId(guildId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            if (session.isHasAssignedRoles()) {
                throw new Exception("Roles already assigned");
            }

            int totalPlayers = session.getPlayers().size();
            log.info("Starting role assignment for guild {} with {} players", guildId, totalPlayers);

            if (progressCallback != null)
                progressCallback.accept(0);
            if (statusLogger != null)
                statusLogger.accept("正在掃描伺服器玩家...");

            var jda = discordService.getJDA();
            var guild = jda.getGuildById(guildId);
            if (guild == null) {
                throw new Exception("Guild not found");
            }

            java.util.List<net.dv8tion.jda.api.entities.Member> pending = new java.util.LinkedList<>();
            for (var member : guild.getMembers()) {
                if ((!member.getUser().isBot()) &&
                        (!member.isOwner()) &&
                        (!member.getRoles().contains(guild.getRoleById(session.getJudgeRoleId()))) &&
                        (!member.getRoles().contains(guild.getRoleById(session.getSpectatorRoleId())))) {
                    pending.add(member);
                }
            }

            if (progressCallback != null)
                progressCallback.accept(10);
            if (statusLogger != null)
                statusLogger.accept("正在驗證玩家與身分數量...");

            java.util.Collections.shuffle(pending, new java.util.Random());
            if (pending.size() != session.getPlayers().size()) {
                throw new Exception(
                        "玩家數量不符合設定值。請確認是否已給予旁觀者應有之身分(使用 `/player died`)，或檢查 `/server set players` 設定的人數。\n(待分配: "
                                + pending.size() + ", 需要: " + session.getPlayers().size() + ")");
            }

            int rolesPerPlayer = session.isDoubleIdentities() ? 2 : 1;
            if (pending.size() != (session.getRoles().size() / rolesPerPlayer)) {
                throw new Exception("玩家身分數量不符合身分清單數量。請確認是否正確啟用雙身分模式，並檢查 `/server roles list`。\n(目前玩家: " + pending.size()
                        + ", 身分總數: " + session.getRoles().size() + ")");
            }

            java.util.List<String> roles = new java.util.ArrayList<>(session.getRoles());
            java.util.Collections.shuffle(roles);

            int gaveJinBaoBao = 0;
            if (statusLogger != null)
                statusLogger.accept("正在分配身分並更新伺服器狀態...");

            java.util.List<dev.robothanzo.werewolf.database.documents.Session.Player> playersList = new java.util.ArrayList<>(
                    session.getPlayers().values());
            playersList.sort(java.util.Comparator
                    .comparingInt(dev.robothanzo.werewolf.database.documents.Session.Player::getId));

            java.util.List<dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask> tasks = new java.util.ArrayList<>();

            for (var player : playersList) {
                var member = pending.get(player.getId() - 1);

                // 1. Prepare Discord Role Task
                var playerRole = guild.getRoleById(player.getRoleId());
                if (playerRole != null) {
                    tasks.add(new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(
                            guild.addRoleToMember(member, playerRole),
                            "已套用身分組: " + playerRole.getName() + " 給 " + member.getEffectiveName()));
                }

                // 2. Logic for role selection (JinBaoBao, etc.)
                java.util.List<String> rs = new java.util.LinkedList<>();
                boolean isJinBaoBao = false;
                rs.add(roles.removeFirst());

                if (rs.getFirst().equals("白癡")) {
                    player.setIdiot(true);
                }

                if (rs.getFirst().equals("平民") && gaveJinBaoBao == 0 && session.isDoubleIdentities()) {
                    rs = new java.util.LinkedList<>(java.util.List.of("平民", "平民"));
                    roles.remove("平民");
                    gaveJinBaoBao++;
                    isJinBaoBao = true;
                } else if (session.isDoubleIdentities()) {
                    boolean shouldRemove = true;
                    rs.add(roles.getFirst());
                    if (rs.contains("複製人")) {
                        player.setDuplicated(true);
                        if (rs.getFirst().equals("複製人")) {
                            rs.set(0, rs.get(1));
                        } else {
                            rs.set(1, rs.getFirst());
                        }
                    }
                    if (rs.getFirst().equals("平民") && rs.get(1).equals("平民")) {
                        if (gaveJinBaoBao >= 2) {
                            for (String r : new java.util.ArrayList<>(roles)) {
                                if (!r.equals("平民")) {
                                    rs.set(1, r);
                                    roles.remove(r);
                                    shouldRemove = false;
                                    break;
                                }
                            }
                        }
                        if (rs.getFirst().equals("平民") && rs.get(1).equals("平民")) {
                            isJinBaoBao = true;
                            gaveJinBaoBao++;
                        }
                    }
                    if (rs.getFirst().contains("狼")) {
                        java.util.Collections.reverse(rs);
                    }
                    if (shouldRemove)
                        roles.removeFirst();
                }

                player.setJinBaoBao(isJinBaoBao && session.isDoubleIdentities());
                player.setRoles(rs);
                player.setDeadRoles(new java.util.ArrayList<>());
                player.setUserId(member.getIdLong());

                // 3. Prepare Nickname Task
                String newNickname = player.getNickname();
                if (!member.getEffectiveName().equals(newNickname)) {
                    tasks.add(new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(
                            member.modifyNickname(newNickname),
                            "已更新暱稱: " + newNickname));
                }

                if (statusLogger != null)
                    statusLogger.accept("  - 已分配身分: " + String.join(", ", rs) + (player.isJinBaoBao() ? " (金寶寶)" : ""));

                // 4. Send Channel Message
                var playerChannel = guild.getTextChannelById(player.getChannelId());
                if (playerChannel != null) {
                    var embed = new net.dv8tion.jda.api.EmbedBuilder()
                            .setTitle("你抽到的身分是 (若為狼人或金寶寶請使用自己的頻道來和隊友討論及確認身分)")
                            .setDescription(String.join("、", rs) + (player.isJinBaoBao() ? " (金寶寶)" : "") +
                                    (player.isDuplicated() ? " (複製人)" : ""))
                            .setColor(dev.robothanzo.werewolf.utils.MsgUtils.getRandomColor());

                    var action = playerChannel.sendMessageEmbeds(embed.build());

                    if (session.isDoubleIdentities()) {
                        action.setComponents(
                                net.dv8tion.jda.api.components.actionrow.ActionRow.of(
                                        net.dv8tion.jda.api.components.buttons.Button.primary("changeRoleOrder",
                                                "更換身分順序 (請在收到身分後全分聽完再使用，逾時不候)")));
                        dev.robothanzo.werewolf.utils.CmdUtils.schedule(() -> {
                            var innerSession = sessionRepository.findByGuildId(guildId).orElse(null);
                            if (innerSession != null) {
                                var innerPlayer = innerSession.getPlayers().get(String.valueOf(player.getId()));
                                if (innerPlayer != null) {
                                    innerPlayer.setRolePositionLocked(true);
                                    sessionRepository.save(innerSession);
                                }
                            }
                            var ch = jda.getTextChannelById(player.getChannelId());
                            if (ch != null)
                                ch.sendMessage("身分順序已鎖定").queue();
                        }, 120000);
                    }
                    tasks.add(new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(action,
                            "已發送私密頻道訊息予 " + member.getEffectiveName()));
                }
            }

            // 5. Send Summary to Judge and Spectator Channels
            String dashboardUrl = System.getenv().getOrDefault("DASHBOARD_URL", "http://localhost:5173") + "/server/"
                    + guildId;
            EmbedBuilder summaryEmbed = new EmbedBuilder()
                    .setTitle("身分列表")
                    .setColor(dev.robothanzo.werewolf.utils.MsgUtils.getRandomColor());

            List<dev.robothanzo.werewolf.database.documents.Session.Player> sortedPlayers = new ArrayList<>(
                    session.getPlayers().values());
            sortedPlayers
                    .sort(Comparator.comparingInt(dev.robothanzo.werewolf.database.documents.Session.Player::getId));

            for (var p : sortedPlayers) {
                String rolesStr = String.join("、", p.getRoles()) +
                        (p.isPolice() ? " (警長)" : "") +
                        (p.isJinBaoBao() ? " (金寶寶)" : p.isDuplicated() ? " (複製人)" : "");
                summaryEmbed.addField(p.getNickname(), rolesStr, true);
            }

            String notificationMsg = "遊戲控制台: " + dashboardUrl;

            // Add notification tasks
            long[] notifyChannelIds = {session.getJudgeTextChannelId(), session.getSpectatorTextChannelId()};
            for (long channelId : notifyChannelIds) {
                if (channelId != 0) {
                    TextChannel channel = guild.getTextChannelById(channelId);
                    if (channel != null) {
                        tasks.add(new dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask(
                                channel.sendMessage(notificationMsg).setEmbeds(summaryEmbed.build()),
                                "已發送身分列表與控制台連結至頻道: " + channel.getName()));
                    }
                }
            }

            if (!tasks.isEmpty()) {
                if (statusLogger != null)
                    statusLogger.accept("正在執行 Discord 變更 (共 " + tasks.size() + " 項)...");
                dev.robothanzo.werewolf.utils.DiscordActionRunner.runActions(tasks, statusLogger, progressCallback, 10,
                        95, 60);
            } else {
                if (statusLogger != null)
                    statusLogger.accept("沒有偵測到需要執行的 Discord 變更。");
                if (progressCallback != null)
                    progressCallback.accept(95);
            }

            // Final Update session flags and logs
            session.setHasAssignedRoles(true);
            session.addLog(dev.robothanzo.werewolf.database.documents.LogType.ROLE_ASSIGNED, "身分分配完成", null);
            sessionRepository.save(session);

            if (progressCallback != null)
                progressCallback.accept(100);
            if (statusLogger != null)
                statusLogger.accept("身分分配完成！");

            gameSessionService.broadcastUpdate(guildId);
        } catch (Exception e) {
            log.error("Failed to assign roles: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to assign roles", e);
        }
    }
}
