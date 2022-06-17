package dev.robothanzo.werewolf.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.robothanzo.werewolf.WerewolfHelper;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.Locale;

@Slf4j
public class Audio {
    public static void play(Resource resource, AudioChannel channel) {
        try {
            AudioManager audioManager = channel.getGuild().getAudioManager();
            audioManager.openAudioConnection(channel);
            AudioPlayer player = WerewolfHelper.playerManager.createPlayer();
            audioManager.setSendingHandler(new AudioPlayerSendHandler(player));
            WerewolfHelper.playerManager.loadItem("sounds/" + resource + ".mp3", new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    player.startTrack(track, false);
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                }

                @Override
                public void noMatches() {
                }

                @Override
                public void loadFailed(FriendlyException exception) {
                    log.error("Error while trying to load audio resource", exception);
                }
            });
        } catch (Exception e) {
            log.error("Error while trying to play sound resource", e);
        }
    }

    public enum Resource {
        EXPEL_POLL, POLICE_ENROLL, POLICE_POLL, TIMER_ENDED, ENROLL_10S_REMAINING, POLL_10S_REMAINING, TIMER_30S_REMAINING;

        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }
    }
}
