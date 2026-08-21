package net.mellowsmp.duels.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Converts {@code &} / {@code §} color codes into Adventure components and
 * legacy strings. {@link Component#text(String)} does not interpret color
 * codes, which is why titles and item names were showing raw {@code &e} text.
 */
public final class Texts {

    private static final LegacyComponentSerializer AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer SECTION =
            LegacyComponentSerializer.legacySection();

    private Texts() {
    }

    public static String legacy(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return SECTION.serialize(AMPERSAND.deserialize(input.replace('§', '&')));
    }

    public static Component component(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return AMPERSAND.deserialize(input.replace('§', '&'));
    }
}
