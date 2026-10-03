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
        var player=player(120);var p=ready();var stack=preview(p,hash(p));
        try(var construction=mockStatic(PerimeterConstruction.class,CALLS_REAL_METHODS)) {
            construction.when(()->PerimeterConstruction.prepare(player,core,1)).thenReturn(p);
            construction.when(()->PerimeterConstruction.startJob(player,p,1)).thenReturn(true);
            assertTrue(PerimeterConstruction.confirm(player,stack));assertTrue(stack.isEmpty());
            assertFalse(PerimeterConstruction.confirm(player,stack));construction.verify(()->PerimeterConstruction.startJob(player,p,1),times(1));
        }
    }
    @Test void changedQuoteRefreshesBeforeAnyHandoff() throws Exception {
        var player=player(120);var p=ready();var stack=preview(p,"0".repeat(64));
        try(var construction=mockStatic(PerimeterConstruction.class,CALLS_REAL_METHODS)) {
            construction.when(()->PerimeterConstruction.prepare(player,core,1)).thenReturn(p);
            assertFalse(PerimeterConstruction.confirm(player,stack));assertEquals(1,stack.getCount());
            construction.verify(()->PerimeterConstruction.startJob(player,p,1),never());
            var refreshed=PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),120);
            assertEquals(hash(p),refreshed.fingerprint());assertFalse(refreshed.canConfirm(120));
        }
    }
    @Test void failedHandoffRetainsThePlanAndExpiredOrCanceledPlansCannotStart() throws Exception {
        var player=player(120);var p=ready();var stack=preview(p,hash(p));
        try(var construction=mockStatic(PerimeterConstruction.class,CALLS_REAL_METHODS)) {
            construction.when(()->PerimeterConstruction.prepare(player,core,1)).thenReturn(p);
            construction.when(()->PerimeterConstruction.startJob(player,p,1)).thenReturn(false);
            assertFalse(PerimeterConstruction.confirm(player,stack));assertEquals(1,stack.getCount());
            assertNotNull(PerimeterPreview.read(stack,owner,Level.OVERWORLD.location(),120));
            PerimeterPreview.clear(stack);assertFalse(PerimeterConstruction.confirm(player,stack));
            construction.verify(()->PerimeterConstruction.startJob(player,p,1),times(1));
        }
    }
}
