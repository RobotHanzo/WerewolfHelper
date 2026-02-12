package dev.robothanzo.werewolf.database.documents

import dev.robothanzo.werewolf.database.ReplayRepository
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.GameSettings
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import io.swagger.v3.oas.annotations.media.Schema
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable

@Document(collection = "replays")
@Schema(description = "Structured game replay data")
data class Replay(
    @Id
    var id: ObjectId? = null,
    val sessionId: String, // UUID
    val startTime: Long,
    val endTime: Long,
    val settings: GameSettings,
    val players: Map<Int, ReplayPlayer>, // PlayerID -> Metadata
    val judgeList: List<Long>,       // Discord User IDs
    val spectatorList: List<Long>,   // Discord User IDs
    val timeline: Map<Int, ReplayDay>, // Day Number -> ReplayDay
    val logs: List<Session.LogEntry>, // Full session logs
    val result: Session.Result
) : Serializable {
    companion object {
        fun fromSession(session: Session): Replay {
            val players = session.players.values.associate { player ->
                val user = player.user
                player.id to ReplayPlayer(
                    id = player.id,
                    userId = player.userId ?: 0L,
                    username = user?.name ?: "Unknown",
                    avatarUrl = user?.effectiveAvatarUrl ?: "",
                    initialRoles = player.roles.toList(),
                    deathDay = if (!player.alive) session.day else null,
                    deathCause = null // Will be enriched below
                )
            }.toMutableMap()

            val timeline = mutableMapOf<Int, ReplayDay>()

            // Group actions and events by day
            for (dayNum in 0..session.day) {
                val polls = session.stateData.historicalPolls.filter { it.day == dayNum }
                val enrollments = session.stateData.policeEnrollmentHistory.filter {
                    // This is a simplification; ideally we'd have timestamps or stages
                    true
                }
                val transfers = session.stateData.policeTransferHistory.filter { it.day == dayNum }

                val dayEvents = mutableListOf<ReplayEvent>()

                polls.forEach { poll ->
                    dayEvents.add(
                        ReplayEvent(
                            type = ReplayEventType.POLL_END,
                            details = mapOf(
                                "title" to poll.title,
                                "votes" to poll.votes
                            )
                        )
                    )
                }

                transfers.forEach { transfer ->
                    dayEvents.add(
                        ReplayEvent(
                            type = ReplayEventType.POLICE_TRANSFER,
                            details = mapOf(
                                "from" to transfer.fromPlayerId,
                                "to" to transfer.toPlayerId,
                                "timestamp" to transfer.timestamp
                            )
                        )
                    )
                }

                timeline[dayNum] = ReplayDay(
                    day = dayNum,
                    nightActions = emptyList(), // Needs to be grouped by day in Session
                    nightEvents = emptyList(),
                    dayEvents = dayEvents
                )
            }

            // Enrich player deaths from logs if possible
            session.logs.filter { it.type == LogType.PLAYER_DIED }.forEach { log ->
                val pid = log.metadata?.get("playerId") as? Int
                val causeStr = log.metadata?.get("deathCause") as? String
                if (pid != null && players.containsKey(pid)) {
                    val cause = try {
                        DeathCause.valueOf(causeStr ?: "UNKNOWN")
                    } catch (e: Exception) {
                        DeathCause.UNKNOWN
                    }
                    players[pid] = players[pid]!!.copy(deathCause = cause)
                }
            }

            return Replay(
                sessionId = session.sessionId,
                startTime = session.stateData.phaseStartTime,
                endTime = System.currentTimeMillis(),
                settings = session.settings,
                players = players,
                judgeList = emptyList(),
                spectatorList = emptyList(),
                timeline = timeline,
                logs = session.logs.toList(),
                result = session.hasEnded(null)
            )
        }

        fun upsertFromSession(session: Session, repository: ReplayRepository) {
            val log = LoggerFactory.getLogger(Replay::class.java)
            try {
                log.info("Upserting replay for session ${session.sessionId}")
                val replay = fromSession(session)

                // Populate judge and spectator lists from the Discord session
                val guild = session.guild
                val judges = mutableListOf<Long>()
                val spectators = mutableListOf<Long>()

                if (guild != null) {
                    session.judgeRole?.let { role ->
                        guild.getMembersWithRoles(role).forEach { judges.add(it.idLong) }
                    }
                    session.spectatorRole?.let { role ->
                        guild.getMembersWithRoles(role).forEach { spectators.add(it.idLong) }
                    }
                }

                val finalReplay = replay.copy(
                    judgeList = judges,
                    spectatorList = spectators
                )

                // Find existing by sessionId to maintain ID if overwriting
                val existing = repository.findBySessionId(session.sessionId)
                if (existing.isPresent) {
                    finalReplay.id = existing.get().id
                }

                repository.save(finalReplay)
                log.info("Successfully upserted replay for session ${session.sessionId}")
            } catch (e: Exception) {
                log.error("Failed to upsert replay for session ${session.sessionId}", e)
            }
        }
    }
}

data class ReplayPlayer(
    val id: Int,
    val userId: Long,
    val username: String,
    val avatarUrl: String,
    val initialRoles: List<String>,
    val deathDay: Int?,
    val deathCause: DeathCause?
)

data class ReplayDay(
    val day: Int,
    val nightActions: List<RoleActionInstance>,
    val nightEvents: List<ReplayEvent>,
    val dayEvents: List<ReplayEvent>
)

data class ReplayEvent(
    val type: ReplayEventType,
    val details: Map<String, Any>
)

enum class ReplayEventType {
    DISCUSSION_START, DISCUSSION_END,
    POLL_START, POLL_END,
    POLICE_ENROLL, POLICE_UNENROLLED, POLICE_TRANSFER
}

enum class PoliceActionStage {
    ENROLLMENT, SPEECH, UNENROLLMENT
}
