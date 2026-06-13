package dev.robothanzo.werewolf.game.night

/**
 * One declared night action. The wolf kill carries every participating wolf in [actorSeats]; most
 * abilities have a single actor. [targets] are seat numbers; [skipped] means the actor (or the
 * judge, on their behalf) declined.
 */
data class NightIntent(
    val abilityId: String,
    val roleId: String,
    val actorSeats: Set<Int>,
    val targets: List<Int> = emptyList(),
    val skipped: Boolean = false,
    /** Free-form sub-action data (e.g. witch `save`/`poison` seat numbers). */
    val meta: Map<String, Int> = emptyMap(),
)

/**
 * Accumulates the declared intents for one night, wave by wave. The judge can submit or skip on a
 * player's behalf; the last submission for an ability wins.
 */
class NightLedger {
    private val intents = LinkedHashMap<String, NightIntent>()

    fun submit(intent: NightIntent) {
        intents[intent.abilityId] = intent
    }

    fun byAbility(abilityId: String): NightIntent? = intents[abilityId]

    fun all(): List<NightIntent> = intents.values.toList()

    fun isSubmitted(abilityId: String): Boolean = intents.containsKey(abilityId)
}
