package com.devfarinsky.siegeoverhaul.siege;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SiegeIntegrationTest extends MinecraftTestSupport {
    public abstract static class Engineer extends Mob {
        public Object siegeController;
        public boolean getShouldRanged() { return true; }
        public void setShouldRanged(boolean value) {}
        protected Engineer() { super(EntityType.ZOMBIE, null); }
    }
    public static class Controller {
        Entity siegeEntity;
        boolean fail, mismatch;
        public void tryMount(Entity entity) {
            if (fail) throw new IllegalStateException("Native attach failure");
            siegeEntity = mismatch ? null : entity;
        }
        public Entity getSiegeEntity() { return siegeEntity; }
    }
    private Engineer engineer() {
        var engineer = mock(Engineer.class);
        var passengerOf = new AtomicReference<Entity>();
        when(engineer.startRiding(any(Entity.class), eq(true))).thenAnswer(call -> {
            passengerOf.set(call.getArgument(0)); return true;
        });
        when(engineer.getVehicle()).thenAnswer(call -> passengerOf.get());
        doAnswer(call -> { passengerOf.set(null); return null; }).when(engineer).stopRiding();
        return engineer;
    }
    @Test void dismountedOperatorRestoresFireEvenWhenItsVehicleIsUnloaded() {
        var engineer = engineer();
        var data = new net.minecraft.nbt.CompoundTag();
        data.putBoolean("SiegeAdvanceSavedRanged", true);
        when(engineer.getPersistentData()).thenReturn(data);
        SiegeIntegration.advanceEngineer(engineer, net.minecraft.core.BlockPos.ZERO);
        verify(engineer).setShouldRanged(true);
        assertFalse(data.contains("SiegeAdvanceSavedRanged"));
        verify(engineer, never()).level();
    }
    private boolean attach(Mob engineer, Entity engine, Controller controller) throws Exception {
        return SiegeIntegration.mountWithController(engineer, engine, controller,
                Controller.class.getMethod("tryMount", Entity.class));
    }
    @Test void successfulAttachRetainsPassengerAndActivatesVerifiedController() throws Exception {
        var engineer=engineer();var engine=mock(Entity.class);var controller=new Controller();
        assertTrue(attach(engineer,engine,controller));
        assertSame(engine,engineer.getVehicle());assertSame(controller,engineer.siegeController);
        verify(engineer,never()).stopRiding();
    }
    @Test void missingControllerDoesNotMount() throws Exception {
        var engineer=engineer();
        assertFalse(attach(engineer,mock(Entity.class),null));
        verify(engineer,never()).startRiding(any(),anyBoolean());
    }
    @Test void nativeExceptionDismountsAndAllowsSuccessfulRetry() throws Exception {
        var engineer=engineer();var engine=mock(Entity.class);var controller=new Controller();controller.fail=true;
        assertFalse(attach(engineer,engine,controller));
        assertNull(engineer.getVehicle());assertNull(engineer.siegeController);
        verify(engineer).stopRiding();
        controller.fail=false;
        assertTrue(attach(engineer,engine,controller));assertSame(engine,engineer.getVehicle());
    }
    @Test void wrongControllerVehicleRollsBackWithoutReplacingActiveController() throws Exception {
        var engineer=engineer();var engine=mock(Entity.class);var controller=new Controller();controller.mismatch=true;
        Object previous=new Object();engineer.siegeController=previous;
        assertFalse(attach(engineer,engine,controller));
        assertNull(engineer.getVehicle());assertSame(previous,engineer.siegeController);
    }
    @Test void missingEngineerApiIsRejectedBeforeMounting() throws Exception {
        var engineer=mock(Mob.class);
        assertFalse(attach(engineer,mock(Entity.class),new Controller()));
        verify(engineer,never()).startRiding(any(),anyBoolean());
    }
    @Test void refusedMountDoesNotAttachControllerOrDismountAnotherVehicle() throws Exception {
        var engineer=engineer();var engine=mock(Entity.class);var other=mock(Entity.class);var controller=new Controller();
        when(engineer.startRiding(engine,true)).thenReturn(false);
        when(engineer.getVehicle()).thenReturn(other);
        assertFalse(attach(engineer,engine,controller));assertNull(controller.siegeEntity);
        verify(engineer,never()).stopRiding();
    }
    @Test void controllerCannotSucceedWithoutActualPassengerRelationship() throws Exception {
        var engineer=engineer();var engine=mock(Entity.class);var controller=new Controller();
        when(engineer.getVehicle()).thenReturn(null);
        assertFalse(attach(engineer,engine,controller));assertNull(engineer.siegeController);
    }
}
