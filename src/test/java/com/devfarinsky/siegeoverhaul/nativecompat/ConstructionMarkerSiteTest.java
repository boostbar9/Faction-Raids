package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConstructionMarkerSiteTest extends MinecraftTestSupport {
    private final ServerLevel level = mock(ServerLevel.class);

    @Test void walkThroughNaturalCoverIsClearedInsteadOfRefusingTheMarker() {
        for (var state : List.of(Blocks.GRASS.defaultBlockState(), Blocks.TALL_GRASS.defaultBlockState(),
                Blocks.DANDELION.defaultBlockState(), Blocks.FERN.defaultBlockState(), Blocks.SNOW.defaultBlockState()))
            assertTrue(ConstructionMarkerSite.clearableCover(level, BlockPos.ZERO, state), state.toString());
    }

    @Test void solidHazardousOrPlayerBlocksStillRefuseTheMarker() {
        for (var state : List.of(Blocks.SWEET_BERRY_BUSH.defaultBlockState(), Blocks.WITHER_ROSE.defaultBlockState(),
                Blocks.CACTUS.defaultBlockState(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4),
                Blocks.OAK_LEAVES.defaultBlockState(), Blocks.TORCH.defaultBlockState(), Blocks.WHEAT.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()))
            assertFalse(ConstructionMarkerSite.clearableCover(level, BlockPos.ZERO, state), state.toString());
    }

    @Test void theCommissioningBuilderAndOwnedMobsDoNotBlockButOthersAndTheOwnerDo() {
        UUID id = UUID.randomUUID();
        var owner = mock(ServerPlayer.class); when(owner.getUUID()).thenReturn(id);
        var builder = mock(Mob.class), stranger = mock(Mob.class);
        var pet = mock(Wolf.class); when(pet.getOwnerUUID()).thenReturn(id);
        var otherPet = mock(Wolf.class); when(otherPet.getOwnerUUID()).thenReturn(UUID.randomUUID());
        assertTrue(ConstructionMarkerSite.ownedBy(builder, owner, builder));
        assertTrue(ConstructionMarkerSite.ownedBy(pet, owner, builder));
        assertFalse(ConstructionMarkerSite.ownedBy(stranger, owner, builder));
        assertFalse(ConstructionMarkerSite.ownedBy(otherPet, owner, builder));
        assertFalse(ConstructionMarkerSite.ownedBy(owner, owner, builder), "the owner still has to step off the shovel spot");
    }
}
