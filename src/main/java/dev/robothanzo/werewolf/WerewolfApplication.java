package dev.robothanzo.werewolf;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.robothanzo.werewolf.service.DiscordService;
import dev.robothanzo.werewolf.service.GameSessionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class WerewolfApplication {

    public static final long AUTHOR = 466769036122783744L;
    public static final List<Long> SERVER_CREATORS = List.of(466769036122783744L, 616590798989033502L,
            451672040227864587L);
    public static final List<String> ROLES = List.of(
            "狼人", "女巫", "獵人", "預言家", "平民", "狼王", "狼美人", "白狼王", "夢魘", "混血兒", "守衛", "騎士", "白癡",
            "守墓人", "魔術師", "黑市商人", "邱比特", "盜賊", "石像鬼", "狼兄", "狼弟", "複製人", "血月使者", "惡靈騎士", "通靈師", "機械狼", "獵魔人");

    // Static bridges for legacy code
    public static JDA jda;
    public static GameSessionService gameSessionService;
    public static dev.robothanzo.werewolf.service.RoleService roleService;
    public static dev.robothanzo.werewolf.service.GameActionService gameActionService;
    public static dev.robothanzo.werewolf.service.PoliceService policeService;
    public static dev.robothanzo.werewolf.service.PlayerService playerService;
    public static dev.robothanzo.werewolf.service.SpeechService speechService;
    public static AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

    static void main(String[] args) {
        // Extract sounds before Spring starts
        extractSoundFiles();
        SpringApplication.run(WerewolfApplication.class, args);
    }

    // Component to populate static fields from Spring Context
    @Component
    @RequiredArgsConstructor
    static class StaticBridge {
        private final GameSessionService gameSessionServiceBean;
        private final DiscordService discordServiceBean;
        private final dev.robothanzo.werewolf.service.RoleService roleServiceBean;
        private final dev.robothanzo.werewolf.service.GameActionService gameActionServiceBean;
        private final dev.robothanzo.werewolf.service.PoliceService policeServiceBean;
        private final dev.robothanzo.werewolf.service.PlayerService playerServiceBean;
        private final dev.robothanzo.werewolf.service.SpeechService speechServiceBean;

        @PostConstruct
        public void init() {
            log.info("Initializing Static Bridge...");
            dev.robothanzo.werewolf.database.Database.initDatabase(); // Initialize legacy DB
            WerewolfApplication.gameSessionService = gameSessionServiceBean;
            WerewolfApplication.roleService = roleServiceBean;
            WerewolfApplication.gameActionService = gameActionServiceBean;
            WerewolfApplication.policeService = policeServiceBean;
            WerewolfApplication.playerService = playerServiceBean;
            WerewolfApplication.speechService = speechServiceBean;
            WerewolfApplication.jda = discordServiceBean.getJDA();

            // AudioPlayerManager setup if needed
            AudioSourceManagers.registerRemoteSources(playerManager);
            AudioSourceManagers.registerLocalSource(playerManager);
        }
    }

    public static void extractSoundFiles() {
        try {
            JarFile jarFile = new JarFile(
                    new File(WerewolfApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
            File soundFolder = new File("sounds");
            if (!soundFolder.exists()) {
                soundFolder.mkdir();
            }
            // Logic to clean and extract (simplified from original to avoid full deletion
            // risk if not intent)
            // Original code deleted file if it was a file named "soundFolder".

            for (Enumeration<JarEntry> em = jarFile.entries(); em.hasMoreElements(); ) {
                String s = em.nextElement().toString();

                if (s.startsWith(("sounds/")) && s.endsWith(".mp3")) {
                    ZipEntry entry = jarFile.getEntry(s);
                    File outputFile = new File(soundFolder, s.split("/")[s.split("/").length - 1]);
                    // Only write if doesn't exist or explicit overwrite logic?
                    // Legacy code overwrote.
                    InputStream inStream = jarFile.getInputStream(entry);
                    OutputStream out = new FileOutputStream(outputFile);
                    int c;
                    while ((c = inStream.read()) != -1) {
                        out.write(c);
                    }
                    inStream.close();
                    out.close();
                }
            }
            jarFile.close();
        } catch (Exception e) {
            log.warn("Failed to extract sound files (ignore if running in IDE): {}", e.getMessage());
        }
    }
}
