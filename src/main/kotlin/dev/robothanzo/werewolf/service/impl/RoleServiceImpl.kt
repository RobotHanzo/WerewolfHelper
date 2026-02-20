package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.RoleService
import dev.robothanzo.werewolf.utils.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class RoleServiceImpl(
    private val gameSessionService: GameSessionService
) : RoleService {
    private val log = LoggerFactory.getLogger(RoleServiceImpl::class.java)

    override fun addRole(session: Session, roleName: String, amount: Int) {
        try {
            val roles = session.roles.toMutableList()
            repeat(amount) { roles.add(roleName) }
            session.roles = roles
            gameSessionService.saveSession(session)
        } catch (e: Exception) {
            log.error("Failed to add role: {}", e.message, e)
            throw RuntimeException("Failed to add role", e)
        }
    }

    override fun removeRole(session: Session, roleName: String, amount: Int) {
        try {
            val roles = session.roles.toMutableList()
            repeat(amount) { roles.remove(roleName) }
            session.roles = roles
            gameSessionService.saveSession(session)
        } catch (e: Exception) {
            log.error("Failed to remove role: {}", e.message, e)
            throw RuntimeException("Failed to remove role", e)
        }
    }

    override fun assignRoles(session: Session, statusCallback: (String) -> Unit, progressCallback: (Int) -> Unit) {
        try {
            val guildId = session.guildId
            if (session.hasAssignedRoles) {
                throw Exception("身分已分配，重新分配前請先重置遊戲。")
            }

            // Ensure player.session references are populated
            session.populatePlayerSessions()

            val totalPlayers = session.players.size
            log.info("Starting role assignment for guild {} with {} players", guildId, totalPlayers)

            progressCallback(0)
            statusCallback("正在掃描伺服器玩家...")

            val guild = session.guild ?: throw Exception("Guild not found")

            val pending = guild.members.filter { member ->
                !member.user.isBot &&
                        !member.isOwner &&
                        !member.isAdmin() &&
                        !member.isSpectator(strict = true)
            }.toMutableList()

            progressCallback(10)
            statusCallback("正在驗證玩家與身分數量...")

            pending.shuffle(Random())
            if (pending.size != session.players.size) {
                throw Exception(
                    "玩家數量不符合設定值。請確認是否已給予旁觀者應有之身分(使用 `/player died`)，或檢查 `/server set players` 設定的人數。\n(待分配: ${pending.size}, 需要: ${session.players.size})"
                )
            }

            val rolesPerPlayer = if (session.doubleIdentities) 2 else 1
            if (pending.size != (session.roles.size / rolesPerPlayer)) {
                throw Exception(
                    "玩家身分數量不符合身分清單數量。請確認是否正確啟用雙身分模式，並檢查 `/server roles list`。\n(目前玩家: ${pending.size}, 身分總數: ${session.roles.size})"
                )
            }

            val roles = session.roles.shuffled().toMutableList()

            var gaveJinBaoBao = 0
            statusCallback("正在分配身分並更新伺服器狀態...")

            val playersList = ArrayList(session.players.values)
            playersList.sortBy { it.id }

            val priorityTasks: MutableList<ActionTask> = ArrayList()
            val notificationTasks: MutableList<ActionTask> = ArrayList()

            for (player in playersList) {
                val member = pending[player.id - 1]
                player.updateUserId(member.idLong)

                var rs = mutableListOf(roles.removeFirst())
                var isJinBaoBao = false

                if (rs.first() == "白癡") {
                    player.idiot = true
                }

                if (rs.first() == "平民" && gaveJinBaoBao == 0 && session.doubleIdentities) {
                    rs = mutableListOf("平民", "平民")
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
                player.deadRoles = mutableListOf()

                val newNickname = player.nickname
                if (member.effectiveName != newNickname) {
                    if (guild.selfMember.canInteract(member)) {
                        priorityTasks.add(
                            ActionTask(
                                member.modifyNickname(newNickname),
                                "已更新暱稱: $newNickname"
                            )
                        )
                    } else {
                        statusCallback("  - [警告] 無法更新 ${member.effectiveName} 的暱稱 (權限不足)")
                    }
                }

                player.role?.let {
                    priorityTasks.add(
                        ActionTask(
                            guild.modifyMemberRoles(
                                member,
                                it
                            ), // Discord wants us to use this or there will be race conditions: https://github.com/discord/discord-api-docs/issues/6289
                            "已套用身分組: " + it.name + " 給 " + member.effectiveName
                        )
                    )
                }

                statusCallback("  - 已分配身分: " + rs.joinToString(", ") + if (player.jinBaoBao) " (金寶寶)" else "")

                // 4. Send Channel Message
                val embed = EmbedBuilder()
                    .setTitle("你抽到的身分是 (若為狼人或金寶寶請使用自己的頻道來和隊友討論及確認身分)")
                    .setDescription(
                        rs.joinToString("、") + (if (player.jinBaoBao) " (金寶寶)" else "") +
                                if (player.duplicated) " (複製人)" else ""
                    )
                    .setColor(MsgUtils.randomColor)

                val action = player.channel?.sendMessageEmbeds(embed.build())

                if (action != null) {
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
                            gameSessionService.withLockedSession(guildId) {
                                val innerPlayer = it.getPlayer(player.id)
                                if (innerPlayer != null) {
                                    innerPlayer.rolePositionLocked = true
                                    innerPlayer.channel?.sendMessage("身分順序已鎖定")?.queue()
                                }
                            }
                        }, 120000)
                    }
                    notificationTasks.add(
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

            val sortedPlayers = session.players.values.sortedBy { it.id }

            for (p in sortedPlayers) {
                val rolesStr = ((p.roles.joinToString("、") ?: "無") +
                        (if (p.police) " (警長)" else "") +
                        if (p.jinBaoBao) " (金寶寶)" else if (p.duplicated) " (複製人)" else "")
                summaryEmbed.addField(p.nickname, rolesStr, true)
            }

            val notificationMsg = "遊戲控制台: $dashboardUrl"

            // Add notification tasks
            val judgeAction = session.judgeTextChannel?.sendMessage(notificationMsg)?.setEmbeds(summaryEmbed.build())
            if (judgeAction != null) {
                notificationTasks.add(
                    ActionTask(
                        judgeAction,
                        "已發送身分列表與控制台連結至頻道: 法官"
                    )
                )
            }

            val spectatorAction =
                session.spectatorTextChannel?.sendMessage(notificationMsg)?.setEmbeds(summaryEmbed.build())
            if (spectatorAction != null) {
                notificationTasks.add(
                    ActionTask(
                        spectatorAction,
                        "已發送身分列表與控制台連結至頻道: 旁觀"
                    )
                )
            }

            // Notify Wolf Brother and Younger Brother of each other if both exist
            val wolfBrother = playersList.find { p -> p.roles.any { it == "狼兄" } }
            val youngerBrother = playersList.find { p -> p.roles.any { it == "狼弟" } }
            if (wolfBrother != null && youngerBrother != null) {
                statusCallback("正在通知狼兄與狼弟彼此身份...")

                val wbMsg = "你的狼弟是 ${youngerBrother.nickname} (玩家 #${youngerBrother.id})"
                val ybMsg = "你的狼兄是 ${wolfBrother.nickname} (玩家 #${wolfBrother.id})"

                val wbAction = wolfBrother.channel?.sendMessage(wbMsg)
                if (wbAction != null) {
                    notificationTasks.add(
                        ActionTask(
                            wbAction,
                            "已通知狼兄 ${wolfBrother.nickname} 其狼弟身份"
                        )
                    )
                }

                val ybAction = youngerBrother.channel?.sendMessage(ybMsg)
                if (ybAction != null) {
                    notificationTasks.add(
                        ActionTask(
                            ybAction,
                            "已通知狼弟 ${youngerBrother.nickname} 其狼兄身份"
                        )
                    )
                }
            }

            if (priorityTasks.isNotEmpty()) {
                statusCallback("正在執行 Discord 變更: 身分與暱稱 (共 " + priorityTasks.size + " 項)...")
                priorityTasks.runActions(statusCallback, progressCallback, 10, 60, 60)
            }

            if (notificationTasks.isNotEmpty()) {
                statusCallback("正在執行 Discord 變更: 發送通知 (共 " + notificationTasks.size + " 項)...")
                notificationTasks.runActions(statusCallback, progressCallback, 60, 95, 120)
            } else {
                statusCallback("沒有偵測到需要執行的通知任務。")
                progressCallback(95)
            }

            // Final Update session flags and logs
            session.hasAssignedRoles = true
            session.addLog(LogType.ROLE_ASSIGNED, "身分分配完成", null)
            gameSessionService.saveSession(session)

            progressCallback(100)
            statusCallback("身分分配完成！")
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            statusCallback("錯誤: $errorMessage")
            log.error("Failed to assign roles: {}", errorMessage, e)
            throw RuntimeException(errorMessage, e)
        }
    }
}
