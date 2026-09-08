package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.camp.CampGuards;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Reject enemy hiring before native ownership/currency/unit-count mutations. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class EnemyHiringProtection {
    private static boolean installed;
    private EnemyHiringProtection() {}
    public static boolean enemy(Entity entity) {
        var tag=entity.getPersistentData();
        if(!tag.getString(ModConstants.Tags.RAID_TEAM).isBlank()
                || !tag.getString(ModConstants.Tags.CAMP_WORKER_TEAM).isBlank()
                || !tag.getString(CampGuards.TEAM_TAG).isBlank()) return true;
        if(entity.getTeam()==null)return false;
        String team=entity.getTeam().getName();
        if(team.equals(RecruitsBridge.RAIDERS_FACTION_ID))return true;
        for(var faction:com.devfarinsky.siegeoverhaul.items.FactionBanners.FactionId.values())
            if(team.equals("siegeoverhaul_"+faction.id))return true;
        return false;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if(!enemy(event.getTarget()))return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        if(!event.getLevel().isClientSide)event.getEntity().displayClientMessage(
                net.minecraft.network.chat.Component.literal("Enemy faction units cannot be hired or commanded."),true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void join(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide && event.getEntity() instanceof Mob mob && enemy(mob)
                && mob.getPersistentData().getString(ModConstants.Tags.CAMP_WORKER_TEAM).isBlank())
            RecruitsBridge.assignToRaidersFaction(mob);
    }
    @SubscribeEvent
    @SuppressWarnings({"unchecked","rawtypes"})
    public static synchronized void start(ServerStartedEvent ignored) {
        if(installed)return;
        try {
            Class<?> type=Class.forName("com.talhanation.recruits.RecruitEvent$Hired");
            var getter=type.getMethod("getRecruit");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,(Class)type,(java.util.function.Consumer)(Object raw)->{
                try {
                    if(getter.invoke(raw) instanceof Entity entity && enemy(entity)) ((Event)raw).setCanceled(true);
                } catch(ReflectiveOperationException ex) {
                    // An incompatible hire event must not bypass the server-side safeguard.
                    ((Event)raw).setCanceled(true);
                    FactionLogger.LOG.error("Could not validate native recruit hire",ex);
                }
            });
            installed=true;
        } catch(ReflectiveOperationException | LinkageError ex) {
            FactionLogger.LOG.error("Native hire-event protection unavailable; enemy ownership and interaction protection remain active",ex);
        }
    }
}
