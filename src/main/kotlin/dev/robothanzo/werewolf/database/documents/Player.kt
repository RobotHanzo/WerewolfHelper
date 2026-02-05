package dev.robothanzo.werewolf.database.documents

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleEventContext
import dev.robothanzo.werewolf.game.model.RoleEventType
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.bson.codecs.pojo.annotations.BsonIgnore
import org.springframework.data.annotation.Transient
import java.text.DecimalFormat
import java.util.*
import dev.robothanzo.werewolf.game.model.Role as GameRole

data class Player(
    var id: Int = 0,
    private var roleId: Long = 0,
    private var channelId: Long = 0,
    var jinBaoBao: Boolean = false,
    var duplicated: Boolean = false,
    var idiot: Boolean = false,
    var police: Boolean = false,
    var rolePositionLocked: Boolean = false,
    var actionSubmitted: Boolean = false, // Track if player has submitted an action this phase
    private var userId: Long? = null,
    var roles: MutableList<String>? = LinkedList(), // stuff like wolf, villager...etc
    var deadRoles: MutableList<String>? = LinkedList()
) : Comparable<Player> {
    @Transient
    @BsonIgnore
    var session: Session? = null // Reference to the session this player belongs to
    val user: User?
        get() = userId?.let {
            WerewolfApplication.jda.getUserById(it) ?: WerewolfApplication.jda.retrieveUserById(it).complete()
        }
    val member: Member?
        get() = userId?.let {
            session?.guild?.getMemberById(it) ?: session?.guild?.retrieveMemberById(user!!.idLong)?.complete()
        }
    val role: Role?
        get() = roleId.let { session?.guild?.getRoleById(it) }
    val channel: TextChannel?
        get() = WerewolfApplication.jda.getTextChannelById(channelId)
    val wolf: Boolean
        get() = roles?.any { isWolf(it) } ?: false

    fun updateUserId(id: Long?) {
        userId = id
    }

    @get:BsonIgnore
    val alive: Boolean
        get() {
            if (roles == null || roles!!.isEmpty()) return false
            if (deadRoles == null) return true
            return deadRoles!!.size < roles!!.size
        }

    @get:BsonIgnore
    val nickname: String
        get() {
            val sb = StringBuilder()
            if (!alive) {
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

    fun died(cause: DeathCause = DeathCause.UNKNOWN, allowLastWords: Boolean = true) {
        val session = this.session ?: throw IllegalStateException("Player must be bound to a session")

        val guild = session.guild
            ?: throw Exception("Guild not found")

        var member = guild.getMemberById(user?.idLong ?: return)
        if (member == null) {
            member = guild.retrieveMemberById(user!!.idLong).complete()
        }

        var lastWords = allowLastWords

        if (!alive) return

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
            metadata["userId"] = user?.idLong!!
            metadata["isExpelled"] = (cause == DeathCause.EXPEL)
            metadata["deathCause"] = cause.name

            session.addLog(
                LogType.PLAYER_DIED,
                "$nickname 的 $killedRole 身份已死亡",
                metadata
            )

            // Delegate to Role.onDeath
            val roleObj: GameRole? = session.hydratedRoles[killedRole]
            if (roleObj != null) {
                roleObj.onDeath(
                    RoleEventContext(
                        session = session,
                        eventType = RoleEventType.ON_DEATH,
                        actorUserId = user!!.idLong,
                        metadata = mapOf(
                            "killedRole" to killedRole,
                            "playerId" to id,
                            "deathCause" to cause
                        )
                    )
                )
            } else {
                // Fallback to legacy event trigger if role object not found
                WerewolfApplication.roleEventService.notifyListeners(
                    session,
                    RoleEventType.ON_DEATH,
                    mapOf(
                        "userId" to user!!.idLong,
                        "killedRole" to killedRole,
                        "playerId" to id
                    )
                )
            }

            session.courtTextChannel?.sendMessage("**:skull: " + member.asMention + " 已死亡**")?.queue()
        }

        val result = session.hasEnded(killedRole)
        if (result != Session.Result.NOT_ENDED) {
            val judgePing = "<@&" + (session.judgeRole?.idLong ?: 0) + "> "
            if (result == Session.Result.WOLVES_DIED) {
                session.spectatorTextChannel?.sendMessage(judgePing + "遊戲結束，**好**人獲勝，原因：" + result.reason)
                    ?.queue()
            } else {
                session.spectatorTextChannel?.sendMessage(judgePing + "遊戲結束，**狼**人獲勝，原因：" + result.reason)
                    ?.queue()
            }
            lastWords = false
        }

        if (alive) {
            val remainingRoles = roles.toMutableList()
            deadRoles.forEach { deadRole ->
                remainingRoles.remove(deadRole)
            }

            val remainingRoleName = if (remainingRoles.isEmpty()) "未知" else remainingRoles.first()
            channel?.sendMessage("因為你死了，所以你的角色變成了 $remainingRoleName")?.queue()
            WerewolfApplication.gameSessionService.saveSession(session)

            if (lastWords) {
                WerewolfApplication.speechService.startLastWordsSpeech(
                    guild,
                    session.courtTextChannel?.idLong ?: 0,
                    this,
                    {
                        transferPolice()
                        guild.modifyMemberRoles(member, session.spectatorRole).queue()
                        updateNickname()
                    }
                )
            } else {
                transferPolice()
            }
        } else {
            // Fully dead
            transferPolice()
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