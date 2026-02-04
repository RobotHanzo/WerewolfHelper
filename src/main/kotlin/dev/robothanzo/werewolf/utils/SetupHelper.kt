package dev.robothanzo.werewolf.utils

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.commands.Server
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Icon
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.requests.RestAction
import org.slf4j.LoggerFactory
import java.awt.Color
import java.util.*
import java.util.concurrent.CompletableFuture

object SetupHelper {
    private val log = LoggerFactory.getLogger(SetupHelper::class.java)

    fun setup(guild: Guild, config: Server.PendingSetup) {
        log.info("Starting setup for guild {}", guild.id)
        guild.manager
            .setDefaultNotificationLevel(Guild.NotificationLevel.MENTIONS_ONLY)
            .setName("狼人殺伺服器")
            .setIcon(Icon.from(WerewolfApplication::class.java.classLoader.getResourceAsStream("wolf.png")!!)).queue()

        // Delete all existing channels before creating new ones
        cleanupChannels(guild)

        // Setup session data
        val session = createSession(guild, config)

        // 1. Create Judge Role first
        guild.createRole(
            name = "法官",
            color = Color.YELLOW,
            hoisted = true,
            permissions = listOf(Permission.ADMINISTRATOR)
        ).queue { judgeRole ->
            session.judgeRoleId = judgeRole.idLong

            // 2. Create Player Roles and Channels
            createPlayerRolesAndChannels(session, config.players).thenAccept { players ->
                session.players = players

                // 3. Create Spectator/Dead Role
                guild.createRole(
                    name = "旁觀者/死人",
                    color = Color(0x654321),
                    permissions = listOf(Permission.VIEW_CHANNEL)
                ).queue { deadRole ->
                    session.spectatorRoleId = deadRole.idLong
                    applySpectatorOverrides(guild, players, deadRole)

                    // 4. Create remaining core channels
                    createChannels(guild, deadRole, guild.publicRole, session).thenAccept {
                        finalizeSetup(guild, config, session)
                    }
                }
            }
        }
    }

    // --- Extension Functions for Cleaner Syntax ---

    private fun Guild.createRole(
        name: String,
        color: Color,
        hoisted: Boolean = false,
        permissions: Collection<Permission> = emptyList()
    ): RestAction<Role> {
        return this.createRole()
            .setName(name)
            .setColor(color)
            .setHoisted(hoisted)
            .setPermissions(permissions)
    }

    private fun Guild.createTextChannel(
        name: String,
        roleOverrides: Map<Role, Pair<Collection<Permission>, Collection<Permission>>>
    ): RestAction<TextChannel> {
        var action = this.createTextChannel(name)
        roleOverrides.forEach { (role, perms) ->
            action = action.addPermissionOverride(role, perms.first, perms.second)
        }
        return action
    }

    private fun Guild.createVoiceChannel(
        name: String,
        roleOverrides: Map<Role, Pair<Collection<Permission>, Collection<Permission>>>
    ): RestAction<VoiceChannel> {
        var action = this.createVoiceChannel(name)
        roleOverrides.forEach { (role, perms) ->
            action = action.addPermissionOverride(role, perms.first, perms.second)
        }
        return action
    }

    // --- Helper Logic ---

    private fun cleanupChannels(guild: Guild) {
        try {
            guild.channels.forEach { ch ->
                try {
                    ch.delete().queue()
                } catch (e: Exception) {
                    log.warn("Failed to delete channel {}: {}", ch.id, e.message)
                }
            }
        } catch (e: Exception) {
            log.warn("Error while deleting existing channels: {}", e.message)
        }
    }

    private fun createSession(guild: Guild, config: Server.PendingSetup): Session {
        val session = Session(guildId = guild.idLong)
        session.doubleIdentities = config.doubleIdentity
        session.owner = guild.ownerIdLong

        val roles = if (config.doubleIdentity) {
            listOf(
                "狼人", "狼人", "狼兄",
                "女巫", "獵人", "預言家", "守墓人", "騎士", "複製人",
                "平民", "平民", "平民", "平民", "平民", "平民", "平民"
            )
        } else {
            listOf(
                "狼人", "狼人", "狼人",
                "女巫", "獵人", "預言家",
                "平民", "平民", "平民"
            )
        }
        session.roles = LinkedList(roles)
        return session
    }

    private fun applySpectatorOverrides(guild: Guild, players: Map<String, Player>, deadRole: Role) {
        try {
            players.values.forEach { p ->
                val ch = guild.getTextChannelById(p.channelId)
                ch?.upsertPermissionOverride(deadRole)
                    ?.setPermissions(listOf(Permission.VIEW_CHANNEL), listOf(Permission.MESSAGE_SEND))
                    ?.queue(null) { }
            }
        } catch (e: Exception) {
            log.warn("Failed to add spectator overrides to player channels: {}", e.message)
        }
    }

