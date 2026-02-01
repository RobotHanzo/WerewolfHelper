package dev.robothanzo.werewolf.database.documents;

import com.mongodb.client.MongoCollection;
import dev.robothanzo.werewolf.database.Database;
import lombok.*;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static com.mongodb.client.model.Filters.eq;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sessions")
public class Session {
    @Id
    @BsonId
    @BsonRepresentation(BsonType.OBJECT_ID)
    private String id;

    @Indexed(unique = true)
    private long guildId;
    private long courtTextChannelId;
    private long courtVoiceChannelId;
    private long spectatorTextChannelId;
    private long judgeTextChannelId;
    private long judgeRoleId;
    private long spectatorRoleId;
    private long owner;
    private boolean doubleIdentities;
    @Builder.Default
    private boolean hasAssignedRoles = false;
    @Builder.Default
    private boolean muteAfterSpeech = true;
    @Builder.Default
    private List<String> roles = new LinkedList<>();
    @Builder.Default
    private Map<String, Player> players = new HashMap<>();
    @Builder.Default
    private List<LogEntry> logs = new ArrayList<>();

    public Map<String, Player> fetchAlivePlayers() {
        Map<String, Player> alivePlayers = new HashMap<>();
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            if (entry.getValue().isAlive()) {
                alivePlayers.put(entry.getKey(), entry.getValue());
            }
        }
        return alivePlayers;
    }

    public static MongoCollection<Session> fetchCollection() {
        return Database.database.getCollection("sessions", Session.class);
    }

    @Nullable
    public Player getPolice() {
        for (var player : players.values()) {
            if (player.isPolice())
                return player;
        }
        return null;
    }

    public Result hasEnded(@Nullable String simulateRoleRemoval) {
        float wolves = 0;
        float gods = 0;
        float villagers = 0;
        float jinBaoBao = 0;
        boolean policeOnWolf = false;
        boolean policeOnGood = false;
        for (var player : players.values()) {
            if (player.isJinBaoBao())
                jinBaoBao++;
            for (var role : Objects.requireNonNull(player.getRoles())) {
                if (role.equals(simulateRoleRemoval)) {
                    simulateRoleRemoval = null;
                    continue;
                }
                // Skip dead roles
                if (player.getDeadRoles() != null && player.getDeadRoles().contains(role)) {
                    continue;
                }

                if (Player.isWolf(role)) {
                    wolves++;
                    if (player.isPolice())
                        policeOnWolf = true;
                } else if (Player.isGod(role) || (player.isDuplicated() && player.getRoles().size() > 1)) {
                    gods++;
                    if (player.isPolice())
                        policeOnGood = true;
                } else if (Player.isVillager(role)) {
                    villagers++;
                    if (player.isPolice())
                        policeOnGood = true;
                }
            }
        }
        if (gods == 0)
            return Result.GODS_DIED;
        if (wolves == 0)
            return Result.WOLVES_DIED;
        if (doubleIdentities) {
            if (jinBaoBao == 0)
                return Result.JIN_BAO_BAO_DIED;
        } else {
            if (villagers == 0 && roles.contains("平民")) // support for an all gods game
                return Result.VILLAGERS_DIED;
        }
        if (policeOnGood)
            villagers += 0.5f;
        if (policeOnWolf)
            wolves += 0.5f;
        if ((wolves >= gods + villagers) && !doubleIdentities) // we don't do equal players ending in double identities,
            // too annoying
            return Result.EQUAL_PLAYERS;
        return Result.NOT_ENDED;
    }

    @AllArgsConstructor
    @Getter
    public enum Result {
        NOT_ENDED("未結束"),
        VILLAGERS_DIED("全部村民死亡"),
        GODS_DIED("全部神死亡"),
        WOLVES_DIED("全部狼死亡"),
        JIN_BAO_BAO_DIED("全部金寶寶死亡"),
        EQUAL_PLAYERS("狼人陣營人數=好人陣營人數");

        private final String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Player implements Comparable<Player> {
        public static final NumberFormat ID_FORMAT = new DecimalFormat("00");
        private int id;
        private long roleId;
        private long channelId;
        @Builder.Default
        private boolean jinBaoBao = false;
        @Builder.Default
        private boolean duplicated = false;
        @Builder.Default
        private boolean idiot = false;
        @Builder.Default
        private boolean police = false;
        @Builder.Default
        private boolean rolePositionLocked = false;
        @Nullable
        private Long userId;
        @Nullable
        @Builder.Default
        private List<String> roles = new LinkedList<>(); // stuff like wolf, villager...etc
        @Nullable
        @Builder.Default
        private List<String> deadRoles = new LinkedList<>();

        public boolean isAlive() {
            if (roles == null || roles.isEmpty())
                return false;
            if (deadRoles == null)
                return true;
            return deadRoles.size() < roles.size();
        }

        private static boolean isGod(String role) {
            return (!isWolf(role)) && (!isVillager(role));
        }

        private static boolean isWolf(String role) {
            return role.contains("狼") || role.equals("石像鬼") || role.equals("血月使者") || role.equals("惡靈騎士");
        }

        private static boolean isVillager(String role) {
            return role.equals("平民");
        }

        public int compareTo(@NotNull Session.Player o) {
            return Integer.compare(id, o.id);
        }

        public String getNickname() {
            StringBuilder sb = new StringBuilder();

            if (!isAlive()) {
                sb.append("[死人] ");
            }

            sb.append("玩家").append(ID_FORMAT.format(id));

            if (isPolice()) {
                sb.append(" [警長]");
            }

            return sb.toString();
        }

        public void updateNickname(net.dv8tion.jda.api.entities.Member member) {
            if (member == null)
                return;

            String newName = getNickname();
            if (!member.getEffectiveName().equals(newName)) {
                member.modifyNickname(newName).queue();
            }
        }
    }

    /**
     * Audit log entry for tracking game events
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogEntry {
        private String id;
        private long timestamp;
        private LogType type;
        private String message;
        private Map<String, Object> metadata;
    }

    /**
     * Add a log entry to the session
     */
    public void addLog(LogType type, String message) {
        addLog(type, message, null);
    }

    /**
     * Add a log entry with metadata to the session
     */
    public void addLog(LogType type, String message, Map<String, Object> metadata) {
        LogEntry entry = LogEntry.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .type(type)
                .message(message)
                .metadata(metadata)
                .build();
        logs.add(entry);

        // Persist to database
        fetchCollection().updateOne(
                eq("guildId", guildId),
                com.mongodb.client.model.Updates.push("logs", entry));
    }
}
