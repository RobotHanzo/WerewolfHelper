package dev.robothanzo.werewolf.server;

import com.mongodb.client.model.Updates;
import dev.robothanzo.werewolf.commands.Speech;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.utils.CmdUtils;
import dev.robothanzo.werewolf.utils.DiscordActionRunner;
import dev.robothanzo.werewolf.utils.DiscordActionRunner.ActionTask;
import dev.robothanzo.werewolf.utils.MsgUtils;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.*;
import java.util.function.Consumer;

import static com.mongodb.client.model.Filters.eq;

/**
 * Helper class to convert Session objects to JSON for API
 * and trigger existing command logic
 */
@Slf4j
public class SessionAPI {

    /**
     * Convert Session to JSON format for frontend
     */
    public static Map<String, Object> toJSON(Session session, JDA jda) {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("guildId", String.valueOf(session.getGuildId()));
        json.put("doubleIdentities", session.isDoubleIdentities());
        json.put("muteAfterSpeech", session.isMuteAfterSpeech());
        json.put("hasAssignedRoles", session.isHasAssignedRoles());
        json.put("roles", session.getRoles());
        json.put("players", playersToJSON(session, jda));

        // Add speech info if available
        if (Speech.speechSessions.containsKey(session.getGuildId())) {
            Speech.SpeechSession speechSession = Speech.speechSessions.get(session.getGuildId());
            Map<String, Object> speechJson = new LinkedHashMap<>();

            List<String> orderIds = new ArrayList<>();
            for (Session.Player p : speechSession.getOrder()) {
                orderIds.add(String.valueOf(p.getId()));
            }
            speechJson.put("order", orderIds);

            if (speechSession.getLastSpeaker() != null) {
                // Find player by user ID to get internal ID
                String speakerId = null;
                for (Session.Player p : session.getPlayers().values()) {
                    if (p.getUserId() != null && p.getUserId().equals(speechSession.getLastSpeaker())) {
                        speakerId = String.valueOf(p.getId());
                        break;
                    }
                }
                speechJson.put("currentSpeakerId", speakerId);
            }

            speechJson.put("endTime", speechSession.getCurrentSpeechEndTime());
            speechJson.put("totalTime", speechSession.getTotalSpeechTime());

            List<String> interruptVotes = new ArrayList<>();
            for (Long uid : speechSession.getInterruptVotes()) {
                interruptVotes.add(String.valueOf(uid));
            }
            speechJson.put("interruptVotes", interruptVotes);

            json.put("speech", speechJson);
        }

        // Add Police/Poll info
        Map<String, Object> policeJson = new LinkedHashMap<>();
        long gid = session.getGuildId();

        boolean allowEnroll = dev.robothanzo.werewolf.commands.Poll.Police.allowEnroll.getOrDefault(gid, false);
        boolean allowUnEnroll = dev.robothanzo.werewolf.commands.Poll.Police.allowUnEnroll.getOrDefault(gid, false);
        policeJson.put("allowEnroll", allowEnroll);
        policeJson.put("allowUnEnroll", allowUnEnroll);

        List<String> candidatesList = new ArrayList<>();
        if (dev.robothanzo.werewolf.commands.Poll.Police.candidates.containsKey(gid)) {
            for (dev.robothanzo.werewolf.commands.Poll.Candidate c : dev.robothanzo.werewolf.commands.Poll.Police.candidates.get(gid).values()) {
                if (!c.isQuit()) {
                    candidatesList.add(String.valueOf(c.getPlayer().getId()));
                }
            }
        }
        policeJson.put("candidates", candidatesList);
        json.put("police", policeJson);

        // Add guild info if available
        if (jda != null) {
            Guild guild = jda.getGuildById(session.getGuildId());
            if (guild != null) {
                json.put("guildName", guild.getName());
                json.put("guildIcon", guild.getIconUrl());
            }
        }

        // Add logs
        List<Map<String, Object>> logsJson = new ArrayList<>();
        if (session.getLogs() != null) {
            for (Session.LogEntry log : session.getLogs()) {
                Map<String, Object> logJson = new LinkedHashMap<>();
                logJson.put("id", log.getId());
                logJson.put("timestamp", formatTimestamp(log.getTimestamp()));
                logJson.put("type", log.getType().getSeverity()); // Use severity for UI type
                logJson.put("message", log.getMessage());
                if (log.getMetadata() != null && !log.getMetadata().isEmpty()) {
                    logJson.put("metadata", log.getMetadata());
                }
                logsJson.add(logJson);
            }
        }
        json.put("logs", logsJson);

        return json;
    }

