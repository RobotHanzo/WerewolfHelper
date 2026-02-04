package dev.robothanzo.werewolf.database.documents

import com.mongodb.client.MongoCollection
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.Database
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.codecs.pojo.annotations.BsonIgnore
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

@Document(collection = "sessions")
data class Session(
    @Id
    @param:BsonId
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
    var logs: MutableList<LogEntry> = ArrayList(),

    // Game State Machine Fields
    var currentState: String = "SETUP", // Default to setup
    var stateData: MutableMap<String, Any> = HashMap(),
    var currentStepEndTime: Long = 0,
    var day: Int = 0,

    // Game Settings
    var settings: MutableMap<String, Any> = HashMap()
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

    @get:BsonIgnore
    val guild: Guild?
        get() = WerewolfApplication.jda.getGuildById(guildId)

    @get:BsonIgnore
    val spectatorRole: Role?
        get() = guild?.getRoleById(spectatorRoleId)

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

    fun sendToCourt(message: String, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(courtTextChannelId)
            ?.sendMessage(message) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToCourt(embed: MessageEmbed, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(courtTextChannelId)
            ?.sendMessageEmbeds(embed) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToCourt(embeds: Collection<MessageEmbed>, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(courtTextChannelId)
            ?.sendMessageEmbeds(embeds) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToCourt(message: String, embed: MessageEmbed, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(courtTextChannelId)
            ?.sendMessage(message)
            ?.setEmbeds(embed) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToCourt(message: String, embeds: Collection<MessageEmbed>, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(courtTextChannelId)
            ?.sendMessage(message)
            ?.setEmbeds(embeds) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToSpectator(message: String, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(spectatorTextChannelId)
            ?.sendMessage(message) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToSpectator(embed: MessageEmbed, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(spectatorTextChannelId)
            ?.sendMessageEmbeds(embed) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToSpectator(embeds: Collection<MessageEmbed>, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(spectatorTextChannelId)
            ?.sendMessageEmbeds(embeds) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToSpectator(message: String, embed: MessageEmbed, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(spectatorTextChannelId)
            ?.sendMessage(message)
            ?.setEmbeds(embed) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToSpectator(
        message: String,
        embeds: Collection<MessageEmbed>,
        queue: Boolean = true
    ): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(spectatorTextChannelId)
            ?.sendMessage(message)
            ?.setEmbeds(embeds) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToJudge(message: String, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(judgeTextChannelId)
            ?.sendMessage(message) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToJudge(embed: MessageEmbed, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(judgeTextChannelId)
            ?.sendMessageEmbeds(embed) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToJudge(embeds: Collection<MessageEmbed>, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(judgeTextChannelId)
            ?.sendMessageEmbeds(embeds) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToJudge(message: String, embed: MessageEmbed, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(judgeTextChannelId)
            ?.sendMessage(message)
            ?.setEmbeds(embed) ?: return null
        if (queue) action.queue()
        return action
    }

    fun sendToJudge(message: String, embeds: Collection<MessageEmbed>, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getGuildById(guildId)
            ?.getTextChannelById(judgeTextChannelId)
            ?.sendMessage(message)
            ?.setEmbeds(embeds) ?: return null
        if (queue) action.queue()
        return action
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

    fun getPlayer(playerId: String): Player? {
        return players[playerId]
    }

    fun getPlayer(userId: Long): Player? {
        return players.values.find { it.userId == userId }
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
        WerewolfApplication.gameSessionService.saveSession(this)
    }

    companion object {
        fun fetchCollection(): MongoCollection<Session> {
            return Database.database.getCollection("sessions", Session::class.java)
        }
    }
}
