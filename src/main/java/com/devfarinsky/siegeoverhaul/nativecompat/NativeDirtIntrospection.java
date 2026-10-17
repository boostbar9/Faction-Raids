package com.devfarinsky.siegeoverhaul.nativecompat;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraftforge.common.ForgeInternalHandler;
import net.minecraftforge.common.loot.LootModifierManager;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.devfarinsky.siegeoverhaul.nativecompat.NativeDirtPolicy.*;

/** Exact 1.20.1/47.4.16 read-only fields. No predicate, number provider, loot or listener is evaluated. */
final class NativeDirtIntrospection {
    static final ResourceLocation TABLE = new ResourceLocation("minecraft", "blocks/dirt");
    interface Fields { Field find(Class<?> owner, String srg) throws ReflectiveOperationException; }
    private final Fields fields;
    NativeDirtIntrospection() { this(ObfuscationReflectionHelper::findField); }
    // Alternate resolver exists only for mapping/descriptor regressions outside a launched Forge runtime.
    NativeDirtIntrospection(Fields fields) { this.fields = fields; }

    Object read(Object object, Class<?> owner, String srg, Class<?> type) throws ReflectiveOperationException {
        Field f = fields.find(owner, srg);
        return readChecked(f, object, owner, type, false);
    }
    static Object raw(Object object, Class<?> owner, String name, Class<?> type, boolean isStatic) throws ReflectiveOperationException {
        return readChecked(owner.getDeclaredField(name), object, owner, type, isStatic);
    }
    private static Object readChecked(Field f, Object object, Class<?> owner, Class<?> type, boolean isStatic) throws ReflectiveOperationException {
        if (f.getDeclaringClass() != owner || f.getType() != type || Modifier.isStatic(f.getModifiers()) != isStatic)
            throw new NoSuchFieldException("Pinned field descriptor changed: " + owner.getName());
        if (!f.trySetAccessible()) throw new IllegalAccessException("Pinned field is inaccessible: " + owner.getName());
        return f.get(object);
    }
    static LootModifierManager modifierManager() throws ReflectiveOperationException {
        Object manager = raw(null, ForgeInternalHandler.class, "INSTANCE", LootModifierManager.class, true);
        if (manager == null || manager.getClass() != LootModifierManager.class)
            throw new IllegalStateException("Exact Forge loot modifier manager is unavailable");
        return (LootModifierManager) manager;
    }
    Check loot(LootTable table) {
        try {
            if (table == null || table.getClass() != LootTable.class || !table.isFrozen() || !TABLE.equals(table.getLootTableId())
                    || read(table, LootTable.class, "f_79108_", LootContextParamSet.class) != LootContextParamSets.BLOCK
                    || !TABLE.equals(read(table, LootTable.class, "f_286958_", ResourceLocation.class))
                    || !emptyFunctions(read(table, LootTable.class, "f_79110_", LootItemFunction[].class))) return graph();
            Object pools = read(table, LootTable.class, "f_79109_", List.class);
            if (pools == null || pools.getClass() != ArrayList.class || ((List<?>) pools).size() != 1) return graph();
            Object candidate = ((List<?>) pools).get(0);
            if (candidate == null || candidate.getClass() != LootPool.class) return graph();
            LootPool pool = (LootPool) candidate;
            if (!pool.isFrozen() || !"main".equals(pool.getName())
                    || !constant(read(pool, LootPool.class, "f_79028_", NumberProvider.class), 1.0f)
                    || !constant(read(pool, LootPool.class, "f_79029_", NumberProvider.class), 0.0f)
                    || !emptyFunctions(read(pool, LootPool.class, "f_79026_", LootItemFunction[].class))) return graph();
            var conditions = (LootItemCondition[]) read(pool, LootPool.class, "f_79024_", LootItemCondition[].class);
            if (conditions == null || conditions.length != 1 || conditions[0] == null || conditions[0].getClass() != ExplosionCondition.class) return graph();
            var entries = (LootPoolEntryContainer[]) read(pool, LootPool.class, "f_79023_", LootPoolEntryContainer[].class);
            if (entries == null || entries.length != 1 || entries[0] == null || entries[0].getClass() != LootItem.class) return graph();
            Object entry = entries[0];
            var entryConditions = (LootItemCondition[]) read(entry, LootPoolEntryContainer.class, "f_79636_", LootItemCondition[].class);
            if (entryConditions == null || entryConditions.length != 0
                    || !Integer.valueOf(1).equals(read(entry, LootPoolSingletonContainer.class, "f_79675_", int.class))
                    || !Integer.valueOf(0).equals(read(entry, LootPoolSingletonContainer.class, "f_79676_", int.class))
                    || !emptyFunctions(read(entry, LootPoolSingletonContainer.class, "f_79677_", LootItemFunction[].class))
                    || read(entry, LootItem.class, "f_79564_", Item.class) != Items.DIRT || Items.DIRT.getClass() != BlockItem.class) return graph();
            return readyCheck();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return denied(Reason.API_UNAVAILABLE, "pinned loaded-loot fields or mappings are unavailable");
        }
    }
    private boolean constant(Object provider, float expected) throws ReflectiveOperationException {
        return provider != null && provider.getClass() == ConstantValue.class
                && Float.valueOf(expected).equals(read(provider, ConstantValue.class, "f_165688_", float.class));
    }
    private static boolean emptyFunctions(Object value) {
        return value != null && value.getClass() == LootItemFunction[].class && ((LootItemFunction[]) value).length == 0;
    }
    private static Check graph() { return denied(Reason.LOOT_GRAPH_CHANGED, "loaded dirt table is not the exact frozen vanilla one-dirt graph"); }

