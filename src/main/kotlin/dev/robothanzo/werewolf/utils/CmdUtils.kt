package dev.robothanzo.werewolf.utils

import com.mongodb.client.model.Filters.eq
import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import java.util.*

object CmdUtils {
    val timer: Timer = Timer()

    fun isAdmin(event: ButtonInteractionEvent): Boolean {
        if (event.guild == null || event.member?.permissions?.contains(Permission.ADMINISTRATOR) != true) {
            event.hook.editOriginal(":x: 你沒有管理員").queue()
            return false
        }
        return true
    }

    fun isAdmin(event: SlashCommandInteractionEvent): Boolean {
        if (event.guild == null || event.member?.permissions?.contains(Permission.ADMINISTRATOR) != true) {
            event.hook.editOriginal(":x: 你沒有管理員").queue()
            return false
        }
        return true
    }

    fun isAuthor(event: SlashCommandInteractionEvent): Boolean {
        if (event.user.idLong != WerewolfApplication.AUTHOR) {
            event.hook.editOriginal(":x:").queue()
            return false
        }
        return true
    }

    fun isServerCreator(event: SlashCommandInteractionEvent): Boolean {
        if (!WerewolfApplication.SERVER_CREATORS.contains(event.user.idLong)) {
            event.hook.editOriginal(":x:").queue()
            return false
        }
        return true
    }

    fun getSession(guild: Guild?): Session? {
        if (guild == null) return null
        return Session.fetchCollection().find(eq("guildId", guild.idLong)).first()
    }

    fun getSession(event: ButtonInteractionEvent): Session? {
        return getSession(event.guild)
    }

    fun getSession(event: SlashCommandInteractionEvent): Session? {
        if (event.guild == null) {
            event.hook.editOriginal(":x: 不在伺服器內").queue()
        }
        val session = getSession(event.guild)
        if (session == null) {
            event.hook.editOriginal(":x: 不在狼人殺伺服器內").queue()
            return null
        }
        return session
    }

    fun schedule(runnable: () -> Unit, delay: Long) {
        timer.schedule(object : TimerTask() {
            override fun run() {
                runnable()
            }
        }, delay)
    }
}
