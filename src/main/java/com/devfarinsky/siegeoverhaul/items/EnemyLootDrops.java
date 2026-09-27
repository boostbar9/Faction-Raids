package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Small bonus for tracked, reward-eligible siege combat, independent of wave rewards. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class EnemyLootDrops {
    static final String PENDING="SiegeLootDropEligible";
    private EnemyLootDrops() {}

    /** Called only after RaidEvents successfully removes the victim from the active raider set. */
    public static void mark(LivingEntity victim,Entity killer,boolean earnedKill) {
        victim.getPersistentData().remove(PENDING);
        if(earnedKill && victim instanceof Mob && killer!=null
                && !(killer instanceof Player player && (player.isSpectator() || player.getAbilities().instabuild)))
            victim.getPersistentData().putBoolean(PENDING,true);
    }
    static LootBoxItem.Tier roll(RandomSource random) {
        int roll=random.nextInt(200);
        return roll<4?LootBoxItem.Tier.COMMON:roll==4?LootBoxItem.Tier.UNCOMMON:null;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void drops(LivingDropsEvent event) {
        var victim=event.getEntity();
        if(!(victim.level() instanceof ServerLevel level))return;
        var tag=victim.getPersistentData();
        boolean eligible=tag.getBoolean(PENDING);
        tag.remove(PENDING); // Consume the opportunity even for a canceled drop event; never roll twice.
        if(!eligible || event.isCanceled() || !(victim instanceof Mob)
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT))return;
        var tier=roll(level.getRandom());
        if(tier==null)return;
        var item=new ItemEntity(level,victim.getX(),victim.getY()+.5,victim.getZ(),EnemyLootBoxes.stack(tier));
        item.setDefaultPickUpDelay();
        event.getDrops().add(item);
    }
}
