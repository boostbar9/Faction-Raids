package com.devfarinsky.siegeoverhaul.nativecompat;

import com.talhanation.workers.entities.workarea.BuildArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;

/** Type/override contracts only. Actual entity lifecycle cases run in ProtectedNativeEntityContracts. */
class ProtectedBuildAreaTest {
    @Test void protectedTypeExtendsTheActualNativeClassWithoutReplacingLegacyRegistration() {
        assertEquals(BuildArea.class, ProtectedBuildArea.class.getSuperclass());
        assertTrue(Modifier.isFinal(ProtectedBuildArea.class.getModifiers()));
    }
    @Test void everyDirectNativeControlMutationHasASealedOverride() throws Exception {
        for (String method : new String[]{"setStartBuild", "setFreeArea", "setAlwaysShowProjection", "setTeamAccess", "setDone"})
            assertNotNull(ProtectedBuildArea.class.getDeclaredMethod(method, boolean.class));
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("setStructureNBT", CompoundTag.class));
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("setFacing", Direction.class));
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("remove", Entity.RemovalReason.class));
    }
    @Test void rawPositionMoveOverloadsAreOverriddenInAdditionToSetPos() throws Exception {
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("setPos", double.class, double.class, double.class));
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("moveTo", double.class, double.class, double.class));
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("moveTo", double.class, double.class, double.class, float.class, float.class));
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("moveTo", Vec3.class));
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("moveTo", BlockPos.class, float.class, float.class));
    }
    @Test void originEnvelopeAndStructureCopyAreExplicitNativeExtensionPoints() throws Exception {
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("getOriginPos"));
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("createArea"));
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("getArea"));
        assertNotNull(ProtectedBuildArea.class.getDeclaredMethod("getStructureNBT"));
    }
}
