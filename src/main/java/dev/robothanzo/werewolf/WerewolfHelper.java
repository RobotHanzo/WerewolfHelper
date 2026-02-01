package dev.robothanzo.werewolf;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.robothanzo.jda.interactions.JDAInteractions;
import dev.robothanzo.werewolf.database.Database;
import dev.robothanzo.werewolf.server.WebServer;
import dev.robothanzo.werewolf.listeners.ButtonListener;
import dev.robothanzo.werewolf.listeners.GuildJoinListener;
import dev.robothanzo.werewolf.listeners.MemberJoinListener;
import dev.robothanzo.werewolf.listeners.MessageListener;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

@Slf4j
public class WerewolfHelper {
    public static final long AUTHOR = 466769036122783744L;
    public static final List<Long> SERVER_CREATORS = List.of(466769036122783744L, 616590798989033502L, 451672040227864587L);
    public static final List<String> ROLES = List.of(
            "狼人", "女巫", "獵人", "預言家", "平民", "狼王", "狼美人", "白狼王", "夢魘", "混血兒", "守衛", "騎士", "白癡",
            "守墓人", "魔術師", "黑市商人", "邱比特", "盜賊", "石像鬼", "狼兄", "狼弟", "複製人", "血月使者", "惡靈騎士", "通靈師", "機械狼", "獵魔人"
    );
    public static JDA jda;
    public static WebServer webServer;
    public static AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

    @SneakyThrows
    public static void main(String[] args) {
        extractSoundFiles();
        Database.initDatabase();
        AudioSourceManagers.registerLocalSource(playerManager);
        jda = JDABuilder.createDefault(System.getenv("TOKEN"))
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableCache(EnumSet.allOf(CacheFlag.class))
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS)
                .addEventListeners(new GuildJoinListener(), new MemberJoinListener(), new MessageListener(), new ButtonListener())
                .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
                .build();
        new JDAInteractions("dev.robothanzo.werewolf.commands").registerInteractions(jda).queue();
        jda.awaitReady();
        jda.getPresence().setActivity(Activity.competing("狼人殺 by Hanzo"));
        
        // Start web server in separate thread
        webServer = new WebServer(8080);
        webServer.setJDA(jda);
        Thread serverThread = new Thread(webServer);
        serverThread.setDaemon(true);
        serverThread.start();
        log.info("Dashboard web server started on port 8080");
//        new JDAInteractions("dev.robothanzo.werewolf.commands")
//                .registerInteractions(jda.getGuildById(dotenv.get("GUILD"))).queue();
    }

    @SneakyThrows
    public static void extractSoundFiles() {
        JarFile jarFile = new JarFile(new File(WerewolfHelper.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI()));
        File soundFolder = new File("sounds");
        if (!soundFolder.exists()) {
            soundFolder.mkdir();
        }
        if (soundFolder.isFile()) {
            soundFolder.delete();
            soundFolder.mkdir();
        }

        for (Enumeration<JarEntry> em = jarFile.entries(); em.hasMoreElements(); ) {
            String s = em.nextElement().toString();

            if (s.startsWith(("sounds/")) && s.endsWith(".mp3")) {
                ZipEntry entry = jarFile.getEntry(s);
                File outputFile = new File(soundFolder, s.split("/")[s.split("/").length - 1]);
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
    }
}
