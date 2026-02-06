package dev.robothanzo.werewolf.utils

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
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
        val session = WerewolfApplication.gameSessionService.getSession(guild.idLong).orElse(null)
        return session
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

    fun schedule(runnable: () -> Unit, delay: Long): TimerTask {
        val task = object : TimerTask() {
            override fun run() {
                runnable()
            }
        }
        timer.schedule(task, delay)
        return task
    }
}

fun Member.isAdmin(): Boolean {
    return this.hasPermission(Permission.ADMINISTRATOR)
}

fun Member.isSpectator(strict: Boolean = false): Boolean {
    val session = CmdUtils.getSession(this.guild) ?: return true
    return (!strict && this.roles.isEmpty()) || this.roles.contains(session.spectatorRole)
}

fun Member.player(aliveOnly: Boolean = true): Player? {
    val p = CmdUtils.getSession(this.guild)?.getPlayer(this.idLong)
    return if (!aliveOnly || p?.alive == true) p else null
}
