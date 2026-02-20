package dev.robothanzo.werewolf.database.documents

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleEventContext
import dev.robothanzo.werewolf.game.model.RoleEventType
import dev.robothanzo.werewolf.game.roles.actions.RoleAction
import io.swagger.v3.oas.annotations.media.Schema
import kotlinx.coroutines.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.bson.codecs.pojo.annotations.BsonIgnore
import org.springframework.data.annotation.Transient
import java.awt.Color
import java.text.DecimalFormat
import kotlin.coroutines.resume
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

    /**
     * Marks the player as dead in the data model and logs the event.
     * This method MUST be called within a locked session.
     * It does NOT handle last words, police transfer, or death triggers.
     */
    fun markDead(cause: DeathCause = DeathCause.UNKNOWN) {
        val session = this.session ?: throw IllegalStateException("Player must be bound to a session")
        val guild = session.guild ?: throw Exception("Guild not found")
        var member = guild.getMemberById(user?.idLong ?: return)
        if (member == null) {
            member = guild.retrieveMemberById(user!!.idLong).complete()
        }

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

            val embed = EmbedBuilder()
                .setTitle("玩家${ID_FORMAT.format(id)} 死亡")
                .setDescription("**:skull: " + member.asMention + " 已死亡**")
                .setColor(Color.RED)
                .setThumbnail(member.user.avatarUrl)
            if (!session.settings.hiddenRoleOnDeath) {
                embed.addField("死亡角色", killedRole, false)
            }
            session.courtTextChannel?.sendMessageEmbeds(embed.build())?.queue()
        }
        if (alive) {
            val remainingRoles = roles.toMutableList()
            deadRoles.forEach { deadRole ->
                remainingRoles.remove(deadRole)
            }

            val remainingRoleName = if (remainingRoles.isEmpty()) "未知" else remainingRoles.first()
            channel?.sendMessage("因為你死了，所以你的角色變成了 $remainingRoleName")?.queue()
        }

        WerewolfApplication.gameSessionService.saveSession(session)
    }

    /**
     * Suspending function to handle the interactive parts of death:
     * Last Words -> Police Transfer -> Death Triggers -> Discord Role update.
     * This MUST be called OUTSIDE of a locked session to allow for user interaction.
     */
    suspend fun runDeathEvents(allowLastWords: Boolean = true) {
        val session = this.session ?: throw IllegalStateException("Player must be bound to a session")
        val guild = session.guild ?: return

        // Wait a bit for the internal state to be fully consistent/propagated if needed
        // (Though marking dead is sync and done before this)

        // 1. Last Words
        if (allowLastWords) {
            // We wrap the callback-based service in suspendCancellableCoroutine to wait for it.
            suspendCancellableCoroutine<Unit> { cont ->
                WerewolfApplication.speechService.startLastWordsSpeech(
                    guild,
                    session.courtTextChannel?.idLong ?: 0,
                    this
                ) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }

        // 2. Transfer Police and Death Triggers
        transferPolice()

        // 3. Mark as processed in session state to avoid re-processing in DeathAnnouncementStep
        WerewolfApplication.gameSessionService.withLockedSession(session.guildId) { lockedSession ->
            if (!lockedSession.stateData.processedDeathPlayerIds.contains(id)) {
                lockedSession.stateData.processedDeathPlayerIds.add(id)
            }
        }
    }

    /**
     * Convenience method to process a death entirely.
     * WARNING: This attempts to acquire the lock for markDead, then releases it for runDeathEvents.
     */
    suspend fun processDeath(cause: DeathCause = DeathCause.UNKNOWN, allowLastWords: Boolean = true) {
        val guildId = session?.guildId ?: return
        WerewolfApplication.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val player = lockedSession.getPlayer(this.id) ?: return@withLockedSession
            player.markDead(cause)
        }
        this.runDeathEvents(allowLastWords)
    }

    /**
     * Handles the killing of police role and transfers it to another player if applicable.
     */
    suspend fun transferPolice() {
        if (police) {
            session?.let { s ->
                suspendCancellableCoroutine<Unit> { cont ->
                    WerewolfApplication.policeService.transferPolice(s, s.guild, this) {
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                discordDeath()
                promptDeathTrigger()
            }
        } else {
            discordDeath()
            promptDeathTrigger()
        }
    }

    private suspend fun promptDeathTrigger() {
        val session = this.session ?: return

        var deathActions: List<RoleAction> = emptyList()
        val timeLeft = 30

        // Locking briefly to check triggers availability
        WerewolfApplication.gameSessionService.withLockedSession(session.guildId) { lockedSession ->
            val p = lockedSession.getPlayer(id) ?: return@withLockedSession
            val roles = p.roles
            deathActions = roles.flatMap { roleName ->
                val roleObj =
                    lockedSession.hydratedRoles[roleName] ?: WerewolfApplication.roleRegistry.getRole(roleName)
                roleObj?.getActions()?.filter {
                    it.timing == ActionTiming.DEATH_TRIGGER && it.isAvailable(
                        lockedSession,
                        id
                    )
                } ?: emptyList()
            }

            if (deathActions.isNotEmpty()) {
                val actionName = deathActions.firstOrNull()?.actionName ?: "死亡技能"
                p.actionSubmitted = false
                p.channel?.sendMessage("你的 $actionName 已觸發！你可以選擇一名玩家帶走。")?.queue()

                WerewolfApplication.actionUIService.promptPlayerForAction(
                    lockedSession.guildId,
                    lockedSession,
                    id,
                    deathActions,
                    timeLeft
                )

            }
        }

        if (deathActions.isNotEmpty()) {
            // Wait for action submission or timeout
            try {
                withTimeout((timeLeft * 1000L) + 2000L) { // +2s buffer
                    // Poll for status
                    while (true) {
                        var isDone = false
                        WerewolfApplication.gameSessionService.withLockedSession(session.guildId) { lockedSession ->
                            val p = lockedSession.getPlayer(id)
                            if (p == null || p.actionSubmitted) {
                                isDone = true
                            } else {
                                // Check if actions consumed otherwise
                                val stillAvailable = deathActions.any {
                                    (lockedSession.stateData.playerOwnedActions[id]?.get(it.actionId.toString())
                                        ?: 0) > 0
                                }
                                if (!stillAvailable) isDone = true
                            }
                        }
                        if (isDone) break
                        delay(1000)
                    }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                // Timeout logic - force expire if needed
                WerewolfApplication.gameSessionService.withLockedSession(session.guildId) { lockedSession ->
                    val p = lockedSession.getPlayer(id) ?: return@withLockedSession
                    val stillAvailable = deathActions.any {
                        (lockedSession.stateData.playerOwnedActions[p.id]?.get(it.actionId.toString()) ?: 0) > 0
                    }
                    if (stillAvailable && !p.actionSubmitted) {
                        p.channel?.sendMessage("⏱️ **時間到！** 你未能在規定時間內選擇目標，行動已取消。")?.queue()
                        // Force consume trigger
                        deathActions.forEach { action ->
                            lockedSession.stateData.playerOwnedActions[p.id]?.remove(action.actionId.toString())
                            lockedSession.stateData.submittedActions.find {
                                it.actor == p.id && it.actionDefinitionId == action.actionId
                            }?.status = dev.robothanzo.werewolf.game.model.ActionStatus.PROCESSED
                        }
                    }
                }
            }
        }

        // Ensure final death state (spectator role)
        WerewolfApplication.gameSessionService.withLockedSession(session.guildId) { lockedSession ->
            val p = lockedSession.getPlayer(id) ?: return@withLockedSession
            p.discordDeath()
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

    /**
     * Orchestrates the entire death sequence, including this player and any subsequent deaths
     * triggered by death events (e.g. Hunter shooting).
     *
     * @param onFinished Callback executed when ALL cascading deaths are resolved.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun processCascadingDeaths(isExpelled: Boolean = false, onFinished: () -> Unit) {
        val session = this.session ?: return
        val guildId = session.guildId

        GlobalScope.launch {
            try {
                WerewolfApplication.gameSessionService.withLockedSession(guildId) { session ->
                    session.stateData.deathProcessingInProgress = true
                }

                // 1. Process THIS player first
                // On Day 1, everyone gets Last Words.
                // Voted-out (expelled) players always get last words.
                val allowFirstLastWords = isExpelled || session.day <= 1
                runDeathEvents(allowFirstLastWords)

                // 2. Loop for others (Cascading Deaths)
                while (true) {
                    val currentSession =
                        WerewolfApplication.gameSessionService.getSession(guildId).orElse(null) ?: break
                    val nextVictim = currentSession.players.values.firstOrNull {
                        !it.alive && !currentSession.stateData.processedDeathPlayerIds.contains(it.id)
                    } ?: break

                    try {
                        // Cascading victims (e.g. Hunter target) get last words on Day 1
                        val allowNextLastWords = currentSession.day <= 1
                        nextVictim.runDeathEvents(allowNextLastWords)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        WerewolfApplication.gameSessionService.withLockedSession(guildId) { s ->
                            s.stateData.processedDeathPlayerIds.add(nextVictim.id)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                WerewolfApplication.gameSessionService.withLockedSession(guildId) { session ->
                    session.stateData.deathProcessingInProgress = false
                }
                onFinished()
            }
        }
    }

    companion object {
        val ID_FORMAT = DecimalFormat("00")

        fun isGod(role: String): Boolean {
            return (!isWolf(role)) && (!isVillager(role))
        }

        fun isWolf(role: String): Boolean {
            return role.contains("狼") || role == "石像鬼" || role == "血月使者" || role == "惡靈騎士" || role == "夢魘"
        }

        fun isVillager(role: String): Boolean {
            return role == "平民"
        }
    }
}
