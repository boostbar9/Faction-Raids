package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PerimeterConfirmationTest extends MinecraftTestSupport {
    private final UUID owner=UUID.randomUUID();private final BlockPos core=new BlockPos(8,64,8);
    private ServerPlayer player(long now) throws Exception {
        var player=mock(ServerPlayer.class);var level=mock(ServerLevel.class);
        when(player.getUUID()).thenReturn(owner);when(player.level()).thenReturn(level);when(player.serverLevel()).thenReturn(level);
        when(level.dimension()).thenReturn(Level.OVERWORLD);when(level.getGameTime()).thenReturn(now);
        var field=Player.class.getDeclaredField("inventoryMenu");field.setAccessible(true);field.set(player,mock(InventoryMenu.class));
        return player;
    }
    private PerimeterConstruction.Preparation ready() {
        var plan=PerimeterBlueprint.create(Set.of(new ChunkPos(0,0)),(x,z)->PerimeterBlueprint.Surface.ready(64),PerimeterBlueprint.Palette.COBBLESTONE);
        return new PerimeterConstruction.Preparation(mock(Mob.class),plan,"claim",null);
    }
    private ItemStack preview(PerimeterConstruction.Preparation p,String hash) {
        var stack=new ItemStack(Items.PAPER);PerimeterPreview.set(stack,owner,Level.OVERWORLD.location(),core,1,100,hash,null,"materials",p.plan().blocks());return stack;
    }
    private String hash(PerimeterConstruction.Preparation p) {return PerimeterPreview.fingerprint(p.plan().blocks(),core,1,p.claimIdentity());}
    @Test void successfulConfirmationConsumesPlanAndCannotBeRepeated() throws Exception {
        var player=player(120);var p=ready();var stack=preview(p,hash(p));var calls=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BiPredicate<PerimeterConstruction.Preparation,Integer> start=(prepared,material)->{calls.incrementAndGet();assertSame(p,prepared);assertEquals(1,material);return true;};
        assertTrue(PerimeterConstruction.confirm(player,stack,selection->p,start));assertTrue(stack.isEmpty());
        assertFalse(PerimeterConstruction.confirm(player,stack,selection->p,start));assertEquals(1,calls.get());
    }
    @Test void changedQuoteRefreshesBeforeAnyHandoff() throws Exception {
        var player=player(120);var p=ready();var stack=preview(p,"0".repeat(64));var calls=new java.util.concurrent.atomic.AtomicInteger();
        assertFalse(PerimeterConstruction.confirm(player,stack,selection->p,(prepared,material)->{calls.incrementAndGet();return true;}));
        assertEquals(1,stack.getCount());assertEquals(0,calls.get());
        var refreshed=PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),120);
        assertEquals(hash(p),refreshed.fingerprint());assertFalse(refreshed.canConfirm(120));
    }
    @Test void failedHandoffRetainsThePlanAndCanceledPlansCannotStart() throws Exception {
        var player=player(120);var p=ready();var stack=preview(p,hash(p));var calls=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BiPredicate<PerimeterConstruction.Preparation,Integer> start=(prepared,material)->{calls.incrementAndGet();return false;};
        assertFalse(PerimeterConstruction.confirm(player,stack,selection->p,start));assertEquals(1,stack.getCount());
        assertNotNull(PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),120));
        PerimeterPreview.clear(stack);assertFalse(PerimeterConstruction.confirm(player,stack,selection->p,start));assertEquals(1,calls.get());
    }
    @Test void foreignSelectionsDoNotEvenPrepareOrStartAJob() throws Exception {
        var player=player(120);var p=ready();var stack=preview(p,hash(p));
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        assertFalse(PerimeterConstruction.confirm(player,stack,selection->{fail("Foreign plan prepared");return p;},(prepared,material)->{fail("Foreign plan started");return true;}));
    }
    @Test void oldSavedPlanAlwaysPreparesFreshAndCanOnlyStartOnce() throws Exception {
        var player=player(24_000_000);var p=ready();
        var stack=ItemStack.of(preview(p,hash(p)).save(new net.minecraft.nbt.CompoundTag()));
        var preparations=new java.util.concurrent.atomic.AtomicInteger();
        var starts=new java.util.concurrent.atomic.AtomicInteger();
        assertTrue(PerimeterConstruction.confirm(player,stack,selection->{preparations.incrementAndGet();return p;},
                (prepared,material)->{starts.incrementAndGet();return true;}));
        assertEquals(1,preparations.get()); assertEquals(1,starts.get());
        assertFalse(PerimeterConstruction.confirm(player,stack,selection->{fail("Spent plan prepared twice");return p;},
                (prepared,material)->{fail("Spent plan started twice");return true;}));
    }
    @Test void oldPlanWithChangedQuoteRefreshesAndWaitsForAnotherDeliberateUse() throws Exception {
        var player=player(24_000_000);var p=ready();var stack=preview(p,"0".repeat(64));
        var starts=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BiPredicate<PerimeterConstruction.Preparation,Integer> start=(prepared,material)->{starts.incrementAndGet();return true;};
        assertFalse(PerimeterConstruction.confirm(player,stack,selection->p,start));
        var refreshed=PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),24_000_000);
        assertNotNull(refreshed); assertEquals(hash(p),refreshed.fingerprint()); assertFalse(refreshed.canConfirm(24_000_000));
        assertFalse(PerimeterConstruction.confirm(player,stack,selection->{fail("Fresh confirmation delay bypassed");return p;},start));
        assertEquals(0,starts.get()); assertEquals(1,stack.getCount());
        when(player.level().getGameTime()).thenReturn(24_000_010L);
        assertTrue(PerimeterConstruction.confirm(player,stack,selection->p,start)); assertEquals(1,starts.get());
    }
    @Test void oldBlockedPlanRetainsItsItemAndNeverHandsOff() throws Exception {
        var player=player(24_000_000);var p=ready();var stack=preview(p,hash(p));
        var blocked=PerimeterConstruction.Preparation.failed("Your faction no longer owns this claim.");
        assertFalse(PerimeterConstruction.confirm(player,stack,selection->blocked,
                (prepared,material)->{fail("Unauthorized stale plan started");return true;}));
        assertEquals(1,stack.getCount());
        var refreshed=PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),24_000_000);
        assertNotNull(refreshed); assertFalse(refreshed.ready());
        assertEquals(blocked.problem(),refreshed.problem());
    }
}
