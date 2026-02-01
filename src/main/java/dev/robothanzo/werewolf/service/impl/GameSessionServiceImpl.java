package dev.robothanzo.werewolf.service.impl;

import dev.robothanzo.werewolf.WerewolfApplication;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.database.documents.UserRole;
import dev.robothanzo.werewolf.security.GlobalWebSocketHandler;
import dev.robothanzo.werewolf.security.SessionRepository;
import dev.robothanzo.werewolf.service.DiscordService;
import dev.robothanzo.werewolf.service.GameSessionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameSessionServiceImpl implements GameSessionService {

    private final SessionRepository sessionRepository;
    private final DiscordService discordService;
    private final GlobalWebSocketHandler webSocketHandler;
    private final dev.robothanzo.werewolf.service.SpeechService speechService;

    @PostConstruct
    public void init() {
        WerewolfApplication.gameSessionService = this;
    }

    @Override
    public List<Session> getAllSessions() {
        return sessionRepository.findAll();
    }

    @Override
    public Optional<Session> getSession(long guildId) {
        return sessionRepository.findByGuildId(guildId);
    }

    @Override
    public Session createSession(long guildId) {
        Session session = Session.builder()
                .guildId(guildId)
                .build();
        return sessionRepository.save(session);
    }

    @Override
    public Session saveSession(Session session) {
        return sessionRepository.save(session);
    }

    @Override
    public void deleteSession(long guildId) {
        sessionRepository.deleteByGuildId(guildId);
    }

    @Override
    public Map<String, Object> sessionToJSON(Session session) {
        Map<String, Object> json = new java.util.LinkedHashMap<>();
        var jda = discordService.getJDA();

        json.put("guildId", String.valueOf(session.getGuildId()));
        json.put("doubleIdentities", session.isDoubleIdentities());
        json.put("muteAfterSpeech", session.isMuteAfterSpeech());
        json.put("hasAssignedRoles", session.isHasAssignedRoles());
        json.put("roles", session.getRoles());
        json.put("players", playersToJSON(session));

        // Add speech info if available
        if (speechService.getSpeechSession(session.getGuildId()) != null) {
            var speechSession = speechService.getSpeechSession(session.getGuildId());
            Map<String, Object> speechJson = new java.util.LinkedHashMap<>();

            List<String> orderIds = new java.util.ArrayList<>();
            for (Session.Player p : speechSession.getOrder()) {
                orderIds.add(String.valueOf(p.getId()));
            }
            speechJson.put("order", orderIds);

            if (speechSession.getLastSpeaker() != null) {
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

            List<String> interruptVotes = new java.util.ArrayList<>();
            for (Long uid : speechSession.getInterruptVotes()) {
                interruptVotes.add(String.valueOf(uid));
            }
            speechJson.put("interruptVotes", interruptVotes);

            json.put("speech", speechJson);
        }

        // Add Police/Poll info
        Map<String, Object> policeJson = new java.util.LinkedHashMap<>();
        long gid = session.getGuildId();

        var policeSession = WerewolfApplication.policeService.getSessions().get(gid);
        if (policeSession != null) {
            policeJson.put("state", policeSession.getState().name());
            policeJson.put("stageEndTime", policeSession.getStageEndTime());
            policeJson.put("allowEnroll", policeSession.getState().canEnroll());
            policeJson.put("allowUnEnroll", policeSession.getState().canQuit());

            List<Map<String, Object>> candidatesList = new java.util.ArrayList<>();
            for (var c : policeSession.getCandidates().values()) {
                Map<String, Object> candidateJson = new java.util.LinkedHashMap<>();
                candidateJson.put("id", String.valueOf(c.getPlayer().getId()));
                candidateJson.put("quit", c.isQuit());
                List<String> voters = new java.util.ArrayList<>();
                for (Long voterId : c.getElectors()) {
                    voters.add(String.valueOf(voterId));
                }
                candidateJson.put("voters", voters);
                candidatesList.add(candidateJson);
            }
            policeJson.put("candidates", candidatesList);
        } else {
            policeJson.put("state", "NONE");
            policeJson.put("allowEnroll", false);
            policeJson.put("allowUnEnroll", false);
            policeJson.put("candidates", java.util.Collections.emptyList());
        }
        json.put("police", policeJson);

        // Add Expel info
        Map<String, Object> expelJson = new java.util.LinkedHashMap<>();
        if (dev.robothanzo.werewolf.commands.Poll.expelCandidates.containsKey(gid)) {
            List<Map<String, Object>> expelCandidatesList = new java.util.ArrayList<>();
            for (var c : dev.robothanzo.werewolf.commands.Poll.expelCandidates.get(gid).values()) {
                Map<String, Object> candidateJson = new java.util.LinkedHashMap<>();
                candidateJson.put("id", String.valueOf(c.getPlayer().getId()));
                List<String> voters = new java.util.ArrayList<>();
                for (Long voterId : c.getElectors()) {
                    voters.add(String.valueOf(voterId));
                }
                candidateJson.put("voters", voters);
                expelCandidatesList.add(candidateJson);
            }
            expelJson.put("candidates", expelCandidatesList);
            expelJson.put("voting", true);
        } else {
            expelJson.put("candidates", java.util.Collections.emptyList());
            expelJson.put("voting", false);
        }
        json.put("expel", expelJson);

        if (jda != null) {
            Guild guild = jda.getGuildById(session.getGuildId());
            if (guild != null) {
                json.put("guildName", guild.getName());
                json.put("guildIcon", guild.getIconUrl());
            }
        }

        List<Map<String, Object>> logsJson = new java.util.ArrayList<>();
        if (session.getLogs() != null) {
            for (Session.LogEntry log : session.getLogs()) {
                Map<String, Object> logJson = new java.util.LinkedHashMap<>();
                logJson.put("id", log.getId());
                logJson.put("timestamp", formatTimestamp(log.getTimestamp()));
                logJson.put("type", log.getType().getSeverity());
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

    private String formatTimestamp(long epochMillis) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(epochMillis);
        java.time.ZoneId zoneId = java.time.ZoneId.systemDefault();
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, zoneId);
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        return dateTime.format(formatter);
    }

    @Override
    public List<Map<String, Object>> playersToJSON(Session session) {
        List<Map<String, Object>> players = new java.util.ArrayList<>();
        var jda = discordService.getJDA();

        for (Map.Entry<String, Session.Player> entry : session.getPlayers().entrySet()) {
            Session.Player player = entry.getValue();
            Map<String, Object> playerJson = new java.util.LinkedHashMap<>();

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
            if (jda != null && player.getUserId() != null) {
                Guild guild = jda.getGuildById(session.getGuildId());
                if (guild != null) {
                    Member member = guild.getMemberById(player.getUserId());
                    if (member != null) {
                        playerJson.put("name", player.getNickname());
                        playerJson.put("username", member.getUser().getName());
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

        players.sort((a, b) -> {
            int idA = Integer.parseInt((String) a.get("id"));
            int idB = Integer.parseInt((String) b.get("id"));
            return Integer.compare(idA, idB);
        });

        return players;
    }

    @Override
    public Map<String, Object> sessionToSummaryJSON(Session session) {
        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("guildId", String.valueOf(session.getGuildId()));

        String guildName = "Unknown Server";
        String guildIcon = null;
        try {
            net.dv8tion.jda.api.entities.Guild guild = discordService.getGuild(session.getGuildId());
            if (guild != null) {
                guildName = guild.getName();
                guildIcon = guild.getIconUrl();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch guild info for summary: {}", session.getGuildId());
        }

        summary.put("guildName", guildName);
        summary.put("guildIcon", guildIcon);

        int pCount = session.getPlayers().size();
        summary.put("playerCount", pCount);
        log.info("Summary for guild {}: name='{}', players={}", session.getGuildId(), guildName, pCount);

        return summary;
    }

    @Override
    public List<Map<String, Object>> getGuildMembers(long guildId) throws Exception {
        Session session = sessionRepository.findByGuildId(guildId)
                .orElseThrow(() -> new Exception("Session not found"));

        var jda = discordService.getJDA();
        if (jda == null) {
            throw new Exception("JDA instance is required");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        List<Map<String, Object>> membersJson = new java.util.ArrayList<>();

        for (Member member : guild.getMembers()) {
            if (member.getUser().isBot())
                continue;

            Map<String, Object> memberMap = new java.util.LinkedHashMap<>();
            memberMap.put("userId", String.valueOf(member.getIdLong()));
            memberMap.put("username", member.getUser().getName());
            memberMap.put("name", member.getEffectiveName());
            memberMap.put("avatar", member.getEffectiveAvatarUrl());

            boolean isJudge = member.getRoles().stream()
                    .anyMatch(r -> r.getIdLong() == session.getJudgeRoleId());
            memberMap.put("isJudge", isJudge);

            boolean isPlayer = session.getPlayers().values().stream()
                    .anyMatch(p -> p.getUserId() != null && p.getUserId() == member.getIdLong() && p.isAlive());
            memberMap.put("isPlayer", isPlayer);

            membersJson.add(memberMap);
        }

        membersJson.sort((a, b) -> {
            boolean judgeA = (boolean) a.get("isJudge");
            boolean judgeB = (boolean) b.get("isJudge");
            if (judgeA != judgeB)
                return judgeB ? 1 : -1;
            return ((String) a.get("name")).compareTo((String) b.get("name"));
        });

        return membersJson;
    }

    @Override
    public void updateUserRole(long guildId, long userId, UserRole role)
            throws Exception {
        Session session = sessionRepository.findByGuildId(guildId)
                .orElseThrow(() -> new Exception("Session not found"));

        var jda = discordService.getJDA();
        if (jda == null) {
            throw new Exception("JDA instance is required");
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new Exception("Guild not found");
        }

        Member member = guild.getMemberById(userId);
        if (member == null) {
            member = guild.retrieveMemberById(userId).complete();
        }

        net.dv8tion.jda.api.entities.Role judgeRole = guild.getRoleById(session.getJudgeRoleId());
        if (judgeRole == null) {
            throw new Exception("Judge role not configured or found in guild");
        }

        if (role == UserRole.JUDGE) {
            guild.addRoleToMember(member, judgeRole).complete();
        } else if (role == UserRole.SPECTATOR) {
            guild.removeRoleFromMember(member, judgeRole).complete();

            net.dv8tion.jda.api.entities.Role spectatorRole = guild.getRoleById(session.getSpectatorRoleId());
            if (spectatorRole != null) {
                guild.addRoleToMember(member, spectatorRole).complete();
            }
        } else {
            throw new Exception("Unsupported role update: " + role);
        }
    }

    @Override
    public void updateSettings(long guildId, Map<String, Object> settings) throws Exception {
        Session session = sessionRepository.findByGuildId(guildId)
                .orElseThrow(() -> new Exception("Session not found"));

        if (settings.containsKey("doubleIdentities")) {
            session.setDoubleIdentities((Boolean) settings.get("doubleIdentities"));
        }
        if (settings.containsKey("muteAfterSpeech")) {
            session.setMuteAfterSpeech((Boolean) settings.get("muteAfterSpeech"));
        }

        saveSession(session);
        broadcastSessionUpdate(session);
    }

    @Override
    public void broadcastUpdate(long guildId) {
        Optional<Session> sessionOpt = getSession(guildId);
        if (sessionOpt.isPresent()) {
            Map<String, Object> updateData = sessionToJSON(sessionOpt.get());
            broadcastEvent("UPDATE", updateData);
        }
    }

    @Override
    public void broadcastSessionUpdate(Session session) {
        if (session != null) {
            broadcastUpdate(session.getGuildId());
        }
    }

    @Override
    public void broadcastEvent(String type, Map<String, Object> data) {
        try {
            // Using jackson to serialize would be better, but for now simple string
            // construction or JSON utils
            // Assuming data is JSON-compatible map?
            // Since we need to send JSON string.
            // Using a simple JSON serialization via standard libs or Jackson if available
            // (Spring Boot has Jackson).
            // Let's rely on standard toString() if it produces valid JSON? No.
            // Map.toString() isn't JSON.
            // We need ObjectMapper.

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            // Wrap in event structure
            // We broadcast the event in an envelope: { "type": type, "data": data }
            // Let's assume we just broadcast the raw JSON object or an envelope.
            // Assuming envelope: { "type": type, "data": data }

            Map<String, Object> envelope = Map.of("type", type, "data", data);
            String jsonMessage = mapper.writeValueAsString(envelope);

            webSocketHandler.broadcast(jsonMessage);
        } catch (Exception e) {
            log.error("Failed to broadcast event", e);
        }
    }
}
