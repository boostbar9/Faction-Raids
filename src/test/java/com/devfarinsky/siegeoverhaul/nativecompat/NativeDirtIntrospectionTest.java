package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.devfarinsky.siegeoverhaul.nativecompat.NativeDirtPolicy.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real vanilla graph/registry objects with explicit test-only mapped field lookup, no loot execution. */
class NativeDirtIntrospectionTest extends MinecraftTestSupport {
    static final Map<String, String> MAPPED = Map.ofEntries(
            Map.entry("f_79108_", "paramSet"), Map.entry("f_286958_", "randomSequence"), Map.entry("f_79109_", "pools"),
            Map.entry("f_79110_", "functions"), Map.entry("f_79023_", "entries"), Map.entry("f_79024_", "conditions"),
            Map.entry("f_79026_", "functions"), Map.entry("f_79028_", "rolls"), Map.entry("f_79029_", "bonusRolls"),
            Map.entry("f_79636_", "conditions"), Map.entry("f_79675_", "weight"), Map.entry("f_79676_", "quality"),
            Map.entry("f_79677_", "functions"), Map.entry("f_79564_", "item"), Map.entry("f_165688_", "value"),
            Map.entry("f_244451_", "gameEventListenerRegistrySections"), Map.entry("f_244422_", "listeners"),
            Map.entry("f_244308_", "listenersToRemove"), Map.entry("f_244008_", "listenersToAdd"), Map.entry("f_244249_", "processing"));
    static final NativeDirtIntrospection.Fields LOOKUP = (owner, srg) -> owner.getDeclaredField(MAPPED.get(srg));
    private final NativeDirtIntrospection bridge = new NativeDirtIntrospection(LOOKUP);

