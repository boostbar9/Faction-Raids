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

/**
 * A Siege Overhaul spawn egg for a specific unit role.
 *
 * <p>Not a real vanilla {@link net.minecraft.world.item.SpawnEggItem}. That
 * class hard-binds to a single {@link EntityType}, which would force us to
 * register 24 distinct entity types just to give each role its own egg. Our
 * roles all share the same handful of underlying Recruits entity types
 * (recruit / shieldman / bowman / crossbowman) plus one wizard role for
 * mage heroes; what changes per egg is the equipment, name and stats we
 * apply after spawn.
 *
 * <p>On use, the egg:
 * <ol>
 *   <li>Resolves the correct Recruits entity type via the role's
 *       {@link CoreHiring#heroBase(int)} mapping.</li>
 *   <li>Spawns the entity one block above the clicked face.</li>
 *   <li>Runs {@link RecruitPersonality#prepare} for a plain recruit role
 *       (0-3) or {@link CoreHiring#prepareHero}-equivalent inline for a
 *       hero role (10-29) so the unit gets its rarity gear, name and
 *       stat bump.</li>
 *   <li>Consumes one from the stack in survival.</li>
 * </ol>
 *
 * <p>Missing Recruits (mod not loaded, or entity type not registered) is a
 * clean no-op: the item stays in the player's hand, and a chat message
 * explains why nothing spawned.
 */
public final class UnitSpawnEggItem extends Item {

    /** Role id as used by {@link CoreHiring} (0-3 for recruits, 10-29 for heroes). */
    private final int role;

    public UnitSpawnEggItem(int role, Rarity rarity) {
        super(new Item.Properties().stacksTo(16).rarity(rarity));
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
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                new ResourceLocation(ns, CoreHiring.IDS[typeRole]));
        if (type == null) {
            if (ctx.getPlayer() != null) {
                ctx.getPlayer().sendSystemMessage(Component.literal(
                        "Villager Recruits isn't installed, so this spawn egg has nothing to spawn."
                ).withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }

        // Spawn one block above the clicked face. This matches vanilla
        // spawn-egg placement; if that block is solid, fall back to the
        // clicked block itself.
        Direction face = ctx.getClickedFace();
        BlockPos pos = ctx.getClickedPos().relative(face);
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            pos = ctx.getClickedPos();
        }

        Mob mob;
        try {
            if (!(type.create(server) instanceof Mob created)) {
                return InteractionResult.FAIL;
            }
            mob = created;
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    ctx.getPlayer() == null ? 0f : ctx.getPlayer().getYRot(), 0f);
            mob.finalizeSpawn(server, server.getCurrentDifficultyAt(pos),
                    MobSpawnType.SPAWN_EGG, null, null);
            server.addFreshEntity(mob);

            equip(mob);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (ctx.getPlayer() != null) {
                ctx.getPlayer().sendSystemMessage(Component.literal(
                        "Couldn't outfit that unit: " + e.getClass().getSimpleName()
                ).withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }

        ItemStack held = ctx.getItemInHand();
        if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) {
            held.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Run the same outfit + hero-prep code paths that {@link CoreHiring#hire}
     * runs, minus the treasury / faction / cost checks. This egg is a
     * creative-mode debug tool, so it always succeeds.
     */
    private void equip(Mob mob) throws ReflectiveOperationException {
        Object inv = mob.getClass().getMethod("getInventory").invoke(mob);
        if (!(inv instanceof SimpleContainer container)) {
            throw new IllegalStateException("Recruit inventory missing");
        }

        if (CoreHiring.isHero(role)) {
            // Mirror CoreHiring.prepareHero without invoking it directly
            // (that method is package-private inside core). Same effects:
            // xp level 10, HP floor 60, +4 attack, hero-tier gear via
            // HeroTraits.equip, bread + arrows for ranged tiers, marker
            // flag so downstream code recognizes the hero.
            mob.getClass().getMethod("setXpLevel", int.class).invoke(mob, 10);
            var health = mob.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (health != null) health.setBaseValue(Math.max(health.getBaseValue(), 60));
            mob.setHealth(mob.getMaxHealth());
            var attack = mob.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (attack != null) attack.setBaseValue(attack.getBaseValue() + 4);
            HeroTraits.equip(mob, role, container);
            container.addItem(new ItemStack(net.minecraft.world.item.Items.BREAD, 32));
            if (role >= 12) {
                container.addItem(new ItemStack(net.minecraft.world.item.Items.ARROW, 64));
            }
            mob.getPersistentData().putBoolean("SiegeHiredHero", true);
            mob.setCustomName(Component.literal(CoreHiring.NAMES[role])
                    .withStyle(tierColor(CoreHiring.heroTier(role))));
            mob.setCustomNameVisible(false);
        } else {
            // Plain recruit role 0-3: give it the standard trimmed armor,
            // matching weapon and (for shieldman) a shield. RecruitPersonality
            // also gives it a random first name.
            RecruitPersonality.prepare(mob, role, container);
        }
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
        tooltip.add(Component.literal("Right-click to spawn.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
