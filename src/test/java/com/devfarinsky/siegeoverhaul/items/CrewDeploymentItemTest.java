package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrewDeploymentItemTest extends MinecraftTestSupport {
    @Test
    void targetsTheBlockImmediatelyBeyondTheClickedFace() {
        BlockPos clicked = new BlockPos(10, 64, -4);
        assertEquals(new BlockPos(10, 65, -4),
                CrewDeploymentItem.deploymentCenter(clicked, Direction.UP));
        assertEquals(new BlockPos(11, 64, -4),
                CrewDeploymentItem.deploymentCenter(clicked, Direction.EAST));
    }
}
