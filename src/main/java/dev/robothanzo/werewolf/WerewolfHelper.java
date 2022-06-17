package dev.robothanzo.werewolf;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.robothanzo.jda.interactions.JDAInteractions;
import dev.robothanzo.werewolf.database.Database;
import dev.robothanzo.werewolf.listeners.ButtonListener;
import dev.robothanzo.werewolf.listeners.GuildJoinListener;
import dev.robothanzo.werewolf.listeners.MemberJoinListener;
import dev.robothanzo.werewolf.listeners.MessageListener;
import io.github.cdimascio.dotenv.Dotenv;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
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

public class WerewolfHelper {
    public static final long AUTHOR = 466769036122783744L;
    public static final List<String> ROLES = List.of(
            "狼人", "女巫", "獵人", "預言家", "平民", "狼王", "狼美人", "白狼王", "夢魘", "混血兒", "守衛", "騎士", "白癡",
            "守墓人", "魔術師", "黑市商人", "邱比特", "盜賊", "石像鬼", "狼兄", "複製人"
    );
    public static JDA jda;
    public static Dotenv dotenv = Dotenv.load();
    public static AudioPlayerManager playerManager = new DefaultAudioPlayerManager();

    @SneakyThrows
    public static void main(String[] args) {
        extractSoundFiles();
        Database.initDatabase();
        AudioSourceManagers.registerLocalSource(playerManager);
        jda = JDABuilder.createDefault(dotenv.get("TOKEN"))
                .enableIntents(EnumSet.allOf(GatewayIntent.class))
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .enableCache(EnumSet.allOf(CacheFlag.class))
                .addEventListeners(new GuildJoinListener(), new MemberJoinListener(), new MessageListener(), new ButtonListener())
                .build();
        new JDAInteractions("dev.robothanzo.werewolf.commands").registerInteractions(jda).queue();
        jda.awaitReady();
        jda.getPresence().setActivity(Activity.competing("狼人殺 by Hanzo"));
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
