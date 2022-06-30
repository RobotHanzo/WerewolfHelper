package dev.robothanzo.werewolf.database.documents;

import com.mongodb.client.MongoCollection;
import dev.robothanzo.werewolf.database.Database;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    private long guildId;
    private long courtTextChannelId;
    private long courtVoiceChannelId;
    private long spectatorTextChannelId;
    private long judgeTextChannelId;
    private long judgeRoleId;
    private long spectatorRoleId;
    private boolean doubleIdentities;
    @Builder.Default
    private List<String> roles = new LinkedList<>();
    @Builder.Default
    private Map<String, Player> players = new HashMap<>();

    public static MongoCollection<Session> fetchCollection() {
        return Database.database.getCollection("sessions", Session.class);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Player {
        private int id;
        private long roleId;
        private long channelId;
        @Builder.Default
        private boolean jinBaoBao = false;
        @Builder.Default
        private boolean duplicated = false;
        @Builder.Default
        private boolean police = false;
        @Builder.Default
        private boolean rolePositionLocked = false;
        @Nullable
        private Long userId;
        @Nullable
        @Builder.Default
        private List<String> roles = new LinkedList<>(); // stuff like wolf, villager...etc
    }
}
