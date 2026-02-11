package dev.robothanzo.werewolf.database.documents

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleEventContext
import dev.robothanzo.werewolf.game.model.RoleEventType
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.bson.codecs.pojo.annotations.BsonIgnore
import org.springframework.data.annotation.Transient
import java.text.DecimalFormat
import dev.robothanzo.werewolf.game.model.Role as GameRole

data class Player(
    var id: Int = 0,
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    var roleId: Long = 0,
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    var channelId: Long = 0,
    var jinBaoBao: Boolean = false,
    var duplicated: Boolean = false,
    var idiot: Boolean = false,
    var police: Boolean = false,
    var rolePositionLocked: Boolean = false,
    var actionSubmitted: Boolean = false, // Track if player has submitted an action this phase
    @get:JsonSerialize(using = ToStringSerializer::class)
    @get:Schema(type = "string")
    var userId: Long? = null,
    var roles: MutableList<String> = mutableListOf(), // stuff like wolf, villager...etc
    var deadRoles: MutableList<String> = mutableListOf()
) : Comparable<Player> {
    @Transient
    @BsonIgnore
    @JsonIgnore
    var session: Session? = null // Reference to the session this player belongs to

    @get:JsonIgnore
    val user: User?
        get() = userId?.let {
            WerewolfApplication.jda.getUserById(it) ?: WerewolfApplication.jda.retrieveUserById(it).complete()
        }

    @get:JsonIgnore
    val member: Member?
        get() = userId?.let {
            session?.guild?.getMemberById(it) ?: session?.guild?.retrieveMemberById(user!!.idLong)?.complete()
        }

    @get:JsonIgnore
    val role: Role?
        get() = roleId.let { session?.guild?.getRoleById(it) }

    @get:JsonIgnore
    val channel: TextChannel?
        get() = WerewolfApplication.jda.getTextChannelById(channelId)

    val wolf: Boolean
        get() = roles.any { isWolf(it) }

    fun updateUserId(id: Long?) {
        userId = id
    }

    val alive: Boolean
        get() {
            if (roles.isEmpty()) return false
            return deadRoles.size < roles.size
        }

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

        val roles = roles
        val deadRoles = deadRoles

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
                        actorPlayerId = id,
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
                        "userId" to (user?.idLong ?: 0L),
                        "killedRole" to killedRole,
                        "playerId" to id,
                        "deathCause" to cause
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
                    this
                ) {
                    transferPolice()
                }
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
    fun transferPolice() {
        if (police)
            session?.let {
                WerewolfApplication.policeService.transferPolice(it, it.guild, this) {
                    discordDeath()
                    promptDeathTrigger()
                }
            }
        else {
            discordDeath()
            promptDeathTrigger()
        }
    }

    private fun promptDeathTrigger() {
        if (session != null) {
            val roles = roles
            val deathActions = roles.flatMap { roleName ->
                val roleObj =
                    session?.hydratedRoles?.get(roleName) ?: WerewolfApplication.roleRegistry.getRole(roleName)
                roleObj?.getActions()?.filter {
                    it.timing == ActionTiming.DEATH_TRIGGER && it.isAvailable(
                        session!!,
                        id
                    )
                } ?: emptyList()
            }

            if (deathActions.isNotEmpty()) {
                val actionName = deathActions.firstOrNull()?.actionName ?: "死亡技能"
                actionSubmitted = false
                channel?.sendMessage("你的 $actionName 已觸發！你可以選擇一名玩家帶走。")?.queue()

                WerewolfApplication.actionUIService.promptPlayerForAction(
                    session!!.guildId,
                    session!!,
                    id,
                    deathActions,
                    30
                )

                // Extend session duration if we are in DEATH_ANNOUNCEMENT step
                WerewolfApplication.gameSessionService.withLockedSession(session!!.guildId) { sess ->
                    if (sess.currentState == "DEATH_ANNOUNCEMENT") {
                        val now = System.currentTimeMillis()
                        sess.currentStepEndTime = maxOf(sess.currentStepEndTime, now + 30000L)
                        WerewolfApplication.gameSessionService.broadcastSessionUpdate(sess)
                    }
                }

                // Safety timer and reminders
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch {
                    // 20s reminder (10s remaining)
                    delay(20000)
                    var alreadySubmitted = false
                    WerewolfApplication.gameSessionService.withLockedSession(session!!.guildId) { lockedSession ->
                        val p = lockedSession.getPlayer(this@Player.id) ?: return@withLockedSession
                        alreadySubmitted = p.actionSubmitted || deathActions.none {
                            (lockedSession.stateData.playerOwnedActions[p.id]?.get(it.actionId.toString()) ?: 0) > 0
                        }
                        if (!alreadySubmitted) {
                            p.channel?.sendMessage("⚠️ **提醒**: 你的技能選擇還剩 **10秒**，請儘快做出選擇！")?.queue()
                        }
                    }

                    if (alreadySubmitted) return@launch

                    // 10s later (at 30s) - Timeout
                    delay(10000)
                    WerewolfApplication.gameSessionService.withLockedSession(session!!.guildId) { lockedSession ->
                        val p = lockedSession.getPlayer(this@Player.id) ?: return@withLockedSession
                        val stillAvailable = deathActions.any {
                            (lockedSession.stateData.playerOwnedActions[p.id]?.get(it.actionId.toString()) ?: 0) > 0
                        }
                        if (stillAvailable && !p.actionSubmitted) {
                            p.channel?.sendMessage("⏱️ **時間到！** 你未能在規定時間內選擇目標，行動已取消。")?.queue()
                            // Force consume trigger status to ensure discordDeath proceeds
                            deathActions.forEach { action ->
                                lockedSession.stateData.playerOwnedActions[p.id]?.remove(action.actionId.toString())
                                // Mark as processed so it's not detected as an "active" trigger anymore
                                lockedSession.stateData.submittedActions.find {
                                    it.actor == p.id && it.actionDefinitionId == action.actionId
                                }?.status = dev.robothanzo.werewolf.game.model.ActionStatus.PROCESSED
                            }

                            // Trigger completion check for potential early step advancement
                            if (lockedSession.currentState == "DEATH_ANNOUNCEMENT") {
                                (WerewolfApplication.gameStateService.getCurrentStep(lockedSession) as? dev.robothanzo.werewolf.game.steps.DeathAnnouncementStep)?.checkAdvance(
                                    lockedSession,
                                    WerewolfApplication.gameStateService
                                )
                            }

                            p.discordDeath()
                        }
                    }
                }
            }
        }
    }

    fun discordDeath() {
        // If player has a death trigger available, don't give spectator role yet
        // They need to be able to see their channel to perform the action
        if (session != null) {
            val roles = roles
            val hasDeathTrigger = roles.any { roleName ->
                val roleObj =
                    session?.hydratedRoles?.get(roleName) ?: WerewolfApplication.roleRegistry.getRole(roleName)
                roleObj?.getActions()?.any {
                    it.timing == ActionTiming.DEATH_TRIGGER && it.isAvailable(
                        session!!,
                        id
                    )
                } == true
            }
            if (hasDeathTrigger) {
                updateNickname()
                return
            }
        }

        session?.let {
            member?.let { it1 -> it.guild?.modifyMemberRoles(it1, it.spectatorRole) }?.queue()
            updateNickname()
            WerewolfApplication.gameSessionService.saveSession(it)
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
