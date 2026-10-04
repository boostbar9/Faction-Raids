package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.items.DefensePlanItem;
import com.devfarinsky.siegeoverhaul.nativecompat.NativeConstructionGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real plan confirmation/commission flow; only preflight and companion boundaries are stubbed. */
class DefenseCommissionDiagnosticsTest extends MinecraftTestSupport {
    @Test void protectionRefusalSurvivesRollbackAndKeepsPlanPreviewWithoutCharging() throws Exception {
        try (Fixture f = new Fixture()) {
            String reason = "Paused: neighboring minecraft:sand at 13, 64, 10 needs manual review";
            f.guard.when(() -> NativeConstructionGuard.status(f.area)).thenAnswer(call ->
                    f.area.getPersistentData().getString("SiegeConstructionPause"));
            f.area.getPersistentData().putString("SiegeConstructionPause", reason);
            f.bridge.when(() -> WorkersBridge.discardPlayerArea(f.area)).thenAnswer(call -> {
                f.area.getPersistentData().remove("SiegeConstructionPause");
                f.area.discard();
                return true;
            });

            String message = f.confirmFailure();
            assertTrue(message.contains("(site protection)"));
            assertTrue(message.contains(reason));
            f.bridge.verify(() -> WorkersBridge.startBlueprint(eq(f.area), any()));
            f.guard.verify(() -> NativeConstructionGuard.protect(f.player, f.builder, f.area,
                    DefenseStructures.reservedCells(f.plan)));
            f.bridge.verify(() -> WorkersBridge.assignBuildAreaDirectly(any(), any()), never());
            f.guard.verify(() -> NativeConstructionGuard.activate(any()), never());
            verify(f.area).discard();
            assertFalse(f.builder.getPersistentData().hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        }
    }

    @Test void inaccessibleMarkerReportsKnownReasonAndKeepsPlanWithoutChargingOrLinking() throws Exception {
        try (Fixture f = new Fixture()) {
            String reason = "Move onto clear ground inside your claim near the build site; no accessible native marker position is available.";
            f.markerFailure(new IllegalStateException(reason));
            String message = f.confirmFailure();
            assertTrue(message.contains("(build marker creation)"));
            assertTrue(message.contains(reason));
            verify(f.level, never()).addFreshEntity(any());
            f.bridge.verify(() -> WorkersBridge.releasePlayerJob(any(), any()), never());
            f.guard.verifyNoInteractions();
            assertFalse(f.builder.getPersistentData().hasUUID(ModConstants.Tags.PLAYER_FORTIFICATION_AREA_ID));
        }
    }

    @Test void unknownMarkerExceptionDetailsStayOutOfPlayerChat() throws Exception {
        try (Fixture f = new Fixture()) {
            f.markerFailure(new IllegalStateException("private-path-or-token-that-must-stay-in-the-server-log"));
            String message = f.confirmFailure();
            assertTrue(message.contains("(build marker creation)"));
            assertTrue(message.contains("See the server log for details."));
            assertFalse(message.contains("private-path-or-token"));
        }
    }

    @Test void protectionReasonRemainsBoundedAndSingleLine() throws Exception {
        try (Fixture f = new Fixture()) {
            f.guard.when(() -> NativeConstructionGuard.status(f.area))
                    .thenReturn("Paused:\n" + "a".repeat(400));
            String message = f.confirmFailure();
            assertFalse(message.contains("\n"));
            assertTrue(message.length() < 350);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final BlockPos origin = new BlockPos(10, 64, 10);
        final DefenseBlueprint.Kind kind = DefenseBlueprint.Kind.WALL;
        final DefenseBlueprint.Plan plan = DefenseBlueprint.create(kind, origin, Direction.SOUTH);
        final DefensePlanItem item = mock(DefensePlanItem.class, CALLS_REAL_METHODS);
        final ServerPlayer player = mock(ServerPlayer.class);
        final ServerLevel level = mock(ServerLevel.class);
        final Mob builder = mock(Mob.class);
        final Entity area = mock(Entity.class);
        final UseOnContext context = mock(UseOnContext.class);
        final ItemStack stack = new ItemStack(Items.PAPER);
        final UUID owner = UUID.randomUUID();
        final MockedStatic<WorkersBridge> bridge = mockStatic(WorkersBridge.class);
        final MockedStatic<PaymentSource> payment = mockStatic(PaymentSource.class);
        final MockedStatic<WallBuilderAccess> access = mockStatic(WallBuilderAccess.class);
        final MockedStatic<NativeConstructionGuard> guard = mockStatic(NativeConstructionGuard.class);
        final MockedStatic<DefenseStructures> structures = mockStatic(DefenseStructures.class, call ->
                call.getMethod().getName().equals("prepare")
                        ? new DefenseStructures.Preparation(builder, plan, null) : call.callRealMethod());

        Fixture() throws Exception {
            var kindField = DefensePlanItem.class.getDeclaredField("kind");
            kindField.setAccessible(true); kindField.set(item, kind);
            var menuField = Player.class.getDeclaredField("inventoryMenu");
            menuField.setAccessible(true); menuField.set(player, mock(InventoryMenu.class));
            when(player.level()).thenReturn(level); when(player.serverLevel()).thenReturn(level);
            when(player.getUUID()).thenReturn(owner);
            when(level.dimension()).thenReturn(Level.OVERWORLD); when(level.getGameTime()).thenReturn(20L);
            when(context.getPlayer()).thenReturn(player); when(context.getClickedFace()).thenReturn(Direction.UP);
            when(context.getClickedPos()).thenReturn(origin.below()); when(context.getItemInHand()).thenReturn(stack);
            when(builder.getUUID()).thenReturn(UUID.randomUUID());
            when(builder.getPersistentData()).thenReturn(new CompoundTag());
            when(area.getUUID()).thenReturn(UUID.randomUUID());
            when(area.getPersistentData()).thenReturn(new CompoundTag()); when(area.blockPosition()).thenReturn(origin.east(4));
            when(level.addFreshEntity(area)).thenReturn(true);
            bridge.when(() -> WorkersBridge.createProtectedPlayerArea(eq(player), eq(builder), any(), anyInt(), anyInt(), anyInt(), any()))
                    .thenReturn(area);
            bridge.when(() -> WorkersBridge.releasePlayerJob(builder, area)).thenReturn(true);
            bridge.when(() -> WorkersBridge.discardPlayerArea(area)).thenAnswer(call -> { area.discard(); return true; });
            DefensePreview.set(stack, kind, origin, Direction.SOUTH, Level.OVERWORLD.location(), owner, 0, null);
        }

        void markerFailure(RuntimeException failure) throws Exception {
            bridge.when(() -> WorkersBridge.createProtectedPlayerArea(eq(player), eq(builder), any(), anyInt(), anyInt(), anyInt(), any()))
                    .thenThrow(failure);
        }

        String confirmFailure() {
            CompoundTag originalPreview = stack.getTag().copy();
            assertEquals(InteractionResult.FAIL, item.useOn(context));
            assertEquals(1, stack.getCount());
            assertEquals(originalPreview, stack.getTag(), "Failure preserves the exact reviewed plan for retry");
            payment.verifyNoInteractions();
            var message = ArgumentCaptor.forClass(Component.class);
            verify(player).sendSystemMessage(message.capture());
            String text = message.getValue().getString();
            assertTrue(text.contains("No payment was taken; your plan is kept."));
            return text;
        }

        @Override public void close() {
            structures.close(); guard.close(); access.close(); payment.close(); bridge.close();
        }
    }
}