    /**
     * Format timestamp to HH:mm:ss
     */
    private static String formatTimestamp(long epochMillis) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(epochMillis);
        java.time.ZoneId zoneId = java.time.ZoneId.systemDefault();
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, zoneId);
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        return dateTime.format(formatter);
    }

    /**
     * Convert players map to JSON array
     */
    public static List<Map<String, Object>> playersToJSON(Session session, JDA jda) {
        List<Map<String, Object>> players = new ArrayList<>();

        for (Map.Entry<String, Session.Player> entry : session.getPlayers().entrySet()) {
            Session.Player player = entry.getValue();
            Map<String, Object> playerJson = new LinkedHashMap<>();

            playerJson.put("id", String.valueOf(player.getId()));
            playerJson.put("roleId", String.valueOf(player.getRoleId()));
            playerJson.put("channelId", String.valueOf(player.getChannelId()));
            playerJson.put("userId", player.getUserId() != null ? String.valueOf(player.getUserId()) : null);
            playerJson.put("roles", player.getRoles());
            playerJson.put("deadRoles", player.getDeadRoles());
            playerJson.put("isAlive", player.isAlive());
            playerJson.put("jinBaoBao", player.isJinBaoBao());
            playerJson.put("police", player.isPolice());
            playerJson.put("idiot", player.isIdiot());
            playerJson.put("duplicated", player.isDuplicated());
            playerJson.put("rolePositionLocked", player.isRolePositionLocked());

            boolean foundMember = false;
            // Add Discord user info if available
            if (jda != null && player.getUserId() != null) {
                Guild guild = jda.getGuildById(session.getGuildId());
                if (guild != null) {
                    Member member = guild.getMemberById(player.getUserId());
                    if (member != null) {
                        playerJson.put("name", player.getNickname()); // Generated nickname
                        playerJson.put("username", member.getUser().getName()); // Discord username
                        playerJson.put("avatar", member.getEffectiveAvatarUrl());

                        boolean isJudge = member.getRoles().stream()
                                .anyMatch(r -> r.getIdLong() == session.getJudgeRoleId());
                        playerJson.put("isJudge", isJudge);

                        foundMember = true;
                    }
                }
            }
            if (!foundMember) {
                playerJson.put("name", player.getNickname());
                playerJson.put("username", null);
                playerJson.put("avatar", null);
                playerJson.put("isJudge", false);
            }

            players.add(playerJson);
        }

        // Sort by player ID
        players.sort((a, b) -> {
            int idA = Integer.parseInt((String) a.get("id"));
            int idB = Integer.parseInt((String) b.get("id"));
            return Integer.compare(idA, idB);
        });

        return players;
    }

    /**
     * Assign roles to players (matching /player assign logic)
     */
    public static void assignRoles(long guildId, JDA jda, Consumer<String> statusLogger, Consumer<Integer> progressCallback) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        if (session.isHasAssignedRoles()) {
            throw new Exception("Roles already assigned");
        }

        int totalPlayers = session.getPlayers().size();
        System.out.println("Starting role assignment for guild " + guildId + " with " + totalPlayers + " players");

        if (progressCallback != null) progressCallback.accept(5);
        if (statusLogger != null) statusLogger.accept("正在掃描伺服器玩家...");

        Guild guild = jda != null ? jda.getGuildById(guildId) : null;
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        List<Member> pending = new LinkedList<>();
        for (Member member : guild.getMembers()) {
            if ((!member.getUser().isBot()) &&
                    (!member.getRoles().contains(guild.getRoleById(session.getJudgeRoleId()))) &&
                    (!member.getRoles().contains(guild.getRoleById(session.getSpectatorRoleId())))) {
                pending.add(member);
            }
        }

        if (progressCallback != null) progressCallback.accept(10);
        if (statusLogger != null) statusLogger.accept("正在驗證玩家與身分數量...");

        Collections.shuffle(pending, new Random());
        if (pending.size() != session.getPlayers().size()) {
            throw new Exception("玩家數量不符合設定值。請確認是否已給予旁觀者應有之身分(使用 `/player died`)，或檢查 `/server set players` 設定的人數。\n(待分配: " + pending.size() + ", 需要: " + session.getPlayers().size() + ")");
        }

        int rolesPerPlayer = session.isDoubleIdentities() ? 2 : 1;
        if (pending.size() != (session.getRoles().size() / rolesPerPlayer)) {
            throw new Exception("玩家身分數量不符合身分清單數量。請確認是否正確啟用雙身分模式，並檢查 `/server roles list`。\n(目前玩家: " + pending.size() + ", 身分總數: " + session.getRoles().size() + ")");
        }

        List<String> roles = new ArrayList<>(session.getRoles());
        Collections.shuffle(roles);

        int gaveJinBaoBao = 0;
        int processedCount = 0;
        totalPlayers = session.getPlayers().size();

        if (statusLogger != null) statusLogger.accept("正在分配身分並更新伺服器狀態...");

        List<Session.Player> playersList = new ArrayList<>(session.getPlayers().values());
        playersList.sort(Comparator.comparingInt(Session.Player::getId));

        List<ActionTask> tasks = new ArrayList<>();

        for (Session.Player player : playersList) {
            Member member = pending.get(player.getId() - 1);

            // 1. Prepare Discord Role Task
            Role playerRole = guild.getRoleById(player.getRoleId());
            if (playerRole != null) {
                tasks.add(new ActionTask(guild.addRoleToMember(member, playerRole),
                        "已套用身分組: " + playerRole.getName() + " 給 " + member.getEffectiveName()));
            }

            // 2. Logic for role selection (JinBaoBao, etc.)
            List<String> rs = new LinkedList<>();
            boolean isJinBaoBao = false;
            rs.add(roles.removeFirst());

            if (rs.getFirst().equals("白癡")) {
                player.setIdiot(true);
            }

            if (rs.getFirst().equals("平民") && gaveJinBaoBao == 0 && session.isDoubleIdentities()) {
                rs = new LinkedList<>(List.of("平民", "平民"));
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
                        for (String r : new ArrayList<>(roles)) {
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
                    Collections.reverse(rs);
                }
                if (shouldRemove)
                    roles.removeFirst();
            }

            player.setJinBaoBao(isJinBaoBao && session.isDoubleIdentities());
            player.setRoles(rs);
            player.setDeadRoles(new ArrayList<>());
            player.setUserId(member.getIdLong());

            // 3. Prepare Nickname Task (Now that roles are set and player is 'alive')
            String newNickname = player.getNickname();
            if (!member.getEffectiveName().equals(newNickname)) {
                tasks.add(new ActionTask(member.modifyNickname(newNickname),
                        "已更新暱稱: " + newNickname));
            }

            final List<String> finalRs = rs;
            if (statusLogger != null)
                statusLogger.accept("  - 已分配身分: " + String.join(", ", finalRs) + (player.isJinBaoBao() ? " (金寶寶)" : ""));

            // 4. Send Channel Message
            net.dv8tion.jda.api.entities.channel.concrete.TextChannel playerChannel = guild.getTextChannelById(player.getChannelId());
            if (playerChannel != null) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("你抽到的身分是 (若為狼人或金寶寶請使用自己的頻道來和隊友討論及確認身分)")
                        .setDescription(String.join("、", rs) + (player.isJinBaoBao() ? " (金寶寶)" : "") +
                                (player.isDuplicated() ? " (複製人)" : ""))
                        .setColor(MsgUtils.getRandomColor());

                var action = playerChannel.sendMessageEmbeds(embed.build());

                if (session.isDoubleIdentities()) {
                    action.setComponents(ActionRow.of(Button.primary("changeRoleOrder", "更換身分順序 (請在收到身分後全分聽完再使用，逾時不候)")));
                    CmdUtils.schedule(() -> {
                        Session.fetchCollection().updateOne(eq("guildId", guild.getIdLong()),
                                Updates.set("players." + player.getId() + ".rolePositionLocked", true));
                        net.dv8tion.jda.api.entities.channel.concrete.TextChannel ch = guild.getTextChannelById(player.getChannelId());
                        if (ch != null) ch.sendMessage("身分順序已鎖定").queue();
                    }, 120000);
                }
                tasks.add(new ActionTask(action, "已發送私密頻道訊息予 " + member.getEffectiveName()));
            }

            // Sync players map in memory & persistence for safety
            session.getPlayers().put(String.valueOf(player.getId()), player);
            Session.fetchCollection().updateOne(eq("guildId", guildId),
                    Updates.set("players." + player.getId(), player));
        }

        if (tasks.size() > 0) {
            if (statusLogger != null) statusLogger.accept("正在執行 Discord 變更 (共 " + tasks.size() + " 項)...");
            DiscordActionRunner.runActions(tasks, statusLogger, progressCallback, 10, 95, 60);
        } else {
            if (statusLogger != null) statusLogger.accept("沒有偵測到需要執行的 Discord 變更。");
            if (progressCallback != null) progressCallback.accept(95);
        }

        // Add log entry
        session.addLog(dev.robothanzo.werewolf.database.documents.LogType.ROLE_ASSIGNED,
                "身分分配完成", null);

        // Final Update session flags and logs
        Session.fetchCollection().updateOne(
                eq("guildId", guildId),
                Updates.combine(
                        Updates.set("hasAssignedRoles", true),
                        Updates.set("logs", session.getLogs())
                )
        );

        if (progressCallback != null) progressCallback.accept(100);
        if (statusLogger != null) statusLogger.accept("身分分配完成！");
    }

    /**
     * Mark a player as dead (uses existing command logic)
     */
    public static void markPlayerDead(long guildId, long userId, boolean lastWords, JDA jda) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        if (jda == null) {
            throw new Exception("JDA instance is required for this operation");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        Member member = guild.getMemberById(userId);
        if (member == null) {
            // Try to retrieve if not in cache (though unlikely for active player)
            try {
                member = guild.retrieveMemberById(userId).complete();
            } catch (Exception e) {
                throw new Exception("Member not found in guild: " + e.getMessage());
            }
        }

        // Use the existing command logic to ensure consistency (messages, game end checks, soft kill)
        // isExpelled is false because this is an admin kill / direct kill, not necessarily a vote expulsion
        boolean success = dev.robothanzo.werewolf.commands.Player.playerDied(session, member, lastWords, false);

        if (!success) {
            throw new Exception("Failed to mark player as dead (Player might already be dead)");
        }
    }

    /**
     * Revive a specific role for a player
     */
    public static void revivePlayerRole(long guildId, long userId, String role, JDA jda) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        if (jda == null) {
            throw new Exception("JDA instance is required for this operation");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        Member member = guild.getMemberById(userId);
        if (member == null) {
            try {
                member = guild.retrieveMemberById(userId).complete();
            } catch (Exception e) {
                throw new Exception("Member not found in guild: " + e.getMessage());
            }
        }

        boolean success = dev.robothanzo.werewolf.commands.Player.playerRevived(session, member, role);

        if (!success) {
            throw new Exception("Failed to revive role (Role might not be dead or found)");
        }
    }

    /**
     * Revive all dead roles for a player
     */
    public static void revivePlayer(long guildId, long userId, JDA jda) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        if (jda == null) {
            throw new Exception("JDA instance is required");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        Member member = guild.getMemberById(userId);
        if (member == null) {
            try {
                member = guild.retrieveMemberById(userId).complete();
            } catch (Exception e) {
                throw new Exception("Member not found in guild: " + e.getMessage());
            }
        }

        // Find player in session to get dead roles
        Session.Player targetPlayer = null;
        for (Session.Player p : session.getPlayers().values()) {
            if (p.getUserId() != null && p.getUserId() == userId) {
                targetPlayer = p;
                break;
            }
        }

        if (targetPlayer == null || targetPlayer.getDeadRoles() == null || targetPlayer.getDeadRoles().isEmpty()) {
            throw new Exception("Player has no dead roles to revive");
        }

        // Revive all dead roles
        // Create a copy to avoid concurrent modification during iteration if logic changes
        List<String> rolesToRevive = new ArrayList<>(targetPlayer.getDeadRoles());
        for (String role : rolesToRevive) {
            dev.robothanzo.werewolf.commands.Player.playerRevived(session, member, role);
            // Refresh session after each revive as playerRevived updates DB
            session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        }

        // Force nickname update after mass revival just in case
        if (targetPlayer != null) {
            targetPlayer.updateNickname(member);
        }
    }

    /**
     * Set a player as police
     */
    public static void setPolice(long guildId, long userId, JDA jda) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        if (jda == null) {
            throw new Exception("JDA instance is required");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        // Remove police from all players and update nickname
        for (Session.Player player : session.getPlayers().values()) {
            if (player.isPolice()) {
                player.setPolice(false);
                if (player.getUserId() != null) {
                    Member member = guild.getMemberById(player.getUserId());
                    if (member != null) player.updateNickname(member);
                }
            }
        }

        // Set new police
        Session.Player targetPlayer = null;
        for (Session.Player player : session.getPlayers().values()) {
            if (player.getUserId() != null && player.getUserId() == userId) {
                player.setPolice(true);
                targetPlayer = player;
                break;
            }
        }

        if (targetPlayer == null) {
            throw new Exception("Player not found");
        }

        // Update new police nickname
        if (targetPlayer.getUserId() != null) {
            Member member = guild.getMemberById(targetPlayer.getUserId());
            if (member != null) {
                targetPlayer.updateNickname(member);

                // Send message to court channel
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = guild.getTextChannelById(session.getCourtTextChannelId());
                if (channel != null) {
                    channel.sendMessage(":white_check_mark: 警徽已移交給 " + member.getAsMention()).queue();
                }
            }
        }

        // Update session
        Session.fetchCollection().updateOne(
                eq("guildId", guildId),
                Updates.set("players", session.getPlayers())
        );
    }

    /**
     * Add role to session's role list
     */
    public static void addRole(long guildId, String role, int amount) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        List<String> roles = new ArrayList<>(session.getRoles());
        for (int i = 0; i < amount; i++) {
            roles.add(role);
        }

        Session.fetchCollection().updateOne(
                eq("guildId", guildId),
                Updates.set("roles", roles)
        );
    }

    /**
     * Remove role from session's role list
     */
    public static void removeRole(long guildId, String role, int amount) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        List<String> roles = new ArrayList<>(session.getRoles());
        for (int i = 0; i < amount; i++) {
            roles.remove(role);
        }

        Session.fetchCollection().updateOne(
                eq("guildId", guildId),
                Updates.set("roles", roles)
        );
    }

    /**
     * Update session settings
     */
    public static void updateSettings(long guildId, Map<String, Object> settings) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        List<org.bson.conversions.Bson> updates = new ArrayList<>();

        if (settings.containsKey("doubleIdentities")) {
            updates.add(Updates.set("doubleIdentities", settings.get("doubleIdentities")));
        }

        if (settings.containsKey("muteAfterSpeech")) {
            updates.add(Updates.set("muteAfterSpeech", settings.get("muteAfterSpeech")));
        }

        if (!updates.isEmpty()) {
            Session.fetchCollection().updateOne(
                    eq("guildId", guildId),
                    Updates.combine(updates)
            );
        }
    }

    /**
     * Update a player's roles
     */
    public static void updatePlayerRoles(long guildId, String playerId, List<String> newRoles, JDA jda) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        Session.Player targetPlayer = session.getPlayers().get(playerId);

        if (targetPlayer == null) {
            throw new Exception("Player not found");
        }

        List<String> finalRoles = new ArrayList<>(newRoles);

        // 1. Handle Duplicated (Copycat) logic
        boolean isDuplicated = newRoles.contains("複製人");
        targetPlayer.setDuplicated(isDuplicated);

        if (isDuplicated && finalRoles.size() == 2) {
            if (finalRoles.get(0).equals("複製人")) {
                finalRoles.set(0, finalRoles.get(1));
            } else if (finalRoles.get(1).equals("複製人")) {
                finalRoles.set(1, finalRoles.get(0));
            }
        }

        // 2. Handle Idiot logic
        boolean isIdiot = finalRoles.contains("白癡");
        targetPlayer.setIdiot(isIdiot);

        // 3. Handle Jin Bao Bao (Golden Baby) logic
        // Two Villagers (平民) -> Jin Bao Bao
        boolean isJinBaoBao = false;
        if (session.isDoubleIdentities() && finalRoles.size() == 2) {
            if (finalRoles.get(0).equals("平民") && finalRoles.get(1).equals("平民")) {
                isJinBaoBao = true;
            }
        }
        targetPlayer.setJinBaoBao(isJinBaoBao);

        targetPlayer.setRoles(finalRoles);

        // Update session
        Session.fetchCollection().updateOne(
                eq("guildId", guildId),
                Updates.set("players", session.getPlayers())
        );

        // Notify user in their channel
        if (jda != null && targetPlayer.getChannelId() != 0) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = guild.getTextChannelById(targetPlayer.getChannelId());
                if (channel != null) {
                    channel.sendMessage("法官已將你的身份更改為: " + String.join(", ", newRoles)).queue();
                }
            }
        }
    }

    /**
     * Switch the order of roles for a player (for double identity)
     */
    public static void switchRoleOrder(long guildId, long userId, JDA jda) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        Session.Player targetPlayer = null;
        for (Session.Player p : session.getPlayers().values()) {
            if (p.getUserId() != null && p.getUserId() == userId) {
                targetPlayer = p;
                break;
            }
        }

        if (targetPlayer == null) {
            throw new Exception("Player not found");
        }

        // check lock
        if (targetPlayer.isRolePositionLocked()) {
            throw new Exception("Role position is locked for this player");
        }

        if (targetPlayer.getRoles() == null || targetPlayer.getRoles().size() < 2) {
            throw new Exception("Player does not have multiple roles to switch");
        }

        // Swap the first two roles
        Collections.swap(targetPlayer.getRoles(), 0, 1);

        // Update session
        Session.fetchCollection().updateOne(
                eq("guildId", guildId),
                Updates.set("players", session.getPlayers())
        );

        // Notify user if needed (optional)
        if (jda != null && targetPlayer.getChannelId() != 0) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = guild.getTextChannelById(targetPlayer.getChannelId());
                if (channel != null) {
                    channel.sendMessage("你已交換了角色順序，現在主要角色為: " + targetPlayer.getRoles().get(0)).queue();
                }
            }
        }
    }

    /**
     * Set role position lock for a player
     */
    public static void setRolePositionLock(long guildId, String playerId, boolean locked) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        Session.Player targetPlayer = session.getPlayers().get(playerId);
        if (targetPlayer == null) {
            throw new Exception("Player not found");
        }

        targetPlayer.setRolePositionLocked(locked);

        Session.fetchCollection().updateOne(
                eq("guildId", guildId),
                Updates.set("players", session.getPlayers())
        );
    }

    /**
     * Start the game (Log game start)
     */
    public static void startGame(long guildId) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        session.addLog(dev.robothanzo.werewolf.database.documents.LogType.GAME_STARTED,
                "遊戲正式開始！", null);

        Session.fetchCollection().updateOne(
                eq("guildId", guildId),
                Updates.set("logs", session.getLogs())
        );
    }

    /**
     * Get all text channel members (potential judges)
     */
    public static List<Map<String, Object>> getGuildMembers(long guildId, JDA jda) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        if (jda == null) {
            throw new Exception("JDA instance is required");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        List<Map<String, Object>> membersJson = new ArrayList<>();

        // This relies on the member cache. Ensure the bot has GUILD_MEMBERS intent and cache is built.
        for (Member member : guild.getMembers()) {
            if (member.getUser().isBot()) continue;

            Map<String, Object> memberMap = new LinkedHashMap<>();
            memberMap.put("userId", String.valueOf(member.getIdLong()));
            memberMap.put("username", member.getUser().getName());
            memberMap.put("name", member.getEffectiveName());
            memberMap.put("avatar", member.getEffectiveAvatarUrl());

            boolean isJudge = member.getRoles().stream()
                    .anyMatch(r -> r.getIdLong() == session.getJudgeRoleId());
            memberMap.put("isJudge", isJudge);

            // Also check if they are an active player
            boolean isPlayer = session.getPlayers().values().stream()
                    .anyMatch(p -> p.getUserId() != null && p.getUserId() == member.getIdLong() && p.isAlive());
            memberMap.put("isPlayer", isPlayer);

            membersJson.add(memberMap);
        }

        // Sort judges first, then alphabetical
        membersJson.sort((a, b) -> {
            boolean judgeA = (boolean) a.get("isJudge");
            boolean judgeB = (boolean) b.get("isJudge");
            if (judgeA != judgeB) return judgeB ? 1 : -1;
            return ((String) a.get("name")).compareTo((String) b.get("name"));
        });

        return membersJson;
    }

    /**
     * Reset session to initial state
     */
    /**
     * Update a user's role (Judge/Spectator) in Discord
     */
    public static void updateUserRole(long guildId, long userId, String roleName, JDA jda) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        if (jda == null) {
            throw new Exception("JDA instance is required");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        Member member = guild.getMemberById(userId);
        if (member == null) {
             try {
                member = guild.retrieveMemberById(userId).complete();
            } catch (Exception e) {
                throw new Exception("Member not found in guild");
            }
        }

        Role judgeRole = guild.getRoleById(session.getJudgeRoleId());
        if (judgeRole == null) {
            throw new Exception("Judge role not configured or found in guild");
        }

        if ("JUDGE".equalsIgnoreCase(roleName)) {
            // Add Judge role
            try {
                guild.addRoleToMember(member, judgeRole).complete();
                System.out.println("Successfully added Judge role (" + judgeRole.getName() + ") to " + member.getEffectiveName());
            } catch (Exception e) {
                log.error("Failed to add Judge role", e);
                throw new Exception("Failed to add Judge role: " + e.getMessage());
            }
            
            // Optionally remove Spectator role if needed, but Judges can usually see everything anyway.
            
        } else if ("SPECTATOR".equalsIgnoreCase(roleName) || "DEMOTE".equalsIgnoreCase(roleName)) {
            // Remove Judge role
            try {
                guild.removeRoleFromMember(member, judgeRole).complete();
                System.out.println("Successfully removed Judge role from " + member.getEffectiveName());
            } catch (Exception e) {
                log.error("Failed to remove Judge role", e);
                throw new Exception("Failed to remove Judge role: " + e.getMessage());
            }
            
            // Ensure they have Spectator role if they are not playing
            Role spectatorRole = guild.getRoleById(session.getSpectatorRoleId());
            if (spectatorRole != null) {
                try {
                    guild.addRoleToMember(member, spectatorRole).complete();
                    System.out.println("Added spectator role to " + member.getEffectiveName());
                } catch (Exception e) {
                    log.error("Failed to add spectator role", e);
                    // Don't fail the whole request if just adding spectator fails, but maybe worth consistent behavior.
                }
            }
        } else {
            throw new Exception("Unknown role type: " + roleName);
        }
    }

    /**
     * Reset session to initial state
     */
    public static void resetSession(long guildId, JDA jda, java.util.function.Consumer<String> statusLogger, java.util.function.Consumer<Integer> progressCallback) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        progressCallback.accept(0);
        statusLogger.accept("正在連線至 Discord...");

        Guild guild = jda != null ? jda.getGuildById(guildId) : null;
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        progressCallback.accept(5);
        statusLogger.accept("正在掃描需要清理的身分組...");

        // Collect all tasks to perform
        List<ActionTask> tasks = new ArrayList<>();

        // 1. Participant Cleanup (Direct targeted removal)
        Role spectatorRole = guild.getRoleById(session.getSpectatorRoleId());

        for (Session.Player player : session.getPlayers().values()) {
            Long currentUserId = player.getUserId();

            // 1. Reset player state in memory
            player.setUserId(null);
            player.setRoles(new ArrayList<>());
            player.setDeadRoles(new ArrayList<>());
            player.setPolice(false);
            player.setIdiot(false);
            player.setJinBaoBao(false);
            player.setDuplicated(false);
            player.setRolePositionLocked(false);

            // 2. Queue Discord cleanup tasks if there was a user
            if (currentUserId != null) {
                net.dv8tion.jda.api.entities.Member member = guild.getMemberById(currentUserId);
                if (member != null) {
                    if (spectatorRole != null) {
                        tasks.add(new ActionTask(guild.removeRoleFromMember(member, spectatorRole),
                                "已移除 " + member.getEffectiveName() + " 的旁觀者身分組"));
                    }

                    Role playerRole = guild.getRoleById(player.getRoleId());
                    if (playerRole != null) {
                        tasks.add(new ActionTask(guild.removeRoleFromMember(member, playerRole),
                                "已移除玩家 " + player.getId() + " (" + member.getEffectiveName() + ") 的玩家身分組"));
                    }

                    if (member.getNickname() != null) {
                        tasks.add(new ActionTask(member.modifyNickname(null),
                                "已重置 " + member.getEffectiveName() + " 的暱稱"));
                    }
                }
            }
        }

        if (tasks.size() > 0) {
            statusLogger.accept("正在執行 Discord 變更 (共 " + tasks.size() + " 項)...");
            DiscordActionRunner.runActions(tasks, statusLogger, progressCallback, 5, 90, 30);
        } else {
            statusLogger.accept("沒有偵測到需要清理的 Discord 變更。");
            progressCallback.accept(90);
        }

        statusLogger.accept("正在更新資料庫並清理日誌...");

        // Clear logs
        session.setLogs(new ArrayList<>());

        // Reset game flags
        session.setHasAssignedRoles(false);

        // Add reset log
        session.addLog(dev.robothanzo.werewolf.database.documents.LogType.GAME_RESET,
                "遊戲已重置", null);

        // Update session in database
        Session.fetchCollection().updateOne(
                eq("guildId", guildId),
                Updates.combine(
                        Updates.set("players", session.getPlayers()),
                        Updates.set("hasAssignedRoles", false),
                        Updates.set("logs", session.getLogs())
                )
        );

        progressCallback.accept(100);
        statusLogger.accept("操作完成。");
    }

    /**
     * Set the player count for the session (resizing game)
     */
    public static void setPlayerCount(long guildId, int count, JDA jda) throws Exception {
        Session session = Session.fetchCollection().find(eq("guildId", guildId)).first();
        if (session == null) {
            throw new Exception("Session not found");
        }

        if (jda == null) {
            throw new Exception("JDA instance is required");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        Map<String, Session.Player> players = session.getPlayers();

        // Remove players if count is smaller
        for (Session.Player player : new LinkedList<>(players.values())) {
            if (player.getId() > count) {
                players.remove(String.valueOf(player.getId()));

                try {
                    Role role = guild.getRoleById(player.getRoleId());
                    if (role != null) role.delete().queue();

                    net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = guild.getTextChannelById(player.getChannelId());
                    if (channel != null) channel.delete().queue();
                } catch (Exception e) {
                    // Ignore errors during deletion (might already be deleted)
                }
            }
        }

        // Add players if count is larger
        Role spectatorRole = guild.getRoleById(session.getSpectatorRoleId());

        for (long i = players.size() + 1; i <= count; i++) {
            Role role = guild.createRole()
                    .setColor(MsgUtils.getRandomColor())
                    .setHoisted(true)
                    .setName("玩家" + Session.Player.ID_FORMAT.format(i))
                    .complete();

            net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = guild.createTextChannel("玩家" + Session.Player.ID_FORMAT.format(i))
                    .addPermissionOverride(spectatorRole != null ? spectatorRole : guild.getPublicRole(),
                            Permission.VIEW_CHANNEL.getRawValue(), Permission.MESSAGE_SEND.getRawValue())
                    .addPermissionOverride(role,
                            List.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND),
                            List.of())
                    .addPermissionOverride(guild.getPublicRole(),
                            List.of(),
                            List.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.USE_APPLICATION_COMMANDS))
                    .complete();

            players.put(String.valueOf(i), Session.Player.builder()
                    .id((int) i)
                    .roleId(role.getIdLong())
                    .channelId(channel.getIdLong())
                    .build());
        }

        Session.fetchCollection().updateOne(
                eq("guildId", guildId),
                Updates.set("players", players)
        );
    }
}
