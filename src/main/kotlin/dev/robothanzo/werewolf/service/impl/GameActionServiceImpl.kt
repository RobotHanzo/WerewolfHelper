package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.utils.ActionTask
import dev.robothanzo.werewolf.utils.runActions
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service

@Service
class GameActionServiceImpl(
    @param:Lazy private val gameSessionService: GameSessionService,
    private val discordService: DiscordService,
    private val sessionRepository: SessionRepository,
    private val roleEventService: dev.robothanzo.werewolf.service.RoleEventService
) : GameActionService {
    private val log = LoggerFactory.getLogger(GameActionServiceImpl::class.java)

    @Throws(Exception::class)
    override fun resetGame(
        session: Session,
        statusCallback: (String) -> Unit,
        progressCallback: (Int) -> Unit
    ) {
        val guildId = session.guildId
        progressCallback(0)
        statusCallback("正在連線至 Discord...")

        val jda = discordService.jda
        val guild = jda.getGuildById(guildId) ?: throw Exception("Guild not found")

        progressCallback(5)
        statusCallback("正在掃描需要清理的身分組...")

        val tasks = mutableListOf<ActionTask>()

        for (player in session.players.values) {
            val currentUserId = player.userId

            player.userId = null
            player.roles = mutableListOf()
            player.deadRoles = mutableListOf()
            player.police = false
            player.idiot = false
            player.jinBaoBao = false
            player.duplicated = false
            player.rolePositionLocked = false

            if (currentUserId != null) {
                val member = guild.getMemberById(currentUserId)
                if (member != null) {
                    val spectatorRole = guild.getRoleById(session.spectatorRoleId)
                    if (spectatorRole != null) {
                        tasks.add(
                            ActionTask(
                                guild.removeRoleFromMember(member, spectatorRole),
                                "已移除 " + member.effectiveName + " 的旁觀者身分組"
                            )
                        )
                    }

                    val playerRole = guild.getRoleById(player.roleId)
                    if (playerRole != null) {
                        tasks.add(
                            ActionTask(
                                guild.removeRoleFromMember(member, playerRole),
                                "已移除玩家 " + player.id + " (" + member.effectiveName + ") 的玩家身分組"
                            )
                        )
                    }

                    // Nickname Reset
                    if (member.isOwner) {
                        statusCallback("  - [資訊] 跳過群主 " + member.effectiveName + " 的暱稱重置")
                    } else if (!guild.selfMember.canInteract(member)) {
                        statusCallback("  - [資訊] 無法重置 " + member.effectiveName + " 的暱稱 (機器人權限不足)")
                    } else if (member.nickname != null) {
                        tasks.add(
                            ActionTask(
                                member.modifyNickname(null),
                                "已重置 " + member.effectiveName + " 的暱稱"
                            )
                        )
                    }
                }
            }
        }

        if (tasks.isNotEmpty()) {
            statusCallback("正在執行 Discord 變更 (共 " + tasks.size + " 項)...")
            tasks.runActions(statusCallback, progressCallback, 5, 90, 30)
        } else {
            statusCallback("沒有偵測到需要清理的 Discord 變更。")
            progressCallback(90)
        }

        statusCallback("正在更新資料庫並清理日誌...")

        session.logs = mutableListOf()
        session.hasAssignedRoles = false
        session.currentState = "SETUP"
        session.stateData = mutableMapOf()
        session.currentStepEndTime = 0L
        session.day = 0
        session.addLog(LogType.GAME_RESET, "遊戲已重置", null)

        sessionRepository.save(session)

        progressCallback(100)
        statusCallback("操作完成。")

        gameSessionService.broadcastUpdate(guildId)
    }

    override fun markPlayerDead(
        session: Session,
        userId: Long,
        allowLastWords: Boolean,
        cause: DeathCause
    ) {
        val player = session.getPlayer(userId) ?: throw RuntimeException("Player not found")

        player.died(cause, allowLastWords)
    }

    override fun revivePlayer(session: Session, userId: Long) {
        // Logic for generic revive (maybe revive all roles? or just one?)
        // Player.java logic was reviveRole(..., roleToRevive)
        // revivePlayer in interface takes userId. 
        // Logic in Player.java `playerRevived` takes a specific role.
        // It seems revivePlayer intention might be to revive 'last dead role' or 'all'?
        // However, looking at usage in `GameActionServiceImpl` previously, it looped through all dead roles and revived them.

        try {
            val jda = discordService.jda
            val guild = jda.getGuildById(session.guildId) ?: throw Exception("Guild not found")

            var member = guild.getMemberById(userId)
            if (member == null) {
                member = guild.retrieveMemberById(userId).complete()
            }

            val targetPlayer: Player? = session.getPlayer(userId)

            if (targetPlayer == null || targetPlayer.deadRoles.isNullOrEmpty()) {
                throw Exception("Player has no dead roles to revive")
            }

            val rolesToRevive = targetPlayer.deadRoles?.toMutableList() ?: mutableListOf()
            for (role in rolesToRevive) {
                reviveRole(session, userId, role)
            }
            // updates broadcasted in reviveRole
        } catch (e: Exception) {
            log.error("Failed to revive player: {}", e.message, e)
            throw RuntimeException("Failed to revive player", e)
        }
    }

    override fun reviveRole(session: Session, userId: Long, role: String) {
        try {
            val guildId = session.guildId
            val guild = session.guild ?: throw Exception("Guild not found")

            var member = guild.getMemberById(userId)
            if (member == null) {
                member = guild.retrieveMemberById(userId).complete()
            }

            var success = false
            for (player in session.players.values) {
                if (member?.idLong == player.userId) {
                    val deadRoles = player.deadRoles
                    if (deadRoles == null || !deadRoles.contains(role)) {
                        // Not dead with this role
                        break
                    }

                    val wasFullyDead = !player.isAlive
                    deadRoles.remove(role)

                    sessionRepository.save(session)

                    val metadata = mutableMapOf<String, Any>()
                    metadata["playerId"] = player.id
                    metadata["playerName"] = player.nickname
                    metadata["revivedRole"] = role
                    session.addLog(LogType.PLAYER_REVIVED, player.nickname + " 的 " + role + " 身份已復活", metadata)
                    if (wasFullyDead) {
                        val spectatorRole = guild.getRoleById(session.spectatorRoleId)
                        if (spectatorRole != null) {
                            guild.removeRoleFromMember(member, spectatorRole).queue()
                        }
                    }
                    val newName = player.nickname
                    if (member.effectiveName != newName) {
                        member.modifyNickname(newName).queue()
                    }

                    val remainingRoles = player.roles?.toMutableList() ?: mutableListOf()
                    player.deadRoles?.forEach { deadRole ->
                        remainingRoles.remove(deadRole)
                    }
                    
                    val currentRoleName = if (remainingRoles.isEmpty()) "未知" else remainingRoles.first()

                    val roleId = player.roleId
                    if (roleId != 0L) {
                        val roleObj = guild.getRoleById(roleId)
                        if (roleObj != null) {
                            guild.addRoleToMember(member, roleObj).queue()
                        }
                    }
                    player.send("因為你復活了，所以你的角色變成了 $currentRoleName")
                    gameSessionService.broadcastSessionUpdate(session)
                    success = true
                    break
                }
            }

            if (!success)
                throw Exception("Failed to revive role: Player not found or role not dead")

            sessionRepository.save(session)
            gameSessionService.broadcastUpdate(guildId)
        } catch (e: Exception) {
            log.error("Failed to revive player role: {}", e.message, e)
            throw RuntimeException("Failed to revive player role", e)
        }
    }

    override fun setPolice(session: Session, userId: Long) {
        try {
            val guildId = session.guildId
            val jda = discordService.jda
            val guild = jda.getGuildById(guildId) ?: throw Exception("Guild not found")

            for (player in session.players.values) {
                if (player.police) {
                    player.police = false
                    player.userId?.let { uid ->
                        val member = guild.getMemberById(uid)
                        if (member != null) {
                            val newName = player.nickname
                            if (member.effectiveName != newName) {
                                member.modifyNickname(newName).queue()
                            }
                        }
                    }
                }
            }

            var targetPlayer: Player? = null
            for (player in session.players.values) {
                if (player.userId != null && player.userId == userId) {
                    player.police = true
                    targetPlayer = player
                    break
                }
            }

            if (targetPlayer == null)
                throw Exception("Player not found")

            targetPlayer.userId?.let { uid ->
                val member = guild.getMemberById(uid)
                if (member != null) {
                    val newName = targetPlayer.nickname
                    if (member.effectiveName != newName) {
                        member.modifyNickname(newName).queue()
                    }
                    val courtChannel = guild.getTextChannelById(session.courtTextChannelId)
                    courtChannel?.sendMessage(":white_check_mark: 警徽已移交給 " + member.asMention)
                        ?.queue()
                }
            }

            sessionRepository.save(session)
            gameSessionService.broadcastUpdate(guildId)
        } catch (e: Exception) {
            log.error("Failed to set police: {}", e.message, e)
            throw RuntimeException("Failed to set police", e)
        }
    }

    override fun broadcastProgress(guildId: Long, message: String?, percent: Int?) {
        val data = mutableMapOf<String, Any>()
        data["guildId"] = guildId.toString()
        if (message != null)
            data["message"] = message
        if (percent != null)
            data["percent"] = percent
        gameSessionService.broadcastEvent("PROGRESS", data)
    }

    override fun muteAll(guildId: Long, mute: Boolean) {
        val jda = discordService.jda
        val guild = jda.getGuildById(guildId) ?: return

        val session = sessionRepository.findByGuildId(guildId).orElse(null) ?: return

        // Mute voice channel logic if applicable (typically mute everyone in court voice channel)
        if (session.courtVoiceChannelId != 0L) {
            val voiceChannel = guild.getVoiceChannelById(session.courtVoiceChannelId)
            if (voiceChannel != null) {
                // This is a naive implementation; improved one would iterate members or use channel overrides
                // But for basic bot logic, we often rely on SpeechService to manage specific mutes.
                // However, user asked for "muteAll".
                // Let's defer to SpeechService's setAllMute logic but implemented here or call it?
                // For now, I'll invoke speechService if available or implement basic member mute.
                // Given `GameActionServiceImpl` dependencies, injecting `SpeechService` might cause cycles.

                // Let's implement directly: Mute all members in the VC
                for (member in voiceChannel.members) {
                    // Check if member is judge? Judge usually shouldn't be muted?
                    // Logic: Mute everyone if mute=true.
                    if (!member.user.isBot) {
                        member.mute(mute).queue()
                    }
                }
            }
        }
    }
}
