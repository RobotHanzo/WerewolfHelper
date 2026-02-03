package dev.robothanzo.werewolf.listeners

import com.mongodb.client.model.Filters.eq
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.slf4j.LoggerFactory

class MemberJoinListener : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(MemberJoinListener::class.java)

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val session = Session.fetchCollection().find(eq("guildId", event.guild.idLong)).first()
        if (WerewolfApplication.SERVER_CREATORS.contains(event.member.idLong)) {
            if (session != null && session.owner == event.user.idLong) {
                val judgeRole = event.guild.getRoleById(session.judgeRoleId)
                if (judgeRole != null) {
                    event.guild.addRoleToMember(event.member, judgeRole).queue()
                }
            }
        }
        if (session != null && session.hasAssignedRoles) {
            val spectatorRole = event.guild.getRoleById(session.spectatorRoleId)
            if (spectatorRole != null) {
                event.guild.addRoleToMember(event.member, spectatorRole).queue()
            }
        }
    }
}
