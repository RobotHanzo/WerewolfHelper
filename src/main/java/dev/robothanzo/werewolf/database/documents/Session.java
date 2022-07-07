package dev.robothanzo.werewolf.database.documents;

import com.mongodb.client.MongoCollection;
import dev.robothanzo.werewolf.database.Database;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private boolean muteAfterSpeech = true;
    @Builder.Default
    private List<String> roles = new LinkedList<>();
    @Builder.Default
    private Map<String, Player> players = new HashMap<>();

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

    public Result hasEnded(String simulateRoleRemoval) {
        int wolves = 0;
        int gods = 0;
        int villagers = 0;
        int jinBaoBao = 0;
        for (var player : players.values()) {
            if(player.isJinBaoBao())
                jinBaoBao++;
            for (var role : Objects.requireNonNull(player.getRoles())) {
                if (role.equals(simulateRoleRemoval))
                    continue;
                if(Player.isWolf(role))
                    wolves++;
                else if(Player.isGod(role)||(player.isDuplicated()&&player.getRoles().size()>1))
                    gods++;
                else if (Player.isVillager(role))
                    villagers++;
            }
        }
        if (wolves >= gods + villagers&&!doubleIdentities) // we don't do equal players ending in double identities, too annoying
            return Result.EQUAL_PLAYERS;
        if (gods == 0)
            return Result.GODS_DIED;
        if (wolves == 0)
            return Result.WOLVES_DIED;
        if (doubleIdentities) {
            if (jinBaoBao == 0)
                return Result.JIN_BAO_BAO_DIED;
        } else {
            if (villagers == 0&&roles.contains("平民")) //support for an all gods game
                return Result.VILLAGERS_DIED;
        }
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
    public static class Player {
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

        private static boolean isGod(String role) {
            return (!isWolf(role))&&(!isVillager(role));
        }

        private static boolean isWolf(String role) {
            return role.contains("狼") || role.equals("石像鬼") || role.equals("血月使者");
        }

        private static boolean isVillager(String role) {
            return role.equals("平民");
        }
    }
}
