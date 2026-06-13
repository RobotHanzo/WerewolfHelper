package dev.robothanzo.werewolf.domain

/**
 * One identity held by a seat. Death is tracked **per card** (soft death, FEATURES §2):
 * a seat is alive while it has more cards than dead cards. In double-identity mode losing one
 * card keeps the player in the game.
 *
 * [roleId] is the stable ASCII id from the role registry; the localized name and faction are
 * resolved from the registry so a future locale or role never requires a data migration.
 */
data class IdentityCard(
    val roleId: String,
    var dead: Boolean = false,
)
