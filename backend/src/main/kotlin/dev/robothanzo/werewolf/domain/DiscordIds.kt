package dev.robothanzo.werewolf.domain

/**
 * The Discord role/channel ids created during provisioning (FEATURES §3), persisted on the session
 * so the gateway can resolve "which channel/role belongs to this game" after a restart. 0 means
 * not yet provisioned.
 */
data class DiscordIds(
    var judgeRoleId: Long = 0,
    var spectatorRoleId: Long = 0,
    var courtTextChannelId: Long = 0,
    var courtVoiceChannelId: Long = 0,
    var judgeTextChannelId: Long = 0,
    var spectatorTextChannelId: Long = 0,
)
