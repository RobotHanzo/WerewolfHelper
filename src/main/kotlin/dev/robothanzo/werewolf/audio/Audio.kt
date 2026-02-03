package dev.robothanzo.werewolf.audio

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.robothanzo.werewolf.WerewolfApplication
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.slf4j.LoggerFactory
import java.util.*

object Audio {
    private val log = LoggerFactory.getLogger(Audio::class.java)

    fun VoiceChannel.play(resource: Resource) {
        try {
            val audioManager = this.guild.audioManager
            val player = WerewolfApplication.playerManager.createPlayer()
            if (audioManager.sendingHandler == null) {
                audioManager.sendingHandler = AudioPlayerSendHandler(player)
            }

            audioManager.openAudioConnection(this)

            WerewolfApplication.playerManager.loadItem(
                "sounds/$resource.mp3",
                object : AudioLoadResultHandler {
                    override fun trackLoaded(track: AudioTrack) {
                        player.startTrack(track, false)
                    }

                    override fun playlistLoaded(playlist: AudioPlaylist) {}
                    override fun noMatches() {}
                    override fun loadFailed(exception: FriendlyException) {
                        log.error("Error while trying to load audio resource", exception)
                    }
                })
        } catch (e: Exception) {
            log.error("Error while trying to play sound resource", e)
        }
    }

    enum class Resource {
        EXPEL_POLL, POLICE_ENROLL, POLICE_POLL, TIMER_ENDED, ENROLL_10S_REMAINING, POLL_10S_REMAINING, TIMER_30S_REMAINING;

        override fun toString(): String {
            return super.toString().lowercase(Locale.ROOT)
        }
    }
}
