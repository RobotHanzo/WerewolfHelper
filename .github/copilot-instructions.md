# WerewolfHelper AI Coding Agent Instructions

## Project Overview

Discord bot for hosting Werewolf (狼人殺) games with a real-time React dashboard. Spring Boot backend (Kotlin) +
TypeScript/React frontend + JDA (Discord API) + MongoDB + WebSocket sync.

## Architecture

### Backend (Spring Boot + Kotlin)

- **Main**: [WerewolfApplication.kt](src/main/kotlin/dev/robothanzo/werewolf/WerewolfApplication.kt) - Entry point, JDA
  setup, audio player initialization
- **State Machine**: Game steps implement `GameStep`
  interface ([game/GameStep.kt](src/main/kotlin/dev/robothanzo/werewolf/game/GameStep.kt)) - each step (Night, Speech,
  Voting, etc.) is a Spring `@Component` in `game/steps/`
- **Services**: Interface + implementation pattern (`service/` + `service/impl/`) - all implementations use `@Service`
  annotation
- **Controllers**: REST endpoints in `controller/` - use `@RestController` with custom security annotations
  `@CanViewGuild` and `@CanManageGuild`
- **Database**: MongoDB with Spring Data - documents in `database/documents/`, main entity is `Session` (per-guild game
  state)
- **Discord Integration**: `DiscordService` handles JDA operations, `Audio` class manages voice channel playback using
  LavaPlayer

### Frontend (React + TypeScript + Vite)

- **Location**: `src/dashboard/` - completely separate from backend
- **Dev Server**: `yarn dev` on port 5173, proxies `/api` and `/ws` to backend:8080 (
  see [vite.config.ts](src/dashboard/vite.config.ts))
- **WebSocket**: Real-time updates via `/ws` endpoint for live game state synchronization
- **Auth**: Discord OAuth2 flow - backend validates, dashboard receives session

## Critical Patterns

### Data Model Hierarchy

- **Session** (guild-scoped): Contains `players: Map<String, Player>`, `currentState`, `stateData`, game settings
- **Player** (nested class in Session): `roles: List<String>`, `isAlive`, `police`, identity flags (`jinBaoBao`,
  `duplicated`)
- **Game Steps**: State machine uses step IDs like "SETUP", "NIGHT", "SPEECH", "VOTING" - managed by `GameStateService`

### Security Model

- Custom annotations: `@CanManageGuild` and `@CanViewGuild` on controller methods
- Identity check: `IdentityUtils.canManage(guildId)` verifies Discord OAuth + guild permissions
- Session storage: MongoDB-backed HTTP sessions with 7-day timeout

### Testing Strategy

- JUnit 5 + Mockito Kotlin for unit tests in `src/test/kotlin/`
- Mock pattern: `@Mock` dependencies, inject into service implementation in `@BeforeEach`
-
Example: [GameSessionServiceImplTest.kt](src/test/kotlin/dev/robothanzo/werewolf/service/impl/GameSessionServiceImplTest.kt) -
mocks `SessionRepository`, `DiscordService`, `WebSocketHandler`
- Run tests: `./gradlew test` (generates HTML report in `build/reports/tests/test/`)

### Discord Audio Integration

- **Audio Manager**: `DefaultAudioPlayerManager` configured in `WerewolfApplication` with `AudioSourceManagers`
- **Sound Resources**: Stored in `src/main/resources/sounds/` - packaged into JAR, extracted at runtime to temp
  directory
- **Native Libraries**: JDA-NAS natives for different platforms included in dependencies (
  see [build.gradle.kts](build.gradle.kts))
- **JVM Args**: `--enable-native-access=ALL-UNNAMED` required for native audio (in `bootRun` task)

## Development Workflows

### Running the Application

```bash
# Backend only
./gradlew bootRun

# Frontend (separate terminal)
cd src/dashboard
yarn dev
```

### Building & Testing

```bash
./gradlew test          # Run all tests
./gradlew build         # Compile + test + create JAR
./gradlew bootJar       # Create executable JAR (main class auto-detected)
```

### Environment Variables

- `DATABASE`: MongoDB connection string (default: `mongodb://localhost:27017`)
- `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET`: For OAuth2 (dashboard auth)
- `DISCORD_REDIRECT_URI`, `DASHBOARD_URL`: OAuth callback configuration

## Technology-Specific Notes

### Kotlin Conventions

- Use data classes for DTOs/documents (`Session`, `Player`, `LogEntry`)
- Service interfaces in `service/`, implementations in `service/impl/` with `Impl` suffix
- Use Kotlin language features as much as possible (null operators, data classes, extension functions...etc)
- Companion objects for constants (see `WerewolfApplication.ROLES`, `WerewolfApplication.SERVER_CREATORS`)
- Do not use fully qualified names unless absolutely necessary - rely on imports

### Spring Boot Configuration

- Java 25 toolchain (specified in [build.gradle.kts](build.gradle.kts))
- MongoDB auto-configured via `application.properties` - uses Spring Session for HTTP sessions
- WebSocket config: [WebSocketConfig.kt](src/main/kotlin/dev/robothanzo/werewolf/config/WebSocketConfig.kt) - CORS
  allows `localhost:5173` and production domain

### MongoDB Integration

- Custom codec setup in [Database.kt](src/main/kotlin/dev/robothanzo/werewolf/database/Database.kt) with POJO
  conventions
- Use `@BsonIgnore` for computed properties (getters without backing fields)
- Session repository: Spring Data MongoDB interface in `security/SessionRepository.kt`
- After-save listener: `SessionAfterSaveListener` broadcasts updates via WebSocket on save

### Frontend Build Integration

- Dashboard builds separately (`yarn build` → `dist/`)
- Production: Serve static files from `dist/` via web server or embed in Spring Boot
- Development: Use Vite proxy to avoid CORS issues

## Common Pitfalls

- **Test failures**: Check for missing mock setup - all external dependencies (Discord, MongoDB) must be mocked
- **WebSocket not connecting**: Verify CORS origins in `WebSocketConfig` match dashboard URL
- **Session not found**: Each guild needs a `Session` document - created via `GameSessionService.createSession(guildId)`

## Key Files Reference

- Game flow: [game/steps/](src/main/kotlin/dev/robothanzo/werewolf/game/steps/) - NightStep, SpeechStep, VotingStep,
  etc.
- Role definitions: `WerewolfApplication.ROLES` - Chinese role names (狼人, 女巫, 獵人, etc.)
- API endpoints: [controller/GameController.kt](src/main/kotlin/dev/robothanzo/werewolf/controller/GameController.kt),
  SessionController, AuthController, SpeechController
- Role assignment logic: `service/impl/RoleServiceImpl.kt` - handles double identities, special role rules