    private fun finalizeSetup(guild: Guild, config: Server.PendingSetup, session: Session) {
        Session.fetchCollection().insertOne(session)
        log.info("Successfully registered guild as a session: {}", guild.id)

        val courtChannel = guild.getTextChannelById(session.courtTextChannelId)
        if (courtChannel != null) {
            session.sendToCourt("伺服器設定完成！請法官邀請玩家並開始遊戲。")
            try {
                courtChannel.createInvite().setMaxUses(0).setMaxAge(0).queue { invite ->
                    val origin = WerewolfApplication.jda!!.getTextChannelById(config.originChannelId)
                    origin?.sendMessage("伺服器已設定完成，點此連結前往伺服器： " + invite.url)?.queue()
                }
            } catch (e: Exception) {
                log.warn("Failed to create/send invite: {}", e.message)
            }
        } else {
            try {
                (guild.defaultChannel as? TextChannel)?.sendMessage("伺服器設定完成！請法官邀請玩家並開始遊戲。")?.queue()
            } catch (_: Exception) {
            }
        }
    }

    private fun createChannels(
        guild: Guild,
        deadRole: Role,
        publicRole: Role,
        session: Session
    ): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()

        // Court Text
        guild.createTextChannel(
            "法院",
            mapOf(
                publicRole to (emptyList<Permission>() to listOf(Permission.USE_APPLICATION_COMMANDS)),
                deadRole to (listOf(Permission.VIEW_CHANNEL) to listOf(
                    Permission.MESSAGE_SEND,
                    Permission.MESSAGE_ADD_REACTION
                ))
            )
        ).queue { courtText ->
            session.courtTextChannelId = courtText.idLong

            // Court Voice
            guild.createVoiceChannel(
                "法院",
                mapOf(
                    publicRole to (listOf(Permission.VOICE_SPEAK) to listOf(Permission.USE_EMBEDDED_ACTIVITIES)),
                    deadRole to (listOf(Permission.VIEW_CHANNEL) to listOf(Permission.VOICE_SPEAK))
                )
            ).queue { courtVoice ->
                session.courtVoiceChannelId = courtVoice.idLong

                // Spectator Text
                guild.createTextChannel(
                    "場外",
                    mapOf(
                        deadRole to (listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND) to emptyList()),
                        publicRole to (emptyList<Permission>() to listOf(
                            Permission.VIEW_CHANNEL,
                            Permission.USE_APPLICATION_COMMANDS
                        ))
                    )
                ).queue { specText ->
                    session.spectatorTextChannelId = specText.idLong

                    // Spectator Voice
                    guild.createVoiceChannel(
                        "場外",
                        mapOf(
                            deadRole to (listOf(Permission.VIEW_CHANNEL, Permission.VOICE_SPEAK) to emptyList()),
                            publicRole to (emptyList<Permission>() to listOf(Permission.VIEW_CHANNEL))
                        )
                    ).queue {
                        // Judge Text
                        guild.createTextChannel(
                            "法官",
                            mapOf(
                                deadRole to (listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND) to emptyList()),
                                publicRole to (emptyList<Permission>() to listOf(
                                    Permission.VIEW_CHANNEL,
                                    Permission.USE_APPLICATION_COMMANDS
                                ))
                            )
                        ).queue { judgeText ->
                            session.judgeTextChannelId = judgeText.idLong
                            future.complete(null)
                        }
                    }
                }
            }
        }
        return future
    }

    private fun createPlayerRolesAndChannels(session: Session, total: Int):
            CompletableFuture<MutableMap<String, Player>> {
        val future = CompletableFuture<MutableMap<String, Player>>()
        createPlayerRecursively(session, total, 1, mutableMapOf(), future)
        return future
    }

    private fun createPlayerRecursively(
        session: Session,
        total: Int,
        current: Int,
        players: MutableMap<String, Player>,
        future: CompletableFuture<MutableMap<String, Player>>
    ) {
        if (current > total) {
            future.complete(players)
            return
        }
        val id = Player.ID_FORMAT.format(current.toLong())
        val guild = session.guild ?: throw Exception("Guild not found")
        guild.createRole("玩家$id", MsgUtils.randomColor, true).queue { playerRole ->
            guild.createTextChannel(
                "玩家$id",
                mapOf(
                    playerRole to (listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND) to emptyList()),
                    guild.publicRole to (emptyList<Permission>() to listOf(
                        Permission.VIEW_CHANNEL,
                        Permission.MESSAGE_SEND,
                        Permission.USE_APPLICATION_COMMANDS
                    ))
                )
            ).queue { playerChannel ->
                players[current.toString()] = Player(
                    id = current,
                    roleId = playerRole.idLong,
                    channelId = playerChannel.idLong
                )
                createPlayerRecursively(session, total, current + 1, players, future)
            }
        }
    }
}
