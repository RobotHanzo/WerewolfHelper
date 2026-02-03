package dev.robothanzo.werewolf.utils

import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import java.awt.Color
import java.util.*
import java.util.regex.Pattern

object MsgUtils {
    val p: Pattern = Pattern.compile("^([0-9]+)([a-z]?)$")
    val randomColor: Color
        get() = Color((Math.random() * 0x1000000).toInt())

    fun getSortOrder(s: String): Int {
        val m = p.matcher(s)
        if (!m.matches()) return 0
        val major = m.group(1).toInt()
        val minor = if (m.group(2).isEmpty()) 0 else m.group(2)[0].code
        return (major shl 8) or minor
    }

    fun getAlphaNumComparator(): Comparator<String> {
        return Comparator.comparingInt { s: String -> getSortOrder(s) }
    }

    fun spreadButtonsAcrossActionRows(buttons: List<Button>): List<ActionRow> {
        val mutableButtons = LinkedList(buttons)
        if (mutableButtons.isEmpty()) return emptyList()

        val rows = LinkedList(listOf(ActionRow.of(mutableButtons.removeFirst())))
        for (button in mutableButtons) {
            if (rows.last.components.size >= 5) {
                rows.add(ActionRow.of(button))
            } else {
                val newButtons = LinkedList(rows.last.buttons)
                newButtons.add(button)
                rows[rows.size - 1] = ActionRow.of(newButtons)
            }
        }
        return rows
    }
}
