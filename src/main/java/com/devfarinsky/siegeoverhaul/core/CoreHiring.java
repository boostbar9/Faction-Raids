package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;

/** Uses native costs, currency, hiring events, ownership, faction and unit-limit checks. */
public final class CoreHiring {
    public static final String[] IDS = {"recruit", "shieldman", "bowman"};
    public static final String[] NAMES = {"Recruit", "Shieldman", "Archer"};
    private static final String[] COSTS = {"RecruitCost", "ShieldmanCost", "BowmanCost"};
    private CoreHiring() {}
    private static Object config(String name) throws ReflectiveOperationException {
        return ((ForgeConfigSpec.ConfigValue<?>) Class.forName("com.talhanation.recruits.config.RecruitsServerConfig").getField(name).get(null)).get();
    }
    public static int cost(int role) throws ReflectiveOperationException { return (Integer) config(COSTS[role]); }
    public static Item currency() throws ReflectiveOperationException {
        ResourceLocation id = ResourceLocation.tryParse((String) config("RecruitCurrency"));
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null || item == Items.AIR ? Items.EMERALD : item;
    }
    public static boolean hire(ServerPlayer player, BlockPos core, int role) {
        Mob mob = null;
        try {
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("recruits", IDS[role]));
            if (type == null || !(type.create(player.serverLevel()) instanceof Mob recruit)) return false;
            mob = recruit;
            boolean found = false;
            for (int radius = 2; radius <= 4 && !found; radius++) {
                for (int dx = -radius; dx <= radius && !found; dx++) for (int dz = -radius; dz <= radius && !found; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    for (int dy = -1; dy <= 1 && !found; dy++) {
                        BlockPos pos = core.offset(dx, dy, dz);
                        var level = player.serverLevel();
                        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                                || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()
                                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) continue;
                        recruit.moveTo(pos.getX()+.5, pos.getY(), pos.getZ()+.5, player.getYRot(), 0);
                        found = level.noCollision(recruit) && level.getEntities(recruit, recruit.getBoundingBox()).isEmpty();
                    }
                }
            }
            if (!found) { recruit.discard(); player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Clear a safe space beside the core for your recruit.")); return false; }
            recruit.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(recruit.blockPosition()), MobSpawnType.EVENT, null, null);
            int price = (Integer) recruit.getClass().getMethod("getCost").invoke(recruit);
            Item currency = currency();
            int available = player.getInventory().countItem(currency);
            if (!player.isCreative() && available < price) {
                recruit.discard();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You need " + price + " ").append(currency.getDescription()));
                return false;
            }
            Class<?> group = Class.forName("com.talhanation.recruits.world.RecruitsGroup");
            var hire = recruit.getClass().getMethod("hire", Player.class, group, boolean.class);
            var faction = Class.forName("com.talhanation.recruits.FactionEvents").getMethod("addNPCToData", net.minecraft.server.level.ServerLevel.class, String.class, int.class);
            recruit.setPersistenceRequired();
            if (!player.serverLevel().addFreshEntity(recruit)) { recruit.discard(); return false; }
            if (!Boolean.TRUE.equals(hire.invoke(recruit, player, null, true))) { recruit.discard(); return false; }
            // Remove only the price, preserving all other stacks and their NBT.
            if (!player.isCreative()) {
                int remaining = price;
                for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.is(currency)) { int take = Math.min(remaining, stack.getCount()); stack.shrink(take); remaining -= take; }
                }
                player.getInventory().setChanged();
            }
            // Hire already assigned owner, scoreboard team and the player's unit count.
            try { faction.invoke(null, player.serverLevel(), player.getTeam().getName(), 1); }
            catch (ReflectiveOperationException ex) { FactionLogger.LOG.warn("Core hire faction statistics could not update", ex); }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (mob != null) mob.discard();
            FactionLogger.LOG.warn("Siege Core hiring unavailable", ex);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Recruit hiring is unavailable; check the server log."));
            return false;
        }
    }
}
