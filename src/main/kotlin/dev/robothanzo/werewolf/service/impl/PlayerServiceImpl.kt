package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.security.SessionRepository
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.PlayerService
import dev.robothanzo.werewolf.utils.ActionTask
import dev.robothanzo.werewolf.utils.MsgUtils
import dev.robothanzo.werewolf.utils.runActions
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PlayerServiceImpl(
    private val sessionRepository: SessionRepository,
    private val discordService: DiscordService,
    private val gameSessionService: GameSessionService
) : PlayerService {
    private val log = LoggerFactory.getLogger(PlayerServiceImpl::class.java)

    override fun getPlayersJSON(guildId: Long): List<Map<String, Any>> {
        val sessionOpt = sessionRepository.findByGuildId(guildId)
        if (sessionOpt.isEmpty) {
            throw RuntimeException("Session not found")
        }
        return gameSessionService.playersToJSON(sessionOpt.get())
    }

    @Throws(Exception::class)
    override fun setPlayerCount(
        guildId: Long,
        count: Int,
        onProgress: (String) -> Unit,
        onPercent: (Int) -> Unit
    ) {
        val session = sessionRepository.findByGuildId(guildId)
            .orElseThrow { RuntimeException("Session not found") }

        val jda = discordService.jda
        val guild = jda!!.getGuildById(guildId) ?: throw Exception("Guild not found")

        onPercent(0)
        onProgress("開始同步 Discord 狀態...")

        val players = session.players
        val deleteTasks = mutableListOf<ActionTask>()
        val createRoleTasks = mutableListOf<ActionTask>()
        val createChannelTasks = mutableListOf<ActionTask>()

        // Phase 1: Identify deletions
        val existingPlayerIds = players.keys.toMutableList()
        for (idStr in existingPlayerIds) {
            val pid = idStr.toInt()
            if (pid > count) {
                val player = players.remove(idStr)
                val role = guild.getRoleById(player!!.roleId)
                if (role != null) {
                    deleteTasks.add(
                        ActionTask(
                            role.delete(),
                            "刪除身分組: ${role.name}"
                        )
                    )
                }
                val channel = guild.getTextChannelById(player.channelId)
                if (channel != null) {
                    deleteTasks.add(
                        ActionTask(
                            channel.delete(),
                            "刪除頻道: ${channel.name}"
                        )
                    )
                }
            }
        }

        // Run Deletions (0% -> 30%)
        if (deleteTasks.isNotEmpty()) {
            deleteTasks.runActions(onProgress, onPercent, 0, 30, 60)
        }

        // Phase 2: Create Roles
        val spectatorRole = guild.getRoleById(session.spectatorRoleId)
        val newRolesMap: MutableMap<Int, Role> = ConcurrentHashMap()

        for (i in players.size + 1..count) {
            val name = "玩家" + Session.Player.ID_FORMAT.format((i).toLong())
            val task = ActionTask(
                guild.createRole()
                    .setColor(MsgUtils.randomColor)
                    .setHoisted(true)
                    .setName(name),
                "創建身分組: $name",
                { obj: Any? -> newRolesMap[i] = obj as Role }
            )
            createRoleTasks.add(task)
        }

        if (createRoleTasks.isNotEmpty()) {
            createRoleTasks.runActions(onProgress, onPercent, 30, 60, 60)
        }

        // Phase 3: Create Channels
        for ((playerId, role) in newRolesMap) {
            val name = "玩家" + Session.Player.ID_FORMAT.format((playerId).toLong())

            val task = ActionTask(
                guild.createTextChannel(name)
                    .addPermissionOverride(
                        spectatorRole ?: guild.publicRole,
                        Permission.VIEW_CHANNEL.rawValue,
                        Permission.MESSAGE_SEND.rawValue
                    )
                    .addPermissionOverride(
                        role,
                        listOf(
                            Permission.VIEW_CHANNEL,
                            Permission.MESSAGE_SEND
                        ),
                        listOf()
                    )
                    .addPermissionOverride(
                        guild.publicRole,
                        listOf(),
                        listOf(
                            Permission.VIEW_CHANNEL,
                            Permission.MESSAGE_SEND,
                            Permission.USE_APPLICATION_COMMANDS
                        )
                    ),
                "創建頻道: $name",
                { obj: Any? ->
                    val channel = obj as TextChannel
                    players[playerId.toString()] = Session.Player(
                        id = playerId,
                        roleId = role.idLong,
                        channelId = channel.idLong
                    )
                }
            )
            createChannelTasks.add(task)
        }

        if (createChannelTasks.isNotEmpty()) {
            createChannelTasks.runActions(onProgress, onPercent, 60, 95, 120)
        }

        session.players = players
        sessionRepository.save(session)
        onPercent(100)
        onProgress("同步完成！")
        gameSessionService.broadcastUpdate(guildId)
    }

    override fun updatePlayerRoles(guildId: Long, playerId: String, roles: List<String>) {
        try {
            val session = sessionRepository.findByGuildId(guildId)
                .orElseThrow { RuntimeException("Session not found") }
            val player = session.players[playerId] ?: throw Exception("Player not found")

            val finalRoles = roles.toMutableList()
            val isDuplicated = roles.contains("複製人")
            player.duplicated = isDuplicated
            if (isDuplicated && finalRoles.size == 2) {
                if (finalRoles[0] == "複製人")
                    finalRoles[0] = finalRoles[1]
                else if (finalRoles[1] == "複製人")
                    finalRoles[1] = finalRoles[0]
            }

            player.idiot = finalRoles.contains("白癡")
            val isJinBaoBao =
                session.doubleIdentities && finalRoles.size == 2 && finalRoles[0] == "平民" && finalRoles[1] == "平民"
            player.jinBaoBao = isJinBaoBao
            player.roles = finalRoles

            sessionRepository.save(session)

            val jda = discordService.jda
            if (jda != null && player.channelId != 0L) {
                val guild = jda.getGuildById(guildId)
                if (guild != null) {
                    val channel = guild.getTextChannelById(player.channelId)
                    channel?.sendMessage("法官已將你的身份更改為: ${roles.joinToString(", ")}")
                        ?.queue()
                }
            }

            gameSessionService.broadcastUpdate(guildId)
        } catch (e: Exception) {
            log.error("Failed to update player roles: {}", e.message, e)
            throw RuntimeException("Failed to update player roles", e)
        }
    }

    override fun switchRoleOrder(guildId: Long, playerId: String) {
        try {
            val session = sessionRepository.findByGuildId(guildId)
                .orElseThrow { RuntimeException("Session not found") }
            val player = session.players[playerId] ?: throw Exception("Player not found")

            if (player.rolePositionLocked)
                throw Exception("你的身分順序已被鎖定")

            val roles = player.roles
            if (roles == null || roles.size < 2)
                throw Exception("Not enough roles to switch")

            roles.let {
                val first = it[0]
                it[0] = it[1]
                it[1] = first
            }
            sessionRepository.save(session)

            val jda = discordService.jda
            if (jda != null && player.channelId != 0L) {
                val guild = jda.getGuildById(guildId)
                if (guild != null) {
                    val channel = guild.getTextChannelById(player.channelId)
                    channel?.sendMessage("你已交換了角色順序，現在主要角色為: ${roles[0]}")?.queue()
                }
            }
            gameSessionService.broadcastUpdate(guildId)
        } catch (e: Exception) {
            log.error("Switch role order failed: {}", e.message, e)
            throw RuntimeException("Failed to switch role order", e)
        }
    }

    override fun setRolePositionLock(guildId: Long, playerId: String, locked: Boolean) {
        try {
            val session = sessionRepository.findByGuildId(guildId)
                .orElseThrow { RuntimeException("Session not found") }
            val player = session.players[playerId] ?: throw Exception("Player not found")

            player.rolePositionLocked = locked
            sessionRepository.save(session)
            gameSessionService.broadcastUpdate(guildId)
        } catch (e: Exception) {
            log.error("Failed to set role position lock: {}", e.message, e)
            throw RuntimeException("Failed to set role position lock", e)
        }
    }
}
