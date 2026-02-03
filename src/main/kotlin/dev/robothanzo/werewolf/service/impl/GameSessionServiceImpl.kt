package dev.robothanzo.werewolf.service.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.commands.Poll
import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.security.GlobalWebSocketHandler
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.SpeechService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class GameSessionServiceImpl(
    private val sessionRepository: SessionRepository,
    private val discordService: DiscordService,
    private val webSocketHandler: GlobalWebSocketHandler,
    private val speechService: SpeechService,
    @Lazy stepList: List<GameStep>,
    @param:Lazy
    private val expelService: dev.robothanzo.werewolf.service.ExpelService
) : GameSessionService {
    private val log = LoggerFactory.getLogger(GameSessionServiceImpl::class.java)
    private val steps = stepList.associateBy { it.id }

    @PostConstruct
    fun init() {
        WerewolfApplication.gameSessionService = this
    }

    override fun getAllSessions(): List<Session> {
        return sessionRepository.findAll()
    }

    override fun getSession(guildId: Long): Optional<Session> {
        return sessionRepository.findByGuildId(guildId)
    }

    override fun createSession(guildId: Long): Session {
        val session = Session(guildId = guildId)

        // Initialize default settings based on guild member count
        // 10 players: witchCanSaveSelf=true
        // 12+ players: witchCanSaveSelf=false
        val jda = discordService.jda
        val guild = jda.getGuildById(guildId)
        val memberCount = guild?.memberCount ?: 10

        session.settings["witchCanSaveSelf"] = memberCount < 12
        session.settings["customRoles"] = mutableMapOf<String, Any>()
        
        return sessionRepository.save(session)
    }

    override fun saveSession(session: Session): Session {
        return sessionRepository.save(session)
    }

    override fun deleteSession(guildId: Long) {
        sessionRepository.deleteByGuildId(guildId)
    }

    override fun sessionToJSON(session: Session): Map<String, Any> {
        val json = mutableMapOf<String, Any>()
        val jda = discordService.jda

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

            val orderIds = mutableListOf<String>()
            for (p in speechSession.order) {
                orderIds.add(p.id.toString())
            }
            speechJson["order"] = orderIds

            if (speechSession.lastSpeaker != null) {
                var speakerId: String? = null
                for (p in session.players.values) {
                    if (p.userId != null && p.userId == speechSession.lastSpeaker) {
                        speakerId = p.id.toString()
                        break
                    }
                }
                speechJson["currentSpeakerId"] = speakerId ?: ""
            }

            speechJson["endTime"] = speechSession.currentSpeechEndTime
            speechJson["totalTime"] = speechSession.totalSpeechTime

            val interruptVotes = mutableListOf<String>()
            for (uid in speechSession.interruptVotes) {
                interruptVotes.add(uid.toString())
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
                candidateJson["id"] = c.player.id.toString()
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
        // Assuming dev.robothanzo.werewolf.commands.Poll.expelCandidates is accessible
        // Using fully qualified name for static access to Java
        if (Poll.expelCandidates.containsKey(gid)) {
            val expelCandidatesList = mutableListOf<Map<String, Any>>()
            for (c in Poll.expelCandidates[gid]!!.values) {
                val candidateJson = mutableMapOf<String, Any>()
                candidateJson["id"] = c.player.id.toString()
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

        val guild = jda.getGuildById(session.guildId)
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
        val jda = discordService.jda

        for ((_, player) in session.players) {
            val playerJson = mutableMapOf<String, Any>()

            playerJson["id"] = player.id.toString()
            playerJson["roleId"] = player.roleId.toString()
            playerJson["channelId"] = player.channelId.toString()
            playerJson["userId"] =
                if (player.userId != null) player.userId.toString() else ""
            playerJson["roles"] = player.roles ?: emptyList<String>()
            playerJson["deadRoles"] = player.deadRoles ?: emptyList<String>()
            playerJson["isAlive"] = player.isAlive
            playerJson["jinBaoBao"] = player.jinBaoBao
            playerJson["police"] = player.police
            playerJson["idiot"] = player.idiot
            playerJson["duplicated"] = player.duplicated
            playerJson["rolePositionLocked"] = player.rolePositionLocked

            var foundMember = false
            if (player.userId != null) {
                val guild = jda.getGuildById(session.guildId)
                if (guild != null) {
                    val member = guild.getMemberById(player.userId!!)
                    if (member != null) {
                        playerJson["name"] = player.nickname
                        playerJson["username"] = member.user.name
                        playerJson["avatar"] = member.effectiveAvatarUrl

                        val isJudge = member.roles.stream()
                            .anyMatch { r -> r.idLong == session.judgeRoleId }
                        playerJson["isJudge"] = isJudge

                        foundMember = true
                    }
                }
            }
            if (!foundMember) {
                playerJson["name"] = player.nickname
                playerJson["username"] = "Unknown"
                playerJson["avatar"] = ""
                playerJson["isJudge"] = false
            }

            players.add(playerJson)
        }

        players.sortWith { a, b ->
            val idA = (a["id"] as? String)?.toInt() ?: 0
            val idB = (b["id"] as? String)?.toInt() ?: 0
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
    override fun getGuildMembers(guildId: Long): List<Map<String, Any>> {
        val session = sessionRepository.findByGuildId(guildId)
            .orElseThrow { Exception("Session not found") }

        val jda = discordService.jda
        val guild = jda.getGuildById(guildId) ?: throw Exception("Guild not found")

        val membersJson = mutableListOf<Map<String, Any>>()

        for (member in guild.members) {
            if (member.user.isBot)
                continue

            val memberMap = mutableMapOf<String, Any>()
            memberMap["userId"] = member.id
            memberMap["username"] = member.user.name
            memberMap["name"] = member.effectiveName
            memberMap["avatar"] = member.effectiveAvatarUrl

            val isJudge = member.roles.stream()
                .anyMatch { r -> r.idLong == session.judgeRoleId }
            memberMap["isJudge"] = isJudge

            val isPlayer = session.players.values.stream()
                .anyMatch { p ->
                    (p.userId != null && p.userId == member.idLong
                            && p.isAlive)
                }
            memberMap["isPlayer"] = isPlayer

            membersJson.add(memberMap)
        }

        membersJson.sortWith { a, b ->
            val judgeA = a["isJudge"] as Boolean
            val judgeB = b["isJudge"] as Boolean
            if (judgeA != judgeB)
                if (judgeB) 1 else -1
            else
                (a["name"] as String).compareTo((b["name"] as String))
        }

        return membersJson
    }

    @Throws(Exception::class)
    override fun updateUserRole(guildId: Long, userId: Long, role: UserRole) {
        val session = sessionRepository.findByGuildId(guildId)
            .orElseThrow { Exception("Session not found") }

        val jda = discordService.jda
        val guild = jda.getGuildById(guildId) ?: throw Exception("Guild not found")

        var member = guild.getMemberById(userId)
        if (member == null) {
            member = guild.retrieveMemberById(userId).complete()
        }

        val judgeRole = guild.getRoleById(session.judgeRoleId)
            ?: throw Exception("Judge role not configured or found in guild")

        if (role == UserRole.JUDGE) {
            guild.addRoleToMember(member!!, judgeRole).complete()
        } else if (role == UserRole.SPECTATOR) {
            guild.removeRoleFromMember(member!!, judgeRole).complete()

            val spectatorRole = guild.getRoleById(session.spectatorRoleId)
            if (spectatorRole != null) {
                guild.addRoleToMember(member, spectatorRole).complete()
            }
        } else {
            throw Exception("Unsupported role update: $role")
        }
    }

    @Throws(Exception::class)
    override fun updateSettings(guildId: Long, settings: Map<String, Any>) {
        val session = sessionRepository.findByGuildId(guildId)
            .orElseThrow { Exception("Session not found") }

        if (settings.containsKey("doubleIdentities")) {
            session.doubleIdentities = settings["doubleIdentities"] as Boolean
        }
        if (settings.containsKey("muteAfterSpeech")) {
            session.muteAfterSpeech = settings["muteAfterSpeech"] as Boolean
        }

        saveSession(session)
    }

    override fun broadcastUpdate(guildId: Long) {
        val sessionOpt = getSession(guildId)
        sessionOpt.ifPresent { broadcastSessionUpdate(it) }
    }

    override fun broadcastSessionUpdate(session: Session) {
        try {
            val updateData = sessionToJSON(session)
            broadcastEvent("UPDATE", updateData)
        } catch (e: InterruptedException) {
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
        } catch (e: InterruptedException) {
            // Thread was interrupted - clear the flag and continue
            Thread.interrupted()
            log.warn("Broadcast thread was interrupted")
        } catch (e: Exception) {
            log.error("Failed to broadcast event", e)
        }
    }
}
