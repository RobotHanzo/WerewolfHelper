package dev.robothanzo.werewolf.database.documents

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.controller.dto.SessionSummary
import dev.robothanzo.werewolf.game.model.GameSettings
import dev.robothanzo.werewolf.game.model.GameStateData
import io.swagger.v3.oas.annotations.media.Schema
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.annotation.Version
import org.springframework.data.domain.Persistable
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable
import java.util.*
import dev.robothanzo.werewolf.game.model.Role as GameRole

data class DiscordIDs(
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    var courtTextChannelId: Long = 0,
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    var courtVoiceChannelId: Long = 0,
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    var spectatorTextChannelId: Long = 0,
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    var judgeTextChannelId: Long = 0,
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    var judgeRoleId: Long = 0,
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    var spectatorRoleId: Long = 0,
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    var owner: Long = 0,
)

@Document(collection = "sessions")
data class Session(
    @Id
    @param:BsonId
    @JsonIgnore
    @Schema(hidden = true)
    var _id: ObjectId? = null,
    @Schema(description = "Unique identifier for this session for replay indexing")
    var sessionId: String = UUID.randomUUID().toString(),
    @Version
    @JsonIgnore
    var version: Long? = null,
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    @Indexed(unique = true)
    var guildId: Long = 0,
    val discordIDs: DiscordIDs = DiscordIDs(),
    var doubleIdentities: Boolean = false,
    var hasAssignedRoles: Boolean = false,
    var muteAfterSpeech: Boolean = true,
    var roles: MutableList<String> = LinkedList(),
    var players: MutableMap<String, Player> = HashMap(),
    var logs: MutableList<LogEntry> = ArrayList(),

    // Game State Machine Fields
    var currentState: String = "SETUP", // Default to setup
    var stateData: GameStateData = GameStateData(),
    var day: Int = 0,

    // Game Settings
    var settings: GameSettings = GameSettings(),
) : Persistable<ObjectId>, Serializable {
    @Transient
    var hydratedRoles: MutableMap<String, GameRole> = HashMap()

    /** Computed transiently before each broadcast — never persisted. */
    val currentStepEndTime: Long
        get() = WerewolfApplication.gameStateService.getStep(currentState)?.getEndTime(this) ?: 0L

    override fun getId(): ObjectId? = _id
    override fun isNew(): Boolean = _id == null

    @get:JsonIgnore
    val guild: Guild?
        get() = WerewolfApplication.jda.getGuildById(guildId)

    @get:JsonIgnore
    val spectatorRole: Role?
        get() = guild?.getRoleById(discordIDs.spectatorRoleId)

    @get:JsonIgnore
    val judgeRole: Role?
        get() = guild?.getRoleById(discordIDs.judgeRoleId)

    @get:JsonIgnore
    val courtTextChannel: TextChannel?
        get() = guild?.getTextChannelById(discordIDs.courtTextChannelId)

    @get:JsonIgnore
    val courtVoiceChannel: VoiceChannel?
        get() = guild?.getVoiceChannelById(discordIDs.courtVoiceChannelId)

    @get:JsonIgnore
    val spectatorTextChannel: TextChannel?
        get() = guild?.getTextChannelById(discordIDs.spectatorTextChannelId)

    @get:JsonIgnore
    val judgeTextChannel: TextChannel?
        get() = guild?.getTextChannelById(discordIDs.judgeTextChannelId)

    /**
     * Populate player.session references after loading from database
     */
    fun populatePlayerSessions() {
        for (player in players.values) {
            player.session = this
        }
    }

    /**
     * Hydrate role strings into Role objects
     */
    fun hydrateRoles(roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry) {
        hydratedRoles.clear()
        for (player in players.values) {
            player.roles.forEach { roleName ->
                if (!hydratedRoles.containsKey(roleName)) {
                    roleRegistry.getRole(roleName)?.let {
                        hydratedRoles[roleName] = it
                    }
                }
            }
        }
    }

    fun alivePlayers(): Map<String, Player> {
        return players.filter { it.value.alive }
    }

    @get:JsonIgnore
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
            for (role in player.roles) {
                if (role == simulateRoleRemoval) {
                    simulateRoleRemoval = null
                    continue
                }
                // Skip dead roles
                if (player.deadRoles.contains(role)) {
                    continue
                }

                if (Player.isWolf(role)) {
                    wolves++
                    if (player.police) policeOnWolf = true
                } else if (Player.isGod(role) || (player.duplicated && player.roles.size > 1)) {
                    gods++
                    if (player.police) policeOnGood = true
                } else if (Player.isVillager(role)) {
                    villagers++
                    if (player.police) policeOnGood = true
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

    fun addPlayer(player: Player) {
        player.session = this
        players[player.id.toString()] = player
    }

    fun removePlayer(playerId: String): Player? {
        val player = players.remove(playerId)
        player?.let { it.session = null }
        return player
    }

    fun getPlayer(playerId: String): Player? = players[playerId]

    fun getPlayer(playerId: Int): Player? = players[playerId.toString()]

    fun getPlayer(userId: Long): Player? = players.values.find { it.user?.idLong == userId }

    fun getPlayerByChannel(channelId: Long): Player? = players.values.find { it.channel?.idLong == channelId }

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
     * Add a log entry with metadata to the session
     */
    fun addLog(type: LogType, message: String, metadata: Map<String, Any>? = null) {
        WerewolfApplication.gameSessionService.withLockedSession(this.guildId) {
            it.logs.add(
                LogEntry(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    message = message,
                    metadata = metadata
                )
            )
        }
    }

    fun generateSummary(): SessionSummary {
        return SessionSummary(
            guildId = this.guildId.toString(),
            guildName = this.guild?.name ?: "Unknown Guild",
            guildIcon = this.guild?.iconUrl ?: "",
            playerCount = this.players.size
        )
    }

    fun isCharacterAlive(character: String): Boolean {
        for (player in alivePlayers().values) {
            if (player.roles.contains(character)) {
                // Check if this specific role is NOT dead
                if (!player.deadRoles.contains(character)) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        val DASHBOARD_BASE_URL: String
            get() = System.getenv("DASHBOARD_URL")?.removeSuffix("/") ?: "http://localhost:5173"
    }

    @get:JsonIgnore
    @get:Transient
    val dashboardUrl: String
        get() = "$DASHBOARD_BASE_URL/server/$guildId"
}
