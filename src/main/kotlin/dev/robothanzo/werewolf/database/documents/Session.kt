package dev.robothanzo.werewolf.database.documents

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates
import dev.robothanzo.werewolf.database.Database
import net.dv8tion.jda.api.entities.Member
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonIgnore
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.text.DecimalFormat
import java.util.*

@Document(collection = "sessions")
data class Session(
    @Id
    @BsonId
    var id: ObjectId? = null,

    @Indexed(unique = true)
    var guildId: Long = 0,
    var courtTextChannelId: Long = 0,
    var courtVoiceChannelId: Long = 0,
    var spectatorTextChannelId: Long = 0,
    var judgeTextChannelId: Long = 0,
    var judgeRoleId: Long = 0,
    var spectatorRoleId: Long = 0,
    var owner: Long = 0,
    var doubleIdentities: Boolean = false,
    var hasAssignedRoles: Boolean = false,
    var muteAfterSpeech: Boolean = true,
    var roles: MutableList<String> = LinkedList(),
    var players: MutableMap<String, Player> = HashMap(),
    var logs: MutableList<LogEntry> = ArrayList()
) {
    fun fetchAlivePlayers(): Map<String, Player> {
        val alivePlayers = HashMap<String, Player>()
        for ((key, value) in players) {
            if (value.isAlive) {
                alivePlayers[key] = value
            }
        }
        return alivePlayers
    }

    @get:BsonIgnore
    val police: Player?
        get() {
            for (player in players.values) {
                if (player.police) return player
            }
            return null
        }

    fun hasEnded(simulateRoleRemovalArg: String?): Result {
        var simulateRoleRemoval = simulateRoleRemovalArg
        var wolves = 0f
        var gods = 0f
        var villagers = 0f
        var jinBaoBao = 0f
        var policeOnWolf = false
        var policeOnGood = false

        for (player in players.values) {
            if (player.jinBaoBao) jinBaoBao++
            if (player.roles != null) {
                for (role in player.roles!!) {
                    if (role == simulateRoleRemoval) {
                        simulateRoleRemoval = null
                        continue
                    }
                    // Skip dead roles
                    if (player.deadRoles != null && player.deadRoles!!.contains(role)) {
                        continue
                    }

                    if (Player.isWolf(role)) {
                        wolves++
                        if (player.police) policeOnWolf = true
                    } else if (Player.isGod(role) || (player.duplicated && player.roles!!.size > 1)) {
                        gods++
                        if (player.police) policeOnGood = true
                    } else if (Player.isVillager(role)) {
                        villagers++
                        if (player.police) policeOnGood = true
                    }
                }
            }
        }

        if (gods == 0f) return Result.GODS_DIED
        if (wolves == 0f) return Result.WOLVES_DIED
        if (doubleIdentities) {
            if (jinBaoBao == 0f) return Result.JIN_BAO_BAO_DIED
        } else {
            if (villagers == 0f && roles.contains("平民")) // support for an all gods game
                return Result.VILLAGERS_DIED
        }
        if (policeOnGood) villagers += 0.5f
        if (policeOnWolf) wolves += 0.5f
        if ((wolves >= gods + villagers) && !doubleIdentities) // we don't do equal players ending in double identities,
        // too annoying
            return Result.EQUAL_PLAYERS
        return Result.NOT_ENDED
    }

    enum class Result(val reason: String) {
        NOT_ENDED("未結束"),
        VILLAGERS_DIED("全部村民死亡"),
        GODS_DIED("全部神死亡"),
        WOLVES_DIED("全部狼死亡"),
        JIN_BAO_BAO_DIED("全部金寶寶死亡"),
        EQUAL_PLAYERS("狼人陣營人數=好人陣營人數")
    }

    data class Player(
        var id: Int = 0,
        var roleId: Long = 0,
        var channelId: Long = 0,
        var jinBaoBao: Boolean = false,
        var duplicated: Boolean = false,
        var idiot: Boolean = false,
        var police: Boolean = false,
        var rolePositionLocked: Boolean = false,
        var userId: Long? = null,
        var roles: MutableList<String>? = LinkedList(), // stuff like wolf, villager...etc
        var deadRoles: MutableList<String>? = LinkedList()
    ) : Comparable<Player> {

        @get:BsonIgnore
        val isAlive: Boolean
            get() {
                if (roles == null || roles!!.isEmpty()) return false
                if (deadRoles == null) return true
                return deadRoles!!.size < roles!!.size
            }

        override fun compareTo(other: Player): Int {
            return Integer.compare(id, other.id)
        }

        @get:BsonIgnore
        val nickname: String
            get() {
                val sb = StringBuilder()
                if (!isAlive) {
                    sb.append("[死人] ")
                }
                sb.append("玩家").append(ID_FORMAT.format(id.toLong()))
                if (police) {
                    sb.append(" [警長]")
                }
                return sb.toString()
            }

        fun updateNickname(member: Member?) {
            if (member == null) return
            val newName = nickname
            if (member.effectiveName != newName) {
                member.modifyNickname(newName).queue()
            }
        }

        companion object {
            val ID_FORMAT = DecimalFormat("00")

            fun isGod(role: String): Boolean {
                return (!isWolf(role)) && (!isVillager(role))
            }

            fun isWolf(role: String): Boolean {
                return role.contains("狼") || role == "石像鬼" || role == "血月使者" || role == "惡靈騎士"
            }

            fun isVillager(role: String): Boolean {
                return role == "平民"
            }
        }
    }

    /**
     * Audit log entry for tracking game events
     */
    data class LogEntry(
        var id: String? = null,
        var timestamp: Long = 0,
        var type: LogType? = null,
        var message: String? = null,
        var metadata: Map<String, Any>? = null
    )

    /**
     * Add a log entry to the session
     */
    fun addLog(type: LogType, message: String) {
        addLog(type, message, null)
    }

    /**
     * Add a log entry with metadata to the session
     */
    fun addLog(type: LogType, message: String, metadata: Map<String, Any>?) {
        val entry = LogEntry(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            type = type,
            message = message,
            metadata = metadata
        )
        logs.add(entry)

        // Persist to database
        fetchCollection().updateOne(
            eq("guildId", guildId),
            Updates.push("logs", entry)
        )
    }

    companion object {
        fun fetchCollection(): MongoCollection<Session> {
            return Database.database.getCollection("sessions", Session::class.java)
        }
    }
}