    /** Reads only existing map entries; getListenerRegistry would create a registry and is not used. */
    Object existingRegistry(LevelChunk chunk, int sectionY) throws ReflectiveOperationException {
        Object map = read(chunk, LevelChunk.class, "f_244451_", Int2ObjectMap.class);
        if (map == null || map.getClass() != Int2ObjectOpenHashMap.class)
            throw new IllegalStateException("Unknown game-event registry map");
        return ((Int2ObjectMap<?>) map).get(sectionY);
    }
    Check registry(Object registry) {
        if (registry == null || registry == GameEventListenerRegistry.NOOP) return readyCheck();
        if (registry.getClass() != EuclideanGameEventListenerRegistry.class)
            return denied(Reason.GAME_EVENT_LISTENER, "unknown nearby game-event registry");
        try {
            Class<?> owner = EuclideanGameEventListenerRegistry.class;
            if (!Boolean.FALSE.equals(read(registry, owner, "f_244249_", boolean.class)))
                return denied(Reason.GAME_EVENT_BUSY, "nearby game-event registry is processing");
            Object listeners = read(registry, owner, "f_244422_", List.class);
            Object additions = read(registry, owner, "f_244008_", List.class);
            Object removals = read(registry, owner, "f_244308_", Set.class);
            if (listeners == null || listeners.getClass() != ArrayList.class || additions == null || additions.getClass() != ArrayList.class
                    || removals == null || removals.getClass() != HashSet.class)
                return denied(Reason.API_UNAVAILABLE, "nearby game-event registry storage is unsupported");
            if (!((List<?>) additions).isEmpty() || !((Set<?>) removals).isEmpty())
                return denied(Reason.GAME_EVENT_BUSY, "nearby game-event registry has pending changes");
            return ((List<?>) listeners).isEmpty() ? readyCheck()
                    : denied(Reason.GAME_EVENT_LISTENER, "BLOCK_DESTROY could reach a nearby game-event listener");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return denied(Reason.API_UNAVAILABLE, "pinned game-event fields or mappings are unavailable");
        }
    }
}
