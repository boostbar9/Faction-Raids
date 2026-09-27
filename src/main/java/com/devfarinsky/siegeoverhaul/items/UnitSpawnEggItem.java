package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.core.CoreHiring;
import com.devfarinsky.siegeoverhaul.core.CoreOffers;
import com.devfarinsky.siegeoverhaul.core.HeroTraits;
import com.devfarinsky.siegeoverhaul.core.RecruitPersonality;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** Role-specific native Recruits egg. Outfitting completes before the entity enters the world. */
public final class UnitSpawnEggItem extends Item {

    /** Role id as used by {@link CoreHiring} (0-3 for recruits, 10-29 for heroes). */
    private final int role;

    public UnitSpawnEggItem(int role, Rarity rarity) {
        super(new Item.Properties().stacksTo(16).rarity(rarity));
        if (!(role >= 0 && role <= 3) && !CoreHiring.isHero(role)) throw new IllegalArgumentException("Unknown unit role");
        this.role = role;
    }

    public int role() {
        return role;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.PASS;
        if (role < 0 || role >= CoreHiring.NAMES.length) return InteractionResult.FAIL;

        int typeRole = CoreHiring.isHero(role) ? CoreHiring.heroBase(role) : role;

        // Recruits (roles 0-3) live in the "recruits" namespace. Workers
        // (4-9) live in "workers" but we don't ship worker eggs from this
        // tab; guard anyway so a future role change doesn't silently spawn
        // a worker entity for a hero base.
        String ns = typeRole < CoreOffers.WORKER_START ? "recruits" : "workers";
        var typeId = new ResourceLocation(ns, CoreHiring.IDS[typeRole]);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.containsKey(typeId)
                ? ForgeRegistries.ENTITY_TYPES.getValue(typeId) : null;
        if (type == null) {
            if (ctx.getPlayer() != null) {
                ctx.getPlayer().sendSystemMessage(Component.literal(
                        "The matching Villager Recruits unit type is unavailable."
                ).withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }

        return spawn(server, ctx, type);
    }

    InteractionResult spawn(ServerLevel server, UseOnContext ctx, EntityType<?> type) {
        BlockPos clicked = ctx.getClickedPos();
        BlockPos pos = server.getBlockState(clicked).getCollisionShape(server, clicked).isEmpty()
                ? clicked : clicked.relative(ctx.getClickedFace());
        Mob mob = null;
        try {
            if (!server.hasChunkAt(pos) || !server.getWorldBorder().isWithinBounds(pos)
                    || server.isOutsideBuildHeight(pos) || server.isOutsideBuildHeight(pos.above()))
                return InteractionResult.FAIL;
            if (!(type.create(server) instanceof Mob created)) return InteractionResult.FAIL;
            mob = created;
            mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5,
                    ctx.getPlayer() == null ? 0 : ctx.getPlayer().getYRot(), 0);
            if (!server.noCollision(mob) || !server.getEntities(mob, mob.getBoundingBox()).isEmpty()) {
                mob.discard();
                if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(
                        Component.literal("Clear a little more room for this unit."), true);
                return InteractionResult.FAIL;
            }
            mob.finalizeSpawn(server, server.getCurrentDifficultyAt(pos), MobSpawnType.SPAWN_EGG, null, null);
            if (CoreHiring.isHero(role)) CoreHiring.prepareHero(mob, role, true);
            else {
                Object inventory = mob.getClass().getMethod("getInventory").invoke(mob);
                if (!(inventory instanceof SimpleContainer container)) throw new IllegalStateException("Recruit inventory missing");
                RecruitPersonality.prepare(mob, role, container);
            }
            mob.setPersistenceRequired();
            if (!server.noCollision(mob) || !server.addFreshEntity(mob)) {
                mob.discard();
                return InteractionResult.FAIL;
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (mob != null) mob.discard();
            com.devfarinsky.siegeoverhaul.FactionLogger.LOG.warn("Could not spawn unit role {}", role, ex);
            if (ctx.getPlayer() != null) ctx.getPlayer().sendSystemMessage(
                    Component.literal("That unit could not be prepared. Your egg was kept.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    private static ChatFormatting tierColor(int tier) {
        return switch (tier) {
            case 0 -> ChatFormatting.WHITE;
            case 1 -> ChatFormatting.GREEN;
            case 2 -> ChatFormatting.BLUE;
            case 3 -> ChatFormatting.LIGHT_PURPLE;
            case 4 -> ChatFormatting.GOLD;
            default -> ChatFormatting.GRAY;
        };
    }

    @Override
    public Component getName(ItemStack stack) {
        if (role < 0 || role >= CoreHiring.NAMES.length) return super.getName(stack);
        return Component.literal(CoreHiring.NAMES[role] + " Spawn Egg");
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (role < 0 || role >= CoreHiring.NAMES.length) return;
        String rarityLabel = CoreHiring.isHero(role)
                ? CoreHiring.RARITY_NAMES[CoreHiring.heroTier(role)] + " Hero"
                : "Recruit";
        tooltip.add(Component.literal(rarityLabel)
                .withStyle(CoreHiring.isHero(role)
                        ? tierColor(CoreHiring.heroTier(role))
                        : ChatFormatting.GRAY));
        tooltip.add(Component.literal("Spawns an outfitted unit. Hire it through its usual recruit menu.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
