package dev.robothanzo.werewolf.model;

import dev.robothanzo.werewolf.database.documents.Session;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

@Data
@Builder
public class SpeechSession {
    private long guildId;
    private long channelId;
    private Session session;
    @Builder.Default
    private List<Long> interruptVotes = new LinkedList<>();
    @Builder.Default
    private List<Session.Player> order = new LinkedList<>();
    @Nullable
    private Thread speakingThread;
    @Nullable
    private Long lastSpeaker;
    @Nullable
    private Runnable finishedCallback;
    @Builder.Default
    private long currentSpeechEndTime = 0;
    @Builder.Default
    private int totalSpeechTime = 0;
}
