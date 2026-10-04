package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.IForgeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampGuardSpawnTest extends MinecraftTestSupport {
    private static final String SCOUT = "com.talhanation.recruits.entities.ScoutEntity";
    private static final String NAV = "com.talhanation.recruits.entities.ai.navigation.RecruitPathNavigation";

    @Test void missingRoleNeverLooksUpTheRegistryDefaultPig() {
        IForgeRegistry<EntityType<?>> registry = mock(IForgeRegistry.class);
        var requested = new ResourceLocation("recruits", "assassin");
        doReturn(EntityType.PIG).when(registry).getValue(requested);
        assertNull(CampGuardSpawn.exactType(registry, requested));
        verify(registry, never()).getValue(requested);
    }

    @Test void registeredEntryMustRetainItsExactRequestedIdentity() {
        IForgeRegistry<EntityType<?>> registry = mock(IForgeRegistry.class);
        var requested = new ResourceLocation("recruits", "scout");
        when(registry.containsKey(requested)).thenReturn(true);
        doReturn(EntityType.PIG).when(registry).getValue(requested);
        when(registry.getKey(EntityType.PIG)).thenReturn(new ResourceLocation("minecraft", "pig"));
        assertNull(CampGuardSpawn.exactType(registry, requested));
    }

    @Test void onlyExactVersionClassAndObservedCastBoundaryCanResume() {
        ClassCastException failure = cast(94);
        assertTrue(CampGuardSpawn.knownScoutCast("1.15.2", SCOUT, NAV, "finalizeSpawn", failure));
        assertFalse(CampGuardSpawn.knownScoutCast("1.15.3", SCOUT, NAV, "finalizeSpawn", failure));
        assertFalse(CampGuardSpawn.knownScoutCast("1.15.2", SCOUT + "Subclass", NAV, "finalizeSpawn", failure));
        assertFalse(CampGuardSpawn.knownScoutCast("1.15.2", SCOUT, NAV + "Subclass", "finalizeSpawn", failure));
        assertFalse(CampGuardSpawn.knownScoutCast("1.15.2", SCOUT, NAV, "finalizeSpawn", cast(93)));
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement(SCOUT, "initSpawn", "ScoutEntity.java", 94)});
        assertFalse(CampGuardSpawn.knownScoutCast("1.15.2", SCOUT, NAV, "finalizeSpawn", failure));
        failure = cast(94); failure.initCause(new IllegalStateException("unrelated failure"));
        assertFalse(CampGuardSpawn.knownScoutCast("1.15.2", SCOUT, NAV, "finalizeSpawn", failure));
    }

    @Test void tailUsesTheExistingNavigatorAndRunsOnlyTheRemainingInitialization() throws Exception {
        ScoutProbe scout = new ScoutProbe(); Object original = scout.getNavigation();
        var tail = CampGuardSpawn.tail(scout, original);
        CampGuardSpawn.finishTail(scout, original, tail, scout::getNavigation);
        assertSame(original, scout.getNavigation()); assertTrue(scout.navigation.doors);
        assertEquals(1, scout.initCalls); assertEquals(0, scout.finalizeCalls);
    }

    @Test void vanillaOverrideIsIdentifiedBySignatureAcrossRuntimeMappings() throws Exception {
        String name = CampGuardSpawn.finalizeMethod(MappedSignature.class);
        assertEquals("runtimeMappedName", name);
        ClassCastException failure = cast(94);
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement(SCOUT, name, "ScoutEntity.java", 94)});
        assertTrue(CampGuardSpawn.knownScoutCast("1.15.2", SCOUT, NAV, name, failure));
        assertThrows(NoSuchMethodException.class, () -> CampGuardSpawn.finalizeMethod(ScoutProbe.class));
    }

    @Test void changedOrUnsupportedTailFailsBeforeAnyMutation() {
        ScoutProbe scout = new ScoutProbe(); BadNavigator bad = new BadNavigator();
        assertThrows(NoSuchMethodException.class, () -> CampGuardSpawn.tail(scout, bad));
        assertFalse(bad.doors); assertEquals(0, scout.initCalls);
        Navigator original = scout.navigation;
        assertThrows(IllegalStateException.class, () -> CampGuardSpawn.finishTail(scout, new Navigator(), CampGuardSpawn.tail(scout, original), scout::getNavigation));
        assertFalse(original.doors); assertEquals(0, scout.initCalls);
    }

    @Test void navigatorReplacementDuringTailIsRejected() throws Exception {
        ScoutProbe scout = new ScoutProbe(); scout.replaceNavigation = true;
        Navigator original = scout.navigation;
        assertThrows(IllegalStateException.class, () -> CampGuardSpawn.finishTail(scout, original, CampGuardSpawn.tail(scout, original), scout::getNavigation));
        assertEquals(1, scout.initCalls); assertEquals(0, scout.finalizeCalls);
    }

    private static ClassCastException cast(int line) {
        ClassCastException failure = new ClassCastException("class " + NAV + " cannot be cast to class net.minecraft.world.entity.ai.navigation.GroundPathNavigation");
        failure.setStackTrace(new StackTraceElement[]{new StackTraceElement(SCOUT, "finalizeSpawn", "ScoutEntity.java", line)});
        return failure;
    }
    public static class Navigator { boolean doors; public void setCanOpenDoors(boolean value) { doors = value; } }
    public static class BadNavigator { boolean doors; public boolean setCanOpenDoors(boolean value) { doors = value; return value; } }
    public static class ScoutProbe {
        Navigator navigation = new Navigator(); int initCalls, finalizeCalls; boolean replaceNavigation;
        public Navigator getNavigation() { return navigation; }
        public void initSpawn() { initCalls++; if (replaceNavigation) navigation = new Navigator(); }
        public void finalizeSpawn() { finalizeCalls++; }
    }
    public static class MappedSignature {
        public net.minecraft.world.entity.SpawnGroupData runtimeMappedName(net.minecraft.world.level.ServerLevelAccessor level,
                net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType reason,
                net.minecraft.world.entity.SpawnGroupData group, net.minecraft.nbt.CompoundTag nbt) { return group; }
    }
}
