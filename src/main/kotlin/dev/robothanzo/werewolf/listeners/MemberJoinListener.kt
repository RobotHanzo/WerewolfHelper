package dev.robothanzo.werewolf.listeners

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.SessionRepository
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MemberJoinListener(
    private val sessionRepository: SessionRepository
) : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(MemberJoinListener::class.java)

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val session = sessionRepository.findByGuildId(event.guild.idLong).orElse(null)
        if (WerewolfApplication.SERVER_CREATORS.contains(event.member.idLong)) {
            if (session != null && session.owner == event.user.idLong) {
                val judgeRole = session.judgeRole
                if (judgeRole != null) {
                    event.guild.addRoleToMember(event.member, judgeRole).queue()
                }
            }
        }
        if (session != null && session.hasAssignedRoles) {
            session.spectatorRole?.let { event.guild.addRoleToMember(event.member, it) }?.queue()
        }
    }
}
