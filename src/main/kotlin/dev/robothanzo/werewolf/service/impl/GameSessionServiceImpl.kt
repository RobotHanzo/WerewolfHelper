package dev.robothanzo.werewolf.service.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.controller.dto.GuildMemberDto
import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.game.model.GameSettings
import dev.robothanzo.werewolf.security.GlobalWebSocketHandler
import dev.robothanzo.werewolf.service.ExpelService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.SpeechService
import dev.robothanzo.werewolf.websocket.WebSocketEventData
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class GameSessionServiceImpl(
    private val sessionRepository: SessionRepository,
    private val webSocketHandler: GlobalWebSocketHandler,
    private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry,
) : GameSessionService {
    private val log = LoggerFactory.getLogger(GameSessionServiceImpl::class.java)
    private val sessionLocks = ConcurrentHashMap<Long, Any>()
    private val sessionCache = ConcurrentHashMap<Long, Session>()
    private val threadLocalSessions = ThreadLocal<MutableMap<Long, Session>>()

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
            // Transfer transient state to the new instance if it's different
            if (saved !== session) {
                saved.hydratedRoles = session.hydratedRoles
                saved.populatePlayerSessions()
            }
            // Update cache with the latest persisted instance
            sessionCache[session.guildId] = saved

            // Sync with thread-local if active
            threadLocalSessions.get()?.let { currentSessions ->
                if (currentSessions.containsKey(session.guildId)) {
                    currentSessions[session.guildId] = saved
                }
            }

            // Sync version back to the input object to prevent stale version errors 
            // if the caller continues to use the old reference.
            session.version = saved.version
            return saved
        }
    }

    override fun <T> withLockedSession(guildId: Long, block: (Session) -> T): T {
        val currentSessions =
            threadLocalSessions.get() ?: mutableMapOf<Long, Session>().also { threadLocalSessions.set(it) }
        val existingSession = currentSessions[guildId]

        if (existingSession != null) {
            // Reentrant call: already have the lock and session for this thread
            log.trace("Reentrant session lock for guild {}", guildId)
            return block(existingSession)
        }

        synchronized(getLock(guildId)) {
            // Check cache again inside lock as it might have changed
            val session = sessionCache[guildId] ?: getSession(guildId).orElseThrow { Exception("Session not found") }

            currentSessions[guildId] = session
            try {
                val result = block(session)
                saveSession(session)
                return result
            } finally {
                currentSessions.remove(guildId)
                if (currentSessions.isEmpty()) {
                    threadLocalSessions.remove()
                }
            }
        }
    }

    override fun deleteSession(guildId: Long) {
        synchronized(getLock(guildId)) {
            sessionRepository.deleteByGuildId(guildId)
            sessionCache.remove(guildId)
        }
    }


    @Throws(Exception::class)
    override fun getGuildMembers(session: Session): List<GuildMemberDto> {
        val guild = session.guild ?: throw Exception("Guild not found")

        val membersJson = mutableListOf<GuildMemberDto>()

        for (member in guild.members) {
            if (member.user.isBot)
                continue
            val memberDto = GuildMemberDto(
                id = member.id,
                name = member.effectiveName,
                avatar = member.effectiveAvatarUrl,
                display = member.effectiveName,
                roles = member.roles.map { it.id }
            )
            membersJson.add(memberDto)
        }
        membersJson.sortBy { it.name }

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
            val eventData = WebSocketEventData.SessionUpdate(
                guildId = session.guildId.toString(),
                session = session
            )
            broadcastEvent(eventData)
        } catch (_: InterruptedException) {
            // Clear the interrupted flag so it doesn't affect other operations
            Thread.interrupted()
            log.warn("Broadcast was interrupted, but continuing")
        } catch (e: Exception) {
            log.error("Failed to broadcast session update", e)
        }
    }

    override fun broadcastEvent(eventData: WebSocketEventData) {
        try {
            val guildId = when (eventData) {
                is WebSocketEventData.SessionUpdate -> eventData.guildId
                is WebSocketEventData.ProgressUpdate -> eventData.guildId
                is WebSocketEventData.PlayerUpdate -> {
                    log.warn("PlayerUpdate events should be broadcast directly, not through broadcastEvent")
                    return
                }
            }
            webSocketHandler.broadcastToGuild(guildId, eventData)
        } catch (_: InterruptedException) {
            // Thread was interrupted - clear the flag and continue
            Thread.interrupted()
            log.warn("Broadcast thread was interrupted")
        } catch (e: Exception) {
            log.error("Failed to broadcast event", e)
        }
    }
}
