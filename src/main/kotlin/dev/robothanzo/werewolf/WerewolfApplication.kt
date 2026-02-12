package dev.robothanzo.werewolf

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import dev.robothanzo.werewolf.database.ReplayRepository
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.service.*
import jakarta.annotation.PostConstruct
import net.dv8tion.jda.api.JDA
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile

@SpringBootApplication
@EnableScheduling
class WerewolfApplication {
    companion object {
        const val AUTHOR = 466769036122783744L
        val SERVER_CREATORS = listOf(466769036122783744L, 616590798989033502L, 451672040227864587L)
        val ROLES = listOf(
            "狼人",
            "女巫",
            "獵人",
            "預言家",
            "平民",
            "狼王",
            "狼美人",
            "白狼王",
            "夢魘",
            "混血兒",
            "守衛",
            "騎士",
            "白癡",
            "守墓人",
            "魔術師",
            "黑市商人",
            "邱比特",
            "盜賊",
            "石像鬼",
            "狼兄",
            "狼弟",
            "複製人",
            "血月使者",
            "惡靈騎士",
            "通靈師",
            "機械狼",
            "獵魔人"
        )

        // Static bridges
        lateinit var jda: JDA
        lateinit var gameSessionService: GameSessionService
        lateinit var gameStateService: GameStateService
        lateinit var roleService: RoleService
        lateinit var gameActionService: GameActionService
        lateinit var policeService: PoliceService
        lateinit var expelService: ExpelService
        lateinit var playerService: PlayerService
        lateinit var speechService: SpeechService
        lateinit var actionUIService: ActionUIService
        lateinit var replayRepository: ReplayRepository
        lateinit var roleActionExecutor: RoleActionExecutor
        lateinit var roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry
        lateinit var roleEventService: RoleEventService
        lateinit var nightManager: NightManager
        val playerManager: AudioPlayerManager = DefaultAudioPlayerManager()

        @JvmStatic
        fun main(args: Array<String>) {
            extractSoundFiles()
            SpringApplication.run(WerewolfApplication::class.java, *args)
        }

        private fun extractSoundFiles() {
            try {
                val sourceFile = File(WerewolfApplication::class.java.protectionDomain.codeSource.location.toURI())
                if (!sourceFile.isFile || !sourceFile.name.endsWith(".jar", ignoreCase = true)) {
                    return
                }

                val jarFile = JarFile(sourceFile)
                val soundFolder = File("sounds")
                if (!soundFolder.exists()) {
                    if (!soundFolder.mkdir()) {
                        LoggerFactory.getLogger(WerewolfApplication::class.java)
                            .error("Failed to create sounds directory")
                        return
                    }
                }

                val entries = jarFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val s = entry.name
                    if (s.startsWith("sounds/") && s.endsWith(".mp3")) {
                        val outputFile = File(soundFolder, s.split("/").last())
                        // Only extract if file doesn't exist or we want to force refresh
                        if (!outputFile.exists()) {
                            jarFile.getInputStream(entry).use { input ->
                                FileOutputStream(outputFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
                jarFile.close()
            } catch (e: Exception) {
                LoggerFactory.getLogger(WerewolfApplication::class.java)
                    .warn("Failed to extract sound files (ignore if running in IDE): {}", e.message)
            }
        }
    }

    @Component
    class StaticBridge(
        private val gameSessionServiceBean: GameSessionService,
        private val discordServiceBean: DiscordService,
        private val roleServiceBean: RoleService,
        private val gameActionServiceBean: GameActionService,
        private val policeServiceBean: PoliceService,
        private val expelServiceBean: ExpelService,
        private val playerServiceBean: PlayerService,
        private val speechServiceBean: SpeechService,
        private val actionUIServiceBean: ActionUIService,
        private val replayRepositoryBean: ReplayRepository,
        private val roleActionExecutorBean: RoleActionExecutor,
        private val roleRegistryBean: RoleRegistry,
        private val roleEventServiceBean: RoleEventService,
        private val gameStateServiceBean: GameStateService,
        private val nightManagerBean: NightManager
    ) {
        private val log = LoggerFactory.getLogger(StaticBridge::class.java)

        @PostConstruct
        fun init() {
            log.info("Initializing Static Bridge...")
            gameSessionService = gameSessionServiceBean
            roleService = roleServiceBean
            roleService = roleServiceBean
            gameActionService = gameActionServiceBean
            policeService = policeServiceBean
            expelService = expelServiceBean
            playerService = playerServiceBean
            speechService = speechServiceBean
            actionUIService = actionUIServiceBean
            roleActionExecutor = roleActionExecutorBean
            roleRegistry = roleRegistryBean
            roleEventService = roleEventServiceBean
            gameStateService = gameStateServiceBean
            nightManager = nightManagerBean
            replayRepository = replayRepositoryBean
            jda = discordServiceBean.jda

            AudioSourceManagers.registerRemoteSources(playerManager)
            AudioSourceManagers.registerLocalSource(playerManager)
        }
    }
}
