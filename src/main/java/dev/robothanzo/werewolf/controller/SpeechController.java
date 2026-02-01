package dev.robothanzo.werewolf.controller;

import dev.robothanzo.werewolf.WerewolfApplication;
import dev.robothanzo.werewolf.database.documents.Session;
import dev.robothanzo.werewolf.security.annotations.CanManageGuild;
import dev.robothanzo.werewolf.service.DiscordService;
import dev.robothanzo.werewolf.service.GameSessionService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/sessions/{guildId}/speech")
@RequiredArgsConstructor
public class SpeechController {

    private final GameSessionService gameSessionService;
    private final DiscordService discordService;
    private final dev.robothanzo.werewolf.service.SpeechService speechService;

    @PostMapping("/auto")
    @CanManageGuild
    public ResponseEntity<?> startAutoSpeech(@PathVariable long guildId) {
        Optional<Session> sessionOpt = gameSessionService.getSession(guildId);
        if (sessionOpt.isEmpty())
            return ResponseEntity.notFound().build();
        Session session = sessionOpt.get();

        Guild guild = discordService.getGuild(guildId);
        TextChannel channel = guild.getTextChannelById(session.getCourtTextChannelId());

        if (channel != null) {
            speechService.startAutoSpeechFlow(guildId, channel.getIdLong());
        }
        gameSessionService.broadcastUpdate(guildId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/skip")
    @CanManageGuild
    public ResponseEntity<?> skipSpeech(@PathVariable long guildId) {
        speechService.skipToNext(guildId);
        gameSessionService.broadcastUpdate(guildId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/interrupt")
    @CanManageGuild
    public ResponseEntity<?> interruptSpeech(@PathVariable long guildId) {
        speechService.interruptSession(guildId);
        gameSessionService.broadcastUpdate(guildId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/police-enroll")
    @CanManageGuild
    public ResponseEntity<?> startPoliceEnroll(@PathVariable long guildId) {
        Optional<Session> sessionOpt = gameSessionService.getSession(guildId);
        if (sessionOpt.isEmpty())
            return ResponseEntity.notFound().build();
        Session session = sessionOpt.get();

        Guild guild = discordService.getGuild(guildId);
        TextChannel channel = guild.getTextChannelById(session.getCourtTextChannelId());

        WerewolfApplication.policeService.startEnrollment(session, channel, null);
        gameSessionService.broadcastUpdate(guildId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/order")
    @CanManageGuild
    public ResponseEntity<?> setSpeechOrder(@PathVariable long guildId, @RequestBody Map<String, String> body) {
        String direction = body.get("direction");
        dev.robothanzo.werewolf.model.SpeechOrder order = dev.robothanzo.werewolf.model.SpeechOrder
                .valueOf(direction.toUpperCase());

        speechService.setSpeechOrder(guildId, order);
        speechService.confirmSpeechOrder(guildId);

        gameSessionService.broadcastUpdate(guildId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/confirm")
    @CanManageGuild
    public ResponseEntity<?> confirmSpeech(@PathVariable long guildId) {
        speechService.confirmSpeechOrder(guildId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/manual-start")
    @CanManageGuild
    public ResponseEntity<?> manualStartTimer(@PathVariable long guildId, @RequestBody Map<String, Integer> body) {
        int duration = body.get("duration");

        Optional<Session> sessionOpt = gameSessionService.getSession(guildId);
        if (sessionOpt.isEmpty())
            return ResponseEntity.notFound().build();
        Session session = sessionOpt.get();

        Guild guild = discordService.getGuild(guildId);
        TextChannel channel = guild.getTextChannelById(session.getCourtTextChannelId());
        var voiceChannel = guild.getVoiceChannelById(session.getCourtVoiceChannelId());

        if (channel != null) {
            speechService.startTimer(guildId, channel.getIdLong(), voiceChannel != null ? voiceChannel.getIdLong() : 0,
                    duration);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/mute-all")
    @CanManageGuild
    public ResponseEntity<?> muteAll(@PathVariable long guildId) {
        speechService.setAllMute(guildId, true);
        gameSessionService.broadcastUpdate(guildId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/unmute-all")
    @CanManageGuild
    public ResponseEntity<?> unmuteAll(@PathVariable long guildId) {
        speechService.setAllMute(guildId, false);
        gameSessionService.broadcastUpdate(guildId);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
