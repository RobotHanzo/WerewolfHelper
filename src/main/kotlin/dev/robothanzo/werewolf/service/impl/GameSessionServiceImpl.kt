package dev.robothanzo.werewolf.service.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.commands.Poll
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.security.GlobalWebSocketHandler
import dev.robothanzo.werewolf.security.SessionRepository
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.SpeechService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
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
    private val speechService: SpeechService
) : GameSessionService {
    private val log = LoggerFactory.getLogger(GameSessionServiceImpl::class.java)

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
        return sessionRepository.save(session)
    }

    override fun saveSession(session: Session): Session {
        return sessionRepository.save(session)
    }

    override fun deleteSession(guildId: Long) {
        sessionRepository.deleteByGuildId(guildId)
    }

    override fun sessionToJSON(session: Session): Map<String, Any> {
        val json: MutableMap<String, Any> = LinkedHashMap()
        val jda = discordService.jda

        json["guildId"] = session.guildId.toString()
        json["doubleIdentities"] = session.doubleIdentities
        json["muteAfterSpeech"] = session.muteAfterSpeech
        json["hasAssignedRoles"] = session.hasAssignedRoles
        json["roles"] = session.roles
        json["players"] = playersToJSON(session)

        // Add speech info if available
        if (speechService.getSpeechSession(session.guildId) != null) {
            val speechSession = speechService.getSpeechSession(session.guildId)!!
            val speechJson: MutableMap<String, Any> = LinkedHashMap()

            val orderIds: MutableList<String> = ArrayList()
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

            val interruptVotes: MutableList<String> = ArrayList()
            for (uid in speechSession.interruptVotes) {
                interruptVotes.add(uid.toString())
            }
            speechJson["interruptVotes"] = interruptVotes

            json["speech"] = speechJson
        }

        // Add Police/Poll info
        val policeJson: MutableMap<String, Any> = LinkedHashMap()
        val gid = session.guildId

        val policeSession = WerewolfApplication.policeService!!.sessions[gid]
        if (policeSession != null) {
            policeJson["state"] = policeSession.state.name
            policeJson["stageEndTime"] = policeSession.stageEndTime
            policeJson["allowEnroll"] = policeSession.state.canEnroll()
            policeJson["allowUnEnroll"] = policeSession.state.canQuit()

            val candidatesList: MutableList<Map<String, Any>> = ArrayList()
            for (c in policeSession.candidates.values) {
                val candidateJson: MutableMap<String, Any> = LinkedHashMap()
                candidateJson["id"] = c.player!!.id.toString()
                candidateJson["quit"] = c.quit
                val voters: MutableList<String> = ArrayList()
                for (voterId in c.electors) {
                    voters.add(voterId.toString())
                }
                candidateJson["voters"] = voters
                candidatesList.add(candidateJson)
            }
            policeJson["candidates"] = candidatesList
        } else {
            policeJson["state"] = "NONE"
            policeJson["allowEnroll"] = false
            policeJson["allowUnEnroll"] = false
            policeJson["candidates"] = Collections.emptyList<Any>()
        }
        json["police"] = policeJson

        // Add Expel info
        val expelJson: MutableMap<String, Any> = LinkedHashMap()
        // Assuming dev.robothanzo.werewolf.commands.Poll.expelCandidates is accessible
        // Using fully qualified name for static access to Java
        if (Poll.expelCandidates.containsKey(gid)) {
            val expelCandidatesList: MutableList<Map<String, Any>> = ArrayList()
            for (c in Poll.expelCandidates[gid]!!.values) {
                val candidateJson: MutableMap<String, Any> = LinkedHashMap()
                candidateJson["id"] = c.player!!.id.toString()
                val voters: MutableList<String> = ArrayList()
                for (voterId in c.electors) {
                    voters.add(voterId.toString())
                }
                candidateJson["voters"] = voters
                expelCandidatesList.add(candidateJson)
            }
            expelJson["candidates"] = expelCandidatesList
            expelJson["voting"] = true
        } else {
            expelJson["candidates"] = Collections.emptyList<Any>()
            expelJson["voting"] = false
        }
        json["expel"] = expelJson

        if (jda != null) {
            val guild = jda.getGuildById(session.guildId)
            if (guild != null) {
                json["guildName"] = guild.name
                json["guildIcon"] = guild.iconUrl ?: ""
            }
        }

        val logsJson: MutableList<Map<String, Any>> = ArrayList()
        if (session.logs != null) {
            for (log in session.logs) {
                val logJson: MutableMap<String, Any> = LinkedHashMap()
                logJson["id"] = log.id ?: ""
                logJson["timestamp"] = formatTimestamp(log.timestamp)
                logJson["type"] = log.type?.getSeverity() ?: "INFO"
                logJson["message"] = log.message ?: ""
                val metadata = log.metadata
                if (metadata != null && metadata.isNotEmpty()) {
                    logJson["metadata"] = metadata
                }
                logsJson.add(logJson)
            }
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
        val players: MutableList<Map<String, Any>> = ArrayList()
        val jda = discordService.jda

        for ((_, player) in session.players) {
            val playerJson: MutableMap<String, Any> = LinkedHashMap()

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
            if (jda != null && player.userId != null) {
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
        val summary: MutableMap<String, Any> = LinkedHashMap()
        summary["guildId"] = session.guildId.toString()

        var guildName = "Unknown Server"
        var guildIcon: String? = null
        try {
            val guild = discordService.getGuild(session.guildId)
            if (guild != null) {
                guildName = guild.name
                guildIcon = guild.iconUrl
            }
        } catch (e: Exception) {
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

        val jda = discordService.jda ?: throw Exception("JDA instance is required")

        val guild = jda.getGuildById(guildId) ?: throw Exception("Guild not found")

        val membersJson: MutableList<Map<String, Any>> = ArrayList()

        for (member in guild.members) {
            if (member.user.isBot)
                continue

            val memberMap: MutableMap<String, Any> = LinkedHashMap()
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
                if (judgeB) 1 else -1;
            else
                (a["name"] as String).compareTo((b["name"] as String))
        }

        return membersJson
    }

    @Throws(Exception::class)
    override fun updateUserRole(guildId: Long, userId: Long, role: UserRole) {
        val session = sessionRepository.findByGuildId(guildId)
            .orElseThrow { Exception("Session not found") }

        val jda = discordService.jda ?: throw Exception("JDA instance is required")
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
        broadcastSessionUpdate(session)
    }

    override fun broadcastUpdate(guildId: Long) {
        val sessionOpt = getSession(guildId)
        if (sessionOpt.isPresent) {
            val updateData = sessionToJSON(sessionOpt.get())
            broadcastEvent("UPDATE", updateData)
        }
    }

    override fun broadcastSessionUpdate(session: Session) {
        broadcastUpdate(session.guildId)
    }

    override fun broadcastEvent(type: String, data: Map<String, Any>) {
        try {
            val mapper = jacksonObjectMapper()
            val envelope = mapOf("type" to type, "data" to data)
            val jsonMessage = mapper.writeValueAsString(envelope)

            webSocketHandler.broadcast(jsonMessage)
        } catch (e: Exception) {
            log.error("Failed to broadcast event", e)
        }
    }
}
