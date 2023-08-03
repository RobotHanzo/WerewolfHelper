package dev.robothanzo.werewolf.utils;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.*;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MsgUtils {
    public static Pattern p = Pattern.compile("^([0-9]+)([a-z]?)$");

    public static Color getRandomColor() {
        return new Color((int) (Math.random() * 0x1000000));
    }

    public static int getSortOrder(String s) {
        Matcher m = p.matcher(s);
        if (!m.matches()) return 0;
        int major = Integer.parseInt(m.group(1));
        int minor = m.group(2).isEmpty() ? 0 : m.group(2).charAt(0);
        return (major << 8) | minor;
    }

    public static Comparator<String> getAlphaNumComparator() {
        return Comparator.comparingInt(MsgUtils::getSortOrder);
    }

    public static List<ActionRow> spreadButtonsAcrossActionRows(List<net.dv8tion.jda.api.interactions.components.buttons.Button> buttons) {
        List<ActionRow> rows = new LinkedList<>(List.of(ActionRow.of(buttons.remove(0))));
        for (Button button : buttons) {
            if (rows.get(rows.size() - 1).getComponents().size() >= 5) {
                rows.add(ActionRow.of(button));
            } else {
                List<Button> newButtons = new LinkedList<>(rows.get(rows.size() - 1).getButtons());
                newButtons.add(button);
                rows.set(rows.size() - 1, ActionRow.of(newButtons));
            }
        }
        return rows;
    }

}
