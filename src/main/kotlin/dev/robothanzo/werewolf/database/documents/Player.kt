package dev.robothanzo.werewolf.database.documents

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleEventType
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.bson.codecs.pojo.annotations.BsonIgnore
import java.text.DecimalFormat
import java.util.*

data class Player(
    var id: Int = 0,
    var roleId: Long = 0,
    var channelId: Long = 0,
    var jinBaoBao: Boolean = false,
    var duplicated: Boolean = false,
    var idiot: Boolean = false,
    var police: Boolean = false,
    var rolePositionLocked: Boolean = false,
    var actionSubmitted: Boolean = false, // Track if player has submitted an action this phase
    var userId: Long? = null,
    var roles: MutableList<String>? = LinkedList(), // stuff like wolf, villager...etc
    var deadRoles: MutableList<String>? = LinkedList(),
    @param:BsonIgnore
    var session: Session? = null // Reference to the session this player belongs to
) : Comparable<Player> {

    @get:BsonIgnore
    val member: Member?
        get() = userId?.let { session?.guild?.getMemberById(it) }

    @get:BsonIgnore
    val isAlive: Boolean
        get() {
            if (roles == null || roles!!.isEmpty()) return false
            if (deadRoles == null) return true
            return deadRoles!!.size < roles!!.size
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

    override fun compareTo(other: Player): Int {
        return id.compareTo(other.id)
    }

    fun updateNickname() {
        member?.let {
            val newName = nickname
            if (it.effectiveName != newName) {
                it.modifyNickname(newName).queue()
            }
        }
    }

    fun send(message: String, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getTextChannelById(channelId)
            ?.sendMessage(message) ?: return null
        if (queue) action.queue()
        return action
    }

    fun send(embed: MessageEmbed, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getTextChannelById(channelId)
            ?.sendMessageEmbeds(embed) ?: return null
        if (queue) action.queue()
        return action
    }

    fun send(embeds: Collection<MessageEmbed>, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getTextChannelById(channelId)
            ?.sendMessageEmbeds(embeds) ?: return null
        if (queue) action.queue()
        return action
    }

    fun send(message: String, embed: MessageEmbed, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getTextChannelById(channelId)
            ?.sendMessage(message)
            ?.setEmbeds(embed) ?: return null
        if (queue) action.queue()
        return action
    }

    fun send(message: String, embeds: Collection<MessageEmbed>, queue: Boolean = true): MessageCreateAction? {
        val action = WerewolfApplication.jda.getTextChannelById(channelId)
            ?.sendMessage(message)
            ?.setEmbeds(embeds) ?: return null
        if (queue) action.queue()
        return action
    }

    fun died(cause: DeathCause = DeathCause.UNKNOWN, allowLastWords: Boolean = true) {
        val session = this.session ?: throw IllegalStateException("Player must be bound to a session")

        val jda = WerewolfApplication.jda
        val guild = jda.getGuildById(session.guildId)
            ?: throw Exception("Guild not found")

        var member = guild.getMemberById(userId ?: return)
        if (member == null) {
            member = guild.retrieveMemberById(userId!!).complete()
        }

        var lastWords = allowLastWords
        var success: Boolean

        if (!isAlive) return

        val roles = roles ?: mutableListOf()
        var deadRoles = deadRoles
        if (deadRoles == null) {
            deadRoles = mutableListOf()
            this.deadRoles = deadRoles
        }

        var killedRole: String? = null
        for (role in roles) {
            val totalCount = roles.stream().filter { r -> r == role }.count()
            val deadCount = deadRoles.stream().filter { r -> r == role }.count()

            if (deadCount < totalCount) {
                killedRole = role
                deadRoles.add(role)
                break
            }
        }

        WerewolfApplication.gameSessionService.saveSession(session)

        if (killedRole != null) {
            val metadata = mutableMapOf<String, Any>()
            metadata["playerId"] = id
            metadata["playerName"] = nickname
            metadata["killedRole"] = killedRole
            metadata["userId"] = userId!!
            metadata["isExpelled"] = (cause == DeathCause.EXPEL)
            metadata["deathCause"] = cause.name

            session.addLog(
                LogType.PLAYER_DIED,
                "$nickname 的 $killedRole 身份已死亡",
                metadata
            )

            // Trigger role event for ON_DEATH listeners (e.g., Hunter revenge)
            WerewolfApplication.roleEventService.notifyListeners(
                session,
                RoleEventType.ON_DEATH,
                mapOf(
                    "userId" to userId!!,
                    "killedRole" to killedRole,
                    "playerId" to id
                )
            )
            session.sendToCourt("**:skull: " + member.asMention + " 已死亡**")
        }

        val result = session.hasEnded(killedRole)
        if (result != Session.Result.NOT_ENDED) {
            val judgePing = "<@&" + session.judgeRoleId + "> "
            if (result == Session.Result.WOLVES_DIED) {
                session.sendToSpectator(judgePing + "遊戲結束，**好**人獲勝，原因：" + result.reason)
            } else {
                session.sendToSpectator(judgePing + "遊戲結束，**狼**人獲勝，原因：" + result.reason)
            }
            lastWords = false
        }

        if (isAlive) {
            val remainingRoles = roles.toMutableList()
            deadRoles.forEach { deadRole ->
                remainingRoles.remove(deadRole)
            }

            val remainingRoleName = if (remainingRoles.isEmpty()) "未知" else remainingRoles.first()
            send("因為你死了，所以你的角色變成了 $remainingRoleName")
            WerewolfApplication.gameSessionService.saveSession(session)

            if (lastWords) {
                WerewolfApplication.speechService.startLastWordsSpeech(
                    guild,
                    session.courtTextChannelId,
                    this,
                    {
                        guild.modifyMemberRoles(member, session.spectatorRole).queue()
                        updateNickname()
                    }
                )
            } else {
                transferPolice()
            }
            success = true
        } else {
            // Fully dead
            transferPolice()
            success = true
        }

        if (!success && member != null) {
            // Player not found in session map, allow spectator role?
            session.spectatorRole?.let { guild.addRoleToMember(member, it) }?.queue()
            member.modifyNickname("[旁觀] " + member.user.name).queue()
        }

        WerewolfApplication.gameSessionService.saveSession(session)
    }

    /**
     * Handles the killing of police role and transfers it to another player if applicable.
     */
    private fun transferPolice() {
        if (police)
            session?.let {
                WerewolfApplication.policeService.transferPolice(it, it.guild, this) {
                    discordDeath()
                }
            }
        else {
            discordDeath()
        }
    }

    private fun discordDeath() {
        session?.let {
            member?.let { it1 -> it.guild?.modifyMemberRoles(it1, it.spectatorRole) }?.queue()
            updateNickname()
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