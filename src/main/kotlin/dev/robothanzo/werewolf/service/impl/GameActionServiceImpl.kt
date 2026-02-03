package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.security.SessionRepository
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
    @Lazy private val gameSessionService: GameSessionService,
    private val discordService: DiscordService,
    private val sessionRepository: SessionRepository
) : GameActionService {
    private val log = LoggerFactory.getLogger(GameActionServiceImpl::class.java)

    @Throws(Exception::class)
    override fun resetGame(
        guildId: Long,
        statusCallback: (String) -> Unit,
        progressCallback: (Int) -> Unit
    ) {
        val session = sessionRepository.findByGuildId(guildId)
            .orElseThrow { RuntimeException("Session not found") }

        progressCallback(0)
        statusCallback("正在連線至 Discord...")

        val jda = discordService.jda
        val guild = jda!!.getGuildById(guildId) ?: throw Exception("Guild not found")

        progressCallback(5)
        statusCallback("正在掃描需要清理的身分組...")

        val tasks = mutableListOf<ActionTask>()
        val spectatorRole = guild.getRoleById(session.spectatorRoleId)

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
        session.addLog(LogType.GAME_RESET, "遊戲已重置", null)

        sessionRepository.save(session)

        progressCallback(100)
        statusCallback("操作完成。")

        gameSessionService.broadcastUpdate(guildId)
    }

    override fun markPlayerDead(guildId: Long, userId: Long, allowLastWords: Boolean) {
        try {
            val session = sessionRepository.findByGuildId(guildId)
                .orElseThrow { RuntimeException("Session not found") }

            val jda = discordService.jda
            val guild = jda!!.getGuildById(guildId) ?: throw Exception("Guild not found")
            val spectatorRole = guild.getRoleById(session.spectatorRoleId)!!

            var member = guild.getMemberById(userId)
            if (member == null) {
                member = guild.retrieveMemberById(userId).complete()
            }

            var lastWords = allowLastWords
            var success = false

            // Logic absorbed from Player.java playerDied
            for ((_, player) in session.players) {
                if (member!!.idLong == player.userId) {
                    if (!player.isAlive) {
                        break // Already dead
                    }

                    // Soft kill logic
                    val roles = player.roles ?: mutableListOf()
                    var deadRoles = player.deadRoles
                    if (deadRoles == null) {
                        deadRoles = mutableListOf()
                        player.deadRoles = deadRoles
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

                    sessionRepository.save(session) // Persist dead role update

                    if (killedRole != null) {
                        val metadata = mutableMapOf<String, Any>()
                        metadata["playerId"] = player.id
                        metadata["playerName"] = player.nickname
                        metadata["killedRole"] = killedRole
                        metadata["isExpelled"] =
                            false // markPlayerDead is usually manual or command based, not expel? Or generic. Assuming generic non-expel for now or handled elsewhere?
                        // original logic had isExpelled param. GameActionService does not?
                        // Command Player.died passes false for isExpelled.

                        session.addLog(
                            LogType.PLAYER_DIED,
                            player.nickname + " 的 " + killedRole + " 身份已死亡",
                            metadata
                        )

                        val courtChannel = guild.getTextChannelById(session.courtTextChannelId)
                        courtChannel?.sendMessage("**:skull: " + member.asMention + " 已死亡**")?.queue()
                    }

                    val result = session.hasEnded(killedRole)
                    if (result != Session.Result.NOT_ENDED) {
                        val channel = guild.getTextChannelById(session.spectatorTextChannelId)
                        val judgePing = "<@&" + session.judgeRoleId + "> "
                        if (channel != null) {
                            if (result == Session.Result.WOLVES_DIED) {
                                channel.sendMessage(judgePing + "遊戲結束，**好**人獲勝，原因：" + result.reason).queue()
                            } else {
                                channel.sendMessage(judgePing + "遊戲結束，**狼**人獲勝，原因：" + result.reason).queue()
                            }
                            lastWords = false
                        }
                    }

                    if (player.isAlive) {
                        val remainingRoles = player.roles?.toMutableList() ?: mutableListOf()
                        if (player.deadRoles != null) {
                            for (deadRole in player.deadRoles!!) {
                                remainingRoles.remove(deadRole)
                            }
                        }
                        val remainingRoleName = if (remainingRoles.isEmpty()) "未知" else remainingRoles.first()

                        guild.getTextChannelById(player.channelId)!!
                            .sendMessage("因為你死了，所以你的角色變成了 $remainingRoleName").queue()

                        sessionRepository.save(session)

                        if (lastWords) {
                            // Ideally access SpeechService. But GameActionServiceImpl might not have it injected?
                            // Need to check dependencies.
                            // If missing, I might need to add it or use Application context if cyclic dep.
                            // Assuming for now I can't easily call startLastWordsSpeech if simpler approach exists or just ignore lastwords automation for this refactor step if critical?
                            // Player.java called WerewolfApplication.speechService.
                            WerewolfApplication.speechService.startLastWordsSpeech(
                                guild,
                                session.courtTextChannelId,
                                player,
                                null
                            )
                        }
                        success = true
                        break
                    }

                    // Fully dead
                    val die = {
                        WerewolfApplication.policeService.transferPolice(session, guild, player) {
                            // Callback after transfer
                            // Re-fetch session in case it changed?
                            val newSession =
                                sessionRepository.findByGuildId(guildId).orElse(null) ?: return@transferPolice

                            // We need to re-find player object
                            val newPlayer = newSession.players[player.id.toString()] ?: return@transferPolice

                            // Actually we need to check if white idiot logic applies.
                            // isExpelled is false here based on usage.
                            // So ordinary death.

                            guild.modifyMemberRoles(member, spectatorRole).queue()

                            // Update DB
                            sessionRepository.save(newSession)
                            newPlayer.updateNickname(member)
                            gameSessionService.broadcastSessionUpdate(newSession)
                        }
                    }

                    if (lastWords) {
                        WerewolfApplication.speechService.startLastWordsSpeech(
                            guild,
                            session.courtTextChannelId,
                            player,
                            die
                        )
                    } else {
                        die()
                    }
                    success = true
                    break
                }
            }

            if (!success) {
                // Player not found in session map, allow spectator role?
                // Player.java logic:
                // guild.addRoleToMember(user, spectatorRole).queue();
                // user.modifyNickname("[旁觀] " + user.getEffectiveName()).queue();
                guild.addRoleToMember(member!!, spectatorRole).queue()
                member.modifyNickname("[旁觀] " + member.effectiveName).queue()
            }

            sessionRepository.save(session)
            gameSessionService.broadcastUpdate(guildId)
        } catch (e: Exception) {
            log.error("Failed to mark player dead: {}", e.message, e)
            throw RuntimeException("Failed to mark player dead", e)
        }
    }

    override fun revivePlayer(guildId: Long, userId: Long) {
        // Logic for generic revive (maybe revive all roles? or just one?)
        // Player.java logic was reviveRole(..., roleToRevive)
        // revivePlayer in interface takes userId. 
        // Logic in Player.java `playerRevived` takes a specific role.
        // It seems revivePlayer intention might be to revive 'last dead role' or 'all'?
        // However, looking at usage in `GameActionServiceImpl` previously, it looped through all dead roles and revived them.

        try {
            val session = sessionRepository.findByGuildId(guildId)
                .orElseThrow { RuntimeException("Session not found") }

            val jda = discordService.jda
            val guild = jda!!.getGuildById(guildId) ?: throw Exception("Guild not found")

            var member = guild.getMemberById(userId)
            if (member == null) {
                member = guild.retrieveMemberById(userId).complete()
            }

            var targetPlayer: Session.Player? = null
            for (p in session.players.values) {
                if (p.userId != null && p.userId == userId) {
                    targetPlayer = p
                    break
                }
            }

            if (targetPlayer == null || targetPlayer.deadRoles == null || targetPlayer.deadRoles!!.isEmpty()) {
                throw Exception("Player has no dead roles to revive")
            }

            val rolesToRevive = targetPlayer.deadRoles?.toMutableList() ?: mutableListOf()
            for (role in rolesToRevive) {
                reviveRole(guildId, userId, role)
            }
            // updates broadcasted in reviveRole
        } catch (e: Exception) {
            log.error("Failed to revive player: {}", e.message, e)
            throw RuntimeException("Failed to revive player", e)
        }
    }

    override fun reviveRole(guildId: Long, userId: Long, role: String) {
        try {
            val session = sessionRepository.findByGuildId(guildId)
                .orElseThrow { RuntimeException("Session not found") }

            val jda = discordService.jda
            val guild = jda!!.getGuildById(guildId) ?: throw Exception("Guild not found")
            val spectatorRole = guild.getRoleById(session.spectatorRoleId)!!

            var member = guild.getMemberById(userId)
            if (member == null) {
                member = guild.retrieveMemberById(userId).complete()
            }

            var success = false
            for (player in session.players.values) {
                if (member!!.idLong == player.userId) {
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
                        guild.removeRoleFromMember(member, spectatorRole).queue()
                    }
                    player.updateNickname(member)

                    val remainingRoles = player.roles?.toMutableList() ?: mutableListOf()
                    if (player.deadRoles != null) {
                        for (deadRole in player.deadRoles!!) {
                            remainingRoles.remove(deadRole)
                        }
                    }
                    val currentRoleName = if (remainingRoles.isEmpty()) "未知" else remainingRoles.first()

                    val roleId = player.roleId
                    if (roleId != 0L) {
                        val roleObj = guild.getRoleById(roleId)
                        if (roleObj != null) {
                            guild.addRoleToMember(member, roleObj).queue()
                        }
                    }

                    val channel = guild.getTextChannelById(player.channelId)
                    channel?.sendMessage("因為你復活了，所以你的角色變成了 $currentRoleName")?.queue()

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

    override fun setPolice(guildId: Long, userId: Long) {
        try {
            val session = sessionRepository.findByGuildId(guildId)
                .orElseThrow { RuntimeException("Session not found") }

            val jda = discordService.jda
            val guild = jda!!.getGuildById(guildId) ?: throw Exception("Guild not found")

            for (player in session.players.values) {
                if (player.police) {
                    player.police = false
                    if (player.userId != null) {
                        val member = guild.getMemberById(player.userId!!)
                        if (member != null)
                            player.updateNickname(member)
                    }
                }
            }

            var targetPlayer: Session.Player? = null
            for (player in session.players.values) {
                if (player.userId != null && player.userId == userId) {
                    player.police = true
                    targetPlayer = player
                    break
                }
            }

            if (targetPlayer == null)
                throw Exception("Player not found")

            if (targetPlayer.userId != null) {
                val member = guild.getMemberById(targetPlayer.userId!!)
                if (member != null) {
                    targetPlayer.updateNickname(member)
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
        val session = sessionRepository.findByGuildId(guildId)
            .orElseThrow { RuntimeException("Session not found") }

        val jda = discordService.jda
        val guild = jda!!.getGuildById(guildId) ?: return

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
