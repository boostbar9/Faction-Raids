package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gear supplied with a soldier is not a renewable source of crafted armor. */
@Mod.EventBusSubscriber(modid=SiegeOverhaul.MOD_ID)
public final class IssuedEquipment {
    private static final String TAG = "SiegeIssuedEquipment";
    private IssuedEquipment() {}

    public static ItemStack issue(ItemStack stack) {
        if (!stack.isEmpty()) stack.getOrCreateTag().putBoolean(TAG, true);
        return stack;
    }

    public static void clear(SimpleContainer inventory) {
        for (int i=0; i<inventory.getContainerSize(); i++)
            if (inventory.getItem(i).hasTag() && inventory.getItem(i).getTag().getBoolean(TAG))
                inventory.setItem(i, ItemStack.EMPTY);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        var data=mob.getPersistentData();
        if (!data.getBoolean("SiegeCoreOutfitted") && !data.contains("SiegeHeroRole")
                && !data.getBoolean("SiegeUniformApplied")) return;
        // Recruits drops its whole native inventory after super.die(), including
        // equipment whose vanilla slot drop chance is zero. Clear only our
        // tagged issue; a player's replacement equipment remains recoverable.
        try {
            Object value=mob.getClass().getMethod("getInventory").invoke(mob);
            if (value instanceof SimpleContainer inventory) clear(inventory);
        } catch (ReflectiveOperationException ignored) { return; }
        for (EquipmentSlot slot: EquipmentSlot.values()) {
            ItemStack stack=mob.getItemBySlot(slot);
            if (stack.hasTag() && stack.getTag().getBoolean(TAG)) mob.setItemSlot(slot,ItemStack.EMPTY);
        }
    }
}
