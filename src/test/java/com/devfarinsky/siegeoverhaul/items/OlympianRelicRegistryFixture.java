package com.devfarinsky.siegeoverhaul.items;

import net.minecraft.world.item.Items;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

/** Plain JUnit has no Forge registration event. Native items stand in only for registry identity. */
final class OlympianRelicRegistryFixture implements AutoCloseable {
    private final MockedStatic<OlympianRelics> registry = mockStatic(OlympianRelics.class);
    OlympianRelicRegistryFixture() {
        registry.when(() -> OlympianRelics.item(OlympianRelics.Kind.FORGE)).thenReturn(Items.BLAZE_POWDER);
        registry.when(() -> OlympianRelics.item(OlympianRelics.Kind.WATCH)).thenReturn(Items.PRISMARINE_CRYSTALS);
        registry.when(() -> OlympianRelics.item(OlympianRelics.Kind.CLEANSE)).thenReturn(Items.NAUTILUS_SHELL);
    }
    @Override public void close() { registry.close(); }
}
