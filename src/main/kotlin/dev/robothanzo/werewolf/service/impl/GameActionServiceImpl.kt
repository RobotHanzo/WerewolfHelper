package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.GameStateData
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.utils.ActionTask
import dev.robothanzo.werewolf.utils.runActions
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service

@Service
class GameActionServiceImpl(
    @param:Lazy private val gameSessionService: GameSessionService
) : GameActionService {
    @Throws(Exception::class)
    override fun resetGame(
        session: Session,
        statusCallback: (String) -> Unit,
        progressCallback: (Int) -> Unit
    ) {
        val guildId = session.guildId
        progressCallback(0)
        statusCallback("正在連線至 Discord...")

        val guild = session.guild ?: throw Exception("Guild not found")

        progressCallback(5)
        statusCallback("正在掃描需要清理的身分組...")

        val tasks = mutableListOf<ActionTask>()

        for (player in session.players.values) {
            val member = player.member ?: continue
            player.roles = mutableListOf()
            player.deadRoles = mutableListOf()
            player.police = false
            player.idiot = false
            player.jinBaoBao = false
            player.duplicated = false
            player.rolePositionLocked = false
            player.updateUserId(null)

            session.spectatorRole?.let {
                tasks.add(
                    ActionTask(
                        guild.removeRoleFromMember(member, it),
                        "已移除 " + member.effectiveName + " 的旁觀者身分組"
                    )
                )
            }

            player.role?.let {
                tasks.add(
                    ActionTask(
                        guild.removeRoleFromMember(member, it),
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
        session.stateData = GameStateData()
        session.currentStepEndTime = 0L
        session.day = 0
        session.addLog(LogType.GAME_RESET, "遊戲已重置", null)

        gameSessionService.saveSession(session)

        progressCallback(100)
        statusCallback("操作完成。")

        gameSessionService.broadcastUpdate(guildId)
    }

    override fun markPlayerDead(
        session: Session,
        playerId: Int,
        allowLastWords: Boolean,
        cause: DeathCause
    ) {
        val player = session.getPlayer(playerId) ?: throw RuntimeException("Player not found")
        player.died(cause, allowLastWords)
    }

    override fun revivePlayer(session: Session, playerId: Int) {
        val targetPlayer: Player? = session.getPlayer(playerId)

        if (targetPlayer == null || targetPlayer.deadRoles.isNullOrEmpty()) {
            throw Exception("Player has no dead roles to revive")
        }

        val rolesToRevive = targetPlayer.deadRoles?.toMutableList() ?: mutableListOf()
        for (role in rolesToRevive) {
            reviveRole(session, playerId, role)
        }
    }

    override fun reviveRole(session: Session, playerId: Int, role: String) {
        val guild = session.guild ?: throw Exception("Guild not found")
        val player = session.getPlayer(playerId) ?: throw Exception("Player not found")
        val member = player.member ?: throw Exception("Player member not found")
        val deadRoles = player.deadRoles
        if (deadRoles == null || !deadRoles.contains(role)) throw Exception("Role not dead")

        val wasFullyDead = !player.alive
        deadRoles.remove(role)

        val metadata = mutableMapOf<String, Any>()
        metadata["playerId"] = player.id
        metadata["playerName"] = player.nickname
        metadata["revivedRole"] = role
        session.addLog(LogType.PLAYER_REVIVED, player.nickname + " 的 " + role + " 身份已復活", metadata)
        if (wasFullyDead) {
            val spectatorRole = session.spectatorRole
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
        player.role?.let { guild.addRoleToMember(member, it) }?.queue()
        player.channel?.sendMessage("因為你復活了，所以你的角色變成了 $currentRoleName")?.queue()
        gameSessionService.saveSession(session)
    }

    override fun setPolice(session: Session, playerId: Int) {
        session.guild ?: throw Exception("Guild not found")
        session.police?.let {
            it.police = false
            it.updateNickname()
        }

        val player = session.getPlayer(playerId) ?: throw Exception("Player not found")
        player.police = true
        player.updateNickname()
        session.courtTextChannel?.sendMessage(":white_check_mark: 警徽已移交給 " + player.user?.asMention)?.queue()

        gameSessionService.saveSession(session)
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
        val session = gameSessionService.getSession(guildId).orElseThrow {
            RuntimeException("Session not found for guild ID: $guildId")
        }
        session.courtVoiceChannel?.let {
            for (member in it.members) {
                // Check if member is judge? Judge usually shouldn't be muted?
                // Logic: Mute everyone if mute=true.
                if (!member.user.isBot) {
                    member.mute(mute).queue()
                }
            }
        }
    }
}
