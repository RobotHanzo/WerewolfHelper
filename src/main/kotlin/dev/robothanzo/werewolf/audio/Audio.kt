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
        val resourcePath = "sounds/$resource.mp3"
        log.info("Attempting to play audio resource: {} in channel: {}", resourcePath, this.name)
        try {
            val audioManager = this.guild.audioManager
            val player = WerewolfApplication.playerManager.createPlayer()

            // Always set/update the sending handler to the player we just created for this clip
            audioManager.sendingHandler = AudioPlayerSendHandler(player)

            if (!audioManager.isConnected) {
                audioManager.openAudioConnection(this)
            }

            WerewolfApplication.playerManager.loadItem(
                resourcePath,
                object : AudioLoadResultHandler {
                    override fun trackLoaded(track: AudioTrack) {
                        log.debug("Track loaded successfully: {}", resourceName(resource))
                        player.startTrack(track, false)
                    }

                    override fun playlistLoaded(playlist: AudioPlaylist) {
                        log.debug("Playlist loaded (unexpected for single clip): {}", resourceName(resource))
                    }

                    override fun noMatches() {
                        log.warn("No matches found for audio resource: {}", resourcePath)
                    }

                    override fun loadFailed(exception: FriendlyException) {
                        log.error("Load failed for audio resource: {}. Error: {}", resourcePath, exception.message)
                    }
                })
        } catch (e: Exception) {
            log.error("Error while trying to play sound resource: {}", resourcePath, e)
        }
    }

    private fun resourceName(resource: Resource): String = resource.toString()

    enum class Resource {
        EXPEL_POLL, POLICE_ENROLL, POLICE_POLL, TIMER_ENDED, ENROLL_10S_REMAINING, POLL_10S_REMAINING, TIMER_30S_REMAINING;

        override fun toString(): String {
            return super.toString().lowercase(Locale.ROOT)
        }
    }
}
