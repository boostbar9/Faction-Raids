package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge discovery stays separate from the plain, independently testable guard policy. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID)
public final class NativeConstructionEvents {
    private NativeConstructionEvents() {}
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void joined(EntityJoinLevelEvent event) { NativeConstructionGuard.areaJoined(event); }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void placed(BlockEvent.EntityPlaceEvent event) { NativeConstructionGuard.blockPlaced(event); }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void broken(BlockEvent.BreakEvent event) { NativeConstructionGuard.blockBroken(event); }
    @SubscribeEvent
    public static void removed(EntityLeaveLevelEvent event) { NativeConstructionGuard.areaRemoved(event); }
}
