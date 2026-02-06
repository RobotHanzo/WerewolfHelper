package dev.robothanzo.werewolf.service.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.game.model.GameSettings
import dev.robothanzo.werewolf.security.GlobalWebSocketHandler
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.ExpelService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.SpeechService
import dev.robothanzo.werewolf.utils.player
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class GameSessionServiceImpl(
    private val sessionRepository: SessionRepository,
    private val discordService: DiscordService,
    private val webSocketHandler: GlobalWebSocketHandler,
    private val speechService: SpeechService,
    private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry,
    @Lazy stepList: List<GameStep>,
    @param:Lazy
    private val expelService: ExpelService
) : GameSessionService {
    private val log = LoggerFactory.getLogger(GameSessionServiceImpl::class.java)
    private val steps = stepList.associateBy { it.id }
    private val sessionLocks = ConcurrentHashMap<Long, Any>()
    private val sessionCache = ConcurrentHashMap<Long, Session>()

    private fun getLock(guildId: Long): Any = sessionLocks.computeIfAbsent(guildId) { Any() }

    @PostConstruct
    fun init() {
        WerewolfApplication.gameSessionService = this
    }

    override fun getAllSessions(): List<Session> {
        val sessions = sessionRepository.findAll()
        sessions.forEach {
            it.populatePlayerSessions()
            it.hydrateRoles(roleRegistry)
        }
        return sessions
    }

    override fun getSession(guildId: Long): Optional<Session> {
        // First check cache
        sessionCache[guildId]?.let {
            return Optional.of(it)
        }

        val sessionOpt = sessionRepository.findByGuildId(guildId)
        sessionOpt.ifPresent { session ->
            session.populatePlayerSessions()
            session.hydrateRoles(roleRegistry)
            // Cache the session for future access
            sessionCache[guildId] = session
        }
        return sessionOpt
    }

    override fun createSession(guildId: Long): Session {
        val session = Session(guildId = guildId)
        // Initialize default settings based on guild member count
        // 10 players: witchCanSaveSelf=true
        // 12+ players: witchCanSaveSelf=false
        val memberCount = session.guild?.memberCount ?: 10
        session.settings = GameSettings(
            witchCanSaveSelf = memberCount < 12
        )
        return sessionRepository.save(session)
    }

    override fun saveSession(session: Session): Session {
        synchronized(getLock(session.guildId)) {
            val saved = sessionRepository.save(session)
            // Sync version back to the original object to prevent stale version errors
            // if this object is saved again later in the same thread.
            session.version = saved.version
            return saved
        }
    }

    override fun <T> withLockedSession(guildId: Long, block: (Session) -> T): T {
        synchronized(getLock(guildId)) {
            val session = sessionCache[guildId] ?: getSession(guildId).orElseThrow { Exception("Session not found") }
            val result = block(session)
            saveSession(session)
            return result
        }
    }

    override fun deleteSession(guildId: Long) {
        sessionRepository.deleteByGuildId(guildId)
    }

    override fun sessionToJSON(session: Session): Map<String, Any> {
        val json = mutableMapOf<String, Any>()

        json["guildId"] = session.guildId.toString()
        json["doubleIdentities"] = session.doubleIdentities
        json["muteAfterSpeech"] = session.muteAfterSpeech
        json["hasAssignedRoles"] = session.hasAssignedRoles
        json["roles"] = session.roles
        json["currentState"] = session.currentState
        json["currentStep"] = steps[session.currentState]?.name ?: session.currentState
        json["day"] = session.day
        json["stateData"] = session.stateData

        val now = System.currentTimeMillis()
        val remaining = if (session.currentStepEndTime > now) {
            (session.currentStepEndTime - now) / 1000
        } else {
            0
        }
        json["timerSeconds"] = remaining
        json["isManualStep"] = session.currentStepEndTime == 0L

        json["players"] = playersToJSON(session)

        // Add speech info if available
        speechService.getSpeechSession(session.guildId)?.let { speechSession ->
            val speechJson = mutableMapOf<String, Any>()

            val orderIds = mutableListOf<Int>()
            for (p in speechSession.order) {
                orderIds.add(p.id)
            }
            speechJson["order"] = orderIds

            speechJson["currentSpeakerId"] = speechSession.lastSpeaker?.let { session.getPlayer(it) }?.id ?: -1
            speechJson["endTime"] = speechSession.currentSpeechEndTime
            speechJson["totalTime"] = speechSession.totalSpeechTime

            val interruptVotes = mutableListOf<Int>()
            for (uid in speechSession.interruptVotes) {
                interruptVotes.add(uid)
            }
            speechJson["interruptVotes"] = interruptVotes

            json["speech"] = speechJson
        }

        // Add Police/Poll info
        val policeJson = mutableMapOf<String, Any>()
        val gid = session.guildId

        val policeSession = WerewolfApplication.policeService.sessions[gid]
        if (policeSession != null) {
            policeJson["state"] = policeSession.state.name
            policeJson["stageEndTime"] = policeSession.stageEndTime
            policeJson["allowEnroll"] = policeSession.state.canEnroll()
            policeJson["allowUnEnroll"] = policeSession.state.canQuit()

            val candidatesList = mutableListOf<Map<String, Any>>()
            for (c in policeSession.candidates.values) {
                val candidateJson = mutableMapOf<String, Any>()
                candidateJson["id"] = c.player.id
                candidateJson["quit"] = c.quit
                candidateJson["voters"] = c.electors.map { it.toString() }
                candidatesList.add(candidateJson)
            }
            policeJson["candidates"] = candidatesList
        } else {
            policeJson["state"] = "NONE"
            policeJson["allowEnroll"] = false
            policeJson["allowUnEnroll"] = false
            policeJson["candidates"] = emptyList<Any>()
        }
        json["police"] = policeJson

        // Add Expel info
        val expelJson = mutableMapOf<String, Any>()
        if (expelService.hasPoll(gid)) {
            val expelCandidatesList = mutableListOf<Map<String, Any>>()
            expelService.getPollCandidates(gid)?.values?.forEach { c ->
                val candidateJson = mutableMapOf<String, Any>()
                candidateJson["id"] = c.player.id
                candidateJson["voters"] = c.electors.map { it.toString() }
                expelCandidatesList.add(candidateJson)
            }
            expelJson["candidates"] = expelCandidatesList
            expelJson["voting"] = true
            // Add endTime if expel session exists
            val expelSession = expelService.getExpelSession(gid)
            if (expelSession != null) {
                expelJson["endTime"] = expelSession.endTime
            }
        } else {
            expelJson["candidates"] = emptyList<Any>()
            expelJson["voting"] = false
        }
        json["expel"] = expelJson

        val guild = session.guild
        if (guild != null) {
            json["guildName"] = guild.name
            json["guildIcon"] = guild.iconUrl ?: ""
        }

        val logsJson = mutableListOf<Map<String, Any>>()
        for (log in session.logs) {
            val logJson = mutableMapOf<String, Any>()
            logJson["id"] = log.id ?: ""
            logJson["timestamp"] = formatTimestamp(log.timestamp)
            logJson["type"] = log.type?.getSeverity() ?: "INFO"
            logJson["message"] = log.message ?: ""
            log.metadata?.let { if (it.isNotEmpty()) logJson["metadata"] = it }
            logsJson.add(logJson)
        }
        json["logs"] = logsJson

        return json
    }

    private fun formatTimestamp(epochMillis: Long): String {
        val instant = Instant.ofEpochMilli(epochMillis)
        val zoneId = ZoneId.systemDefault()
        val dateTime = LocalDateTime.ofInstant(instant, zoneId)
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        return dateTime.format(formatter)
    }

    override fun playersToJSON(session: Session): List<Map<String, Any>> {
        val players = mutableListOf<Map<String, Any>>()
        for (player in session.players.values) {
            val playerJson = mutableMapOf<String, Any>()

            playerJson["id"] = player.id
            playerJson["roleId"] = player.role?.idLong?.toString() ?: ""
            playerJson["channelId"] = player.channel?.idLong?.toString() ?: ""
            playerJson["userId"] =
                if (player.user != null) player.user?.idLong.toString() else ""
            playerJson["roles"] = player.roles ?: emptyList<String>()
            playerJson["deadRoles"] = player.deadRoles ?: emptyList<String>()
            playerJson["isAlive"] = player.alive
            playerJson["jinBaoBao"] = player.jinBaoBao
            playerJson["police"] = player.police
            playerJson["idiot"] = player.idiot
            playerJson["duplicated"] = player.duplicated
            playerJson["rolePositionLocked"] = player.rolePositionLocked
            players.add(playerJson)
        }

        players.sortWith { a, b ->
            val idA = a["id"] as Int
            val idB = b["id"] as Int
            idA.compareTo(idB)
        }

        return players
    }

    override fun sessionToSummaryJSON(session: Session): Map<String, Any> {
        val summary = mutableMapOf<String, Any>()
        summary["guildId"] = session.guildId.toString()

        var guildName = "Unknown Server"
        var guildIcon: String? = null
        try {
            val guild = discordService.getGuild(session.guildId)
            if (guild != null) {
                guildName = guild.name
                guildIcon = guild.iconUrl
            }
        } catch (_: Exception) {
            log.warn("Failed to fetch guild info for summary: {}", session.guildId)
        }

        summary["guildName"] = guildName
        if (guildIcon != null)
            summary["guildIcon"] = guildIcon

        val pCount = session.players.size
        summary["playerCount"] = pCount
        log.info(
            "Summary for guild {}: name='{}', players={}",
            session.guildId,
            guildName,
            pCount
        )

        return summary
    }

    @Throws(Exception::class)
    override fun getGuildMembers(session: Session): List<Map<String, Any>> {
        val guild = session.guild ?: throw Exception("Guild not found")

        val membersJson = mutableListOf<Map<String, Any>>()

        for (member in guild.members) {
            if (member.user.isBot)
                continue

            val memberMap = mutableMapOf<String, Any>()
            memberMap["userId"] = member.id
            val isJudge = member.roles.stream()
                .anyMatch { r -> r == session.judgeRole }
            memberMap["isJudge"] = isJudge
            memberMap["isPlayer"] = member.player() != null

            membersJson.add(memberMap)
        }
        membersJson.sortWith { a, b ->
            val judgeA = a["isJudge"] as Boolean
            val judgeB = b["isJudge"] as Boolean
            if (judgeA != judgeB)
                if (judgeB) 1 else -1
            else
                0
        }
        return membersJson
    }

    @Throws(Exception::class)
    override fun updateUserRole(session: Session, userId: Long, role: UserRole) {
        val guild = session.guild ?: throw Exception("Guild not found")

        var member = guild.getMemberById(userId)
        if (member == null) {
            member = guild.retrieveMemberById(userId).complete()
        }

        val judgeRole = session.judgeRole
            ?: throw Exception("Judge role not configured or found in guild")

        if (role == UserRole.JUDGE) {
            guild.addRoleToMember(member!!, judgeRole).complete()
        } else if (role == UserRole.SPECTATOR) {
            guild.removeRoleFromMember(member!!, judgeRole).complete()

            val spectatorRole = session.spectatorRole
            if (spectatorRole != null) {
                guild.addRoleToMember(member, spectatorRole).complete()
            }
        } else {
            throw Exception("Unsupported role update: $role")
        }
    }

    override fun broadcastUpdate(guildId: Long) {
        val sessionOpt = getSession(guildId)
        sessionOpt.ifPresent { broadcastSessionUpdate(it) }
    }

    override fun broadcastSessionUpdate(session: Session) {
        try {
            val updateData = sessionToJSON(session)
            broadcastEvent("UPDATE", updateData)
        } catch (_: InterruptedException) {
            // Clear the interrupted flag so it doesn't affect other operations
            Thread.interrupted()
            log.warn("Broadcast was interrupted, but continuing")
        } catch (e: Exception) {
            log.error("Failed to broadcast session update", e)
        }
    }

    override fun broadcastEvent(type: String, data: Map<String, Any>) {
        try {
            val guildIdObj = data["guildId"]
            if (guildIdObj == null) {
                log.warn("Cannot broadcast event type {} without guildId in data", type)
                return
            }
            val guildId = if (guildIdObj is Long) guildIdObj.toString() else guildIdObj as String

            val mapper = jacksonObjectMapper()
            val envelope = mapOf("type" to type, "data" to data)
            val jsonMessage = mapper.writeValueAsString(envelope)

            webSocketHandler.broadcastToGuild(guildId, jsonMessage)
        } catch (_: InterruptedException) {
            // Thread was interrupted - clear the flag and continue
            Thread.interrupted()
            log.warn("Broadcast thread was interrupted")
        } catch (e: Exception) {
            log.error("Failed to broadcast event", e)
        }
    }

}
