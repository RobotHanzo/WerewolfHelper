package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.security.SessionRepository
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.RoleService
import dev.robothanzo.werewolf.utils.ActionTask
import dev.robothanzo.werewolf.utils.CmdUtils
import dev.robothanzo.werewolf.utils.MsgUtils
import dev.robothanzo.werewolf.utils.runActions
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.Member
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class RoleServiceImpl(
    private val sessionRepository: SessionRepository,
    private val discordService: DiscordService,
    private val gameSessionService: GameSessionService
) : RoleService {
    private val log = LoggerFactory.getLogger(RoleServiceImpl::class.java)

    override fun addRole(guildId: Long, roleName: String, amount: Int) {
        try {
            val session = sessionRepository.findByGuildId(guildId)
                .orElseThrow { RuntimeException("Session not found") }

            val roles = ArrayList(session.roles)
            for (i in 0 until amount) {
                roles.add(roleName)
            }
            session.roles = roles
            sessionRepository.save(session)

            gameSessionService.broadcastUpdate(guildId)
        } catch (e: Exception) {
            log.error("Failed to add role: {}", e.message, e)
            throw RuntimeException("Failed to add role", e)
        }
    }

    override fun removeRole(guildId: Long, roleName: String, amount: Int) {
        try {
            val session = sessionRepository.findByGuildId(guildId)
                .orElseThrow { RuntimeException("Session not found") }

            val roles = ArrayList(session.roles)
            for (i in 0 until amount) {
                roles.remove(roleName)
            }
            session.roles = roles
            sessionRepository.save(session)

            gameSessionService.broadcastUpdate(guildId)
        } catch (e: Exception) {
            log.error("Failed to remove role: {}", e.message, e)
            throw RuntimeException("Failed to remove role", e)
        }
    }

    override fun getRoles(guildId: Long): List<String> {
        val session = sessionRepository.findByGuildId(guildId)
            .orElseThrow { RuntimeException("Session not found") }
        return session.roles
    }

    override fun assignRoles(guildId: Long, statusLogger: (String) -> Unit, progressCallback: (Int) -> Unit) {
        try {
            val session = sessionRepository.findByGuildId(guildId)
                .orElseThrow { RuntimeException("Session not found") }

            if (session.hasAssignedRoles) {
                throw Exception("Roles already assigned")
            }

            val totalPlayers = session.players.size
            log.info("Starting role assignment for guild {} with {} players", guildId, totalPlayers)

            progressCallback(0)
            statusLogger("正在掃描伺服器玩家...")

            val jda = discordService.jda
            val guild = jda!!.getGuildById(guildId) ?: throw Exception("Guild not found")

            val pending: MutableList<Member> = LinkedList()
            for (member in guild.members) {
                if ((!member.user.isBot) &&
                    (!member.isOwner) &&
                    (!member.roles.contains(guild.getRoleById(session.judgeRoleId))) &&
                    (!member.roles.contains(guild.getRoleById(session.spectatorRoleId)))
                ) {
                    pending.add(member)
                }
            }

            progressCallback(10)
            statusLogger("正在驗證玩家與身分數量...")

            pending.shuffle(Random())
            if (pending.size != session.players.size) {
                throw Exception(
                    "玩家數量不符合設定值。請確認是否已給予旁觀者應有之身分(使用 `/player died`)，或檢查 `/server set players` 設定的人數。\n(待分配: "
                            + pending.size + ", 需要: " + session.players.size + ")"
                )
            }

            val rolesPerPlayer = if (session.doubleIdentities) 2 else 1
            if (pending.size != (session.roles.size / rolesPerPlayer)) {
                throw Exception(
                    "玩家身分數量不符合身分清單數量。請確認是否正確啟用雙身分模式，並檢查 `/server roles list`。\n(目前玩家: " + pending.size
                            + ", 身分總數: " + session.roles.size + ")"
                )
            }

            val roles = ArrayList(session.roles)
            roles.shuffle()

            var gaveJinBaoBao = 0
            statusLogger("正在分配身分並更新伺服器狀態...")

            val playersList = ArrayList(session.players.values)
            playersList.sortBy { it.id }

            val tasks: MutableList<ActionTask> = ArrayList()

            for (player in playersList) {
                val member = pending[player.id - 1]

                // 1. Prepare Discord Role Task
                val playerRole = guild.getRoleById(player.roleId)
                if (playerRole != null) {
                    tasks.add(
                        ActionTask(
                            guild.addRoleToMember(member, playerRole),
                            "已套用身分組: " + playerRole.name + " 給 " + member.effectiveName
                        )
                    )
                }

                // 2. Logic for role selection (JinBaoBao, etc.)
                var rs: MutableList<String> = LinkedList()
                var isJinBaoBao = false
                rs.add(roles.removeFirst())

                if (rs.first() == "白癡") {
                    player.idiot = true
                }

                if (rs.first() == "平民" && gaveJinBaoBao == 0 && session.doubleIdentities) {
                    rs = LinkedList(listOf("平民", "平民"))
                    roles.remove("平民")
                    gaveJinBaoBao++
                    isJinBaoBao = true
                } else if (session.doubleIdentities) {
                    var shouldRemove = true
                    rs.add(roles.first())
                    if (rs.contains("複製人")) {
                        player.duplicated = true
                        if (rs.first() == "複製人") {
                            rs[0] = rs[1]
                        } else {
                            rs[1] = rs.first()
                        }
                    }
                    if (rs.first() == "平民" && rs[1] == "平民") {
                        if (gaveJinBaoBao >= 2) {
                            for (r in ArrayList(roles)) {
                                if (r != "平民") {
                                    rs[1] = r
                                    roles.remove(r)
                                    shouldRemove = false
                                    break
                                }
                            }
                        }
                        if (rs.first() == "平民" && rs[1] == "平民") {
                            isJinBaoBao = true
                            gaveJinBaoBao++
                        }
                    }
                    if (rs.first().contains("狼")) {
                        rs.reverse()
                    }
                    if (shouldRemove)
                        roles.removeFirst()
                }

                player.jinBaoBao = isJinBaoBao && session.doubleIdentities
                player.roles = rs
                player.deadRoles = ArrayList()
                player.userId = member.idLong

                // 3. Prepare Nickname Task
                val newNickname = player.nickname
                if (member.effectiveName != newNickname) {
                    tasks.add(
                        ActionTask(
                            member.modifyNickname(newNickname),
                            "已更新暱稱: $newNickname"
                        )
                    )
                }

                statusLogger("  - 已分配身分: " + rs.joinToString(", ") + if (player.jinBaoBao) " (金寶寶)" else "")

                // 4. Send Channel Message
                val playerChannel = guild.getTextChannelById(player.channelId)
                if (playerChannel != null) {
                    val embed = EmbedBuilder()
                        .setTitle("你抽到的身分是 (若為狼人或金寶寶請使用自己的頻道來和隊友討論及確認身分)")
                        .setDescription(
                            rs.joinToString("、") + (if (player.jinBaoBao) " (金寶寶)" else "") +
                                    if (player.duplicated) " (複製人)" else ""
                        )
                        .setColor(MsgUtils.randomColor)

                    val action = playerChannel.sendMessageEmbeds(embed.build())

                    if (session.doubleIdentities) {
                        action.setComponents(
                            ActionRow.of(
                                Button.primary(
                                    "changeRoleOrder",
                                    "更換身分順序 (請在收到身分後全分聽完再使用，逾時不候)"
                                )
                            )
                        )
                        CmdUtils.schedule({
                            val innerSession = sessionRepository.findByGuildId(guildId).orElse(null)
                            if (innerSession != null) {
                                val innerPlayer = innerSession.players[player.id.toString()]
                                if (innerPlayer != null) {
                                    innerPlayer.rolePositionLocked = true
                                    sessionRepository.save(innerSession)
                                }
                            }
                            val ch = jda.getTextChannelById(player.channelId)
                            ch?.sendMessage("身分順序已鎖定")?.queue()
                        }, 120000)
                    }
                    tasks.add(
                        ActionTask(
                            action,
                            "已發送私密頻道訊息予 " + member.effectiveName
                        )
                    )
                }
            }

            // 5. Send Summary to Judge and Spectator Channels
            val dashboardUrl = System.getenv().getOrDefault(
                "DASHBOARD_URL",
                "http://localhost:5173"
            ) + "/server/" + guildId
            val summaryEmbed = EmbedBuilder()
                .setTitle("身分列表")
                .setColor(MsgUtils.randomColor)

            val sortedPlayers = ArrayList(session.players.values)
            sortedPlayers.sortBy { it.id }

            for (p in sortedPlayers) {
                val rolesStr = ((p.roles?.joinToString("、") ?: "無") +
                        (if (p.police) " (警長)" else "") +
                        if (p.jinBaoBao) " (金寶寶)" else if (p.duplicated) " (複製人)" else "")
                summaryEmbed.addField(p.nickname, rolesStr, true)
            }

            val notificationMsg = "遊戲控制台: $dashboardUrl"

            // Add notification tasks
            val notifyChannelIds = longArrayOf(session.judgeTextChannelId, session.spectatorTextChannelId)
            for (channelId in notifyChannelIds) {
                if (channelId != 0L) {
                    val channel = guild.getTextChannelById(channelId)
                    if (channel != null) {
                        tasks.add(
                            ActionTask(
                                channel.sendMessage(notificationMsg).setEmbeds(summaryEmbed.build()),
                                "已發送身分列表與控制台連結至頻道: " + channel.name
                            )
                        )
                    }
                }
            }

            if (tasks.isNotEmpty()) {
                statusLogger("正在執行 Discord 變更 (共 " + tasks.size + " 項)...")
                tasks.runActions(statusLogger, progressCallback, 10, 95, 60)
            } else {
                statusLogger("沒有偵測到需要執行的 Discord 變更。")
                progressCallback(95)
            }

            // Final Update session flags and logs
            session.hasAssignedRoles = true
            session.addLog(LogType.ROLE_ASSIGNED, "身分分配完成", null)
            sessionRepository.save(session)

            progressCallback(100)
            statusLogger("身分分配完成！")

            gameSessionService.broadcastUpdate(guildId)
        } catch (e: Exception) {
            log.error("Failed to assign roles: {}", e.message, e)
            throw RuntimeException("Failed to assign roles", e)
        }
    }
}
