package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTextCodecTest {
    @Test void listsRenderAsEditableCommaSeparatedValues() {
        assertEquals("minecraft:villager, recruits:recruit",
                ConfigTextCodec.format(List.of("minecraft:villager", "recruits:recruit")));
        assertEquals("", ConfigTextCodec.format(List.of()));
    }

    @Test void commaSeparatedInputRemainsAListInsteadOfCorruptingItsConfigType() {
        Object parsed = ConfigTextCodec.parse(
                " minecraft:villager, recruits:recruit ,, minecraft:wandering_trader ",
                List.of("minecraft:villager"));
        assertInstanceOf(List.class, parsed);
        assertEquals(List.of("minecraft:villager", "recruits:recruit", "minecraft:wandering_trader"), parsed);
    }

    @Test void legacyBracketedDisplayCanBeEditedWithoutSavingBracketsAsValues() {
        assertEquals(List.of("BALLISTA", "CATAPULT"),
                ConfigTextCodec.parse("[BALLISTA, CATAPULT]", List.of("BALLISTA")));
    }

    @Test void blankInputProducesTheAllowedEmptyList() {
        assertEquals(List.of(), ConfigTextCodec.parse("   ", List.of("BALLISTA")));
        assertEquals(List.of(), ConfigTextCodec.parse("[]", List.of("BALLISTA")));
    }

    @Test void existingScalarParsingBehaviorIsPreserved() {
        assertEquals(42, ConfigTextCodec.parse(" 42 ", 0));
        assertEquals(2.5D, ConfigTextCodec.parse("2.5", 0D));
        assertEquals(" value ", ConfigTextCodec.parse(" value ", ""));
        assertNull(ConfigTextCodec.parse("not-a-number", 0));
    }
}