    @Test void exactFrozenNativeGraphIsRecognizedWithoutGeneratingLoot() { assertTrue(bridge.loot(dirt()).ready()); }
    @Test void unfrozenOrWrongTableOrRandomSequenceIsRejected() throws Exception {
        LootTable table = dirt(); set(table, LootTable.class, "isFrozen", false);
        assertEquals(Reason.LOOT_GRAPH_CHANGED, bridge.loot(table).reason());
        table = dirt(); set(table, LootTable.class, "lootTableId", NativeDirtIntrospection.TABLE.withPath("blocks/stone"));
        assertEquals(Reason.LOOT_GRAPH_CHANGED, bridge.loot(table).reason());
        table = dirt(); set(table, LootTable.class, "randomSequence", null);
        assertEquals(Reason.LOOT_GRAPH_CHANGED, bridge.loot(table).reason());
    }
    @Test void lootLoadMutationOfFrozenGraphCannotHideBehindOriginalResourceBytes() throws Exception {
        LootTable table = dirt(); set(table.getPool("main"), LootPool.class, "rolls", ConstantValue.exactly(2));
        assertEquals(Reason.LOOT_GRAPH_CHANGED, bridge.loot(table).reason());
        table = dirt(); set(table.getPool("main"), LootPool.class, "bonusRolls", ConstantValue.exactly(1));
        assertEquals(Reason.LOOT_GRAPH_CHANGED, bridge.loot(table).reason());
        table = dirt(); set(table.getPool("main"), LootPool.class, "conditions", new LootItemCondition[0]);
        assertEquals(Reason.LOOT_GRAPH_CHANGED, bridge.loot(table).reason());
    }
    @Test void unknownProvidersConditionsAndFunctionsAreNeverInvoked() throws Exception {
        NumberProvider provider = mock(NumberProvider.class);
        LootTable table = dirt(); set(table.getPool("main"), LootPool.class, "rolls", provider);
        assertEquals(Reason.LOOT_GRAPH_CHANGED, bridge.loot(table).reason()); verifyNoInteractions(provider);
        LootItemCondition condition = mock(LootItemCondition.class);
        table = dirt(); set(table.getPool("main"), LootPool.class, "conditions", new LootItemCondition[]{condition});
        assertEquals(Reason.LOOT_GRAPH_CHANGED, bridge.loot(table).reason()); verifyNoInteractions(condition);
        LootItemFunction function = mock(LootItemFunction.class);
        table = dirt(); set(table, LootTable.class, "functions", new LootItemFunction[]{function});
        assertEquals(Reason.LOOT_GRAPH_CHANGED, bridge.loot(table).reason()); verifyNoInteractions(function);
    }
    @Test void missingOrRemappedOrWrongDescriptorFieldsRefuseInsteadOfGuessingNames() {
        NativeDirtIntrospection missing = new NativeDirtIntrospection((owner, name) -> { throw new NoSuchFieldException(name); });
        assertEquals(Reason.API_UNAVAILABLE, missing.loot(dirt()).reason());
        NativeDirtIntrospection wrong = new NativeDirtIntrospection((owner, name) -> owner.getDeclaredField("functions"));
        assertEquals(Reason.API_UNAVAILABLE, wrong.loot(dirt()).reason());
        NativeDirtIntrospection remapped = new NativeDirtIntrospection((owner, name) -> owner.getDeclaredField(name));
        assertEquals(Reason.API_UNAVAILABLE, remapped.loot(dirt()).reason());
    }
    @Test void absentNoopAndIdleNativeRegistriesAreSafeWithoutReadingPositions() {
        assertTrue(bridge.registry(null).ready()); assertTrue(bridge.registry(GameEventListenerRegistry.NOOP).ready());
        assertTrue(bridge.registry(registry()).ready());
    }
    @Test void unknownRegistryIsNotAskedToReportEmpty() {
        GameEventListenerRegistry unknown = mock(GameEventListenerRegistry.class);
        assertEquals(Reason.GAME_EVENT_LISTENER, bridge.registry(unknown).reason()); verifyNoInteractions(unknown);
    }
    @Test void sculkOrOtherRegisteredListenerRefusesWithoutExecutingItsPositionOrFilter() throws Exception {
        var registry = registry(); GameEventListener listener = mock(GameEventListener.class);
        ((List<Object>) field(registry, "listeners")).add(listener);
        assertEquals(Reason.GAME_EVENT_LISTENER, bridge.registry(registry).reason()); verifyNoInteractions(listener);
    }
    @Test void pendingChangesOrReentrantProcessingRefuseEvenWhenIsEmptyWouldBeTrue() throws Exception {
        var registry = registry(); set(registry, registry.getClass(), "processing", true);
        assertEquals(Reason.GAME_EVENT_BUSY, bridge.registry(registry).reason());
        registry = registry(); ((List<Object>) field(registry, "listenersToAdd")).add(mock(GameEventListener.class));
        assertTrue(registry.isEmpty()); assertEquals(Reason.GAME_EVENT_BUSY, bridge.registry(registry).reason());
        registry = registry(); ((Set<Object>) field(registry, "listenersToRemove")).add(mock(GameEventListener.class));
        assertTrue(registry.isEmpty()); assertEquals(Reason.GAME_EVENT_BUSY, bridge.registry(registry).reason());
    }
    static LootTable dirt() {
        LootTable table = LootTable.lootTable().setParamSet(LootContextParamSets.BLOCK).setRandomSequence(NativeDirtIntrospection.TABLE)
                .withPool(LootPool.lootPool().name("main").setRolls(ConstantValue.exactly(1)).setBonusRolls(ConstantValue.exactly(0))
                        .add(LootItem.lootTableItem(Items.DIRT)).when(ExplosionCondition.survivesExplosion())).build();
        table.setLootTableId(NativeDirtIntrospection.TABLE); table.freeze(); return table;
    }
    private static EuclideanGameEventListenerRegistry registry() { return new EuclideanGameEventListenerRegistry(null, 4, ignored -> {}); }
    static void set(Object object, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
}
