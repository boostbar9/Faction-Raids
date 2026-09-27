package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Saved supply identity, explicit activation, and bounded server-side blessings. */
@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID)
public final class OlympianSupplyPowers {
    static final String POWER = "SiegeSupplyPower";
    static final String NEXT = "SiegeSupplyNext";
    static final int COOLDOWN = 200;
    private OlympianSupplyPowers() {}

    record Buff(MobEffect effect, int seconds, int amplifier) {
        MobEffectInstance create() { return new MobEffectInstance(effect, seconds * 20, amplifier); }
        String description() {
            return Component.translatable(effect.getDescriptionId()).getString() + " "
                    + (amplifier == 0 ? "I" : "II") + " for " + seconds + "s";
        }
    }
    enum Power {
        MOON_SIGHT(8, new Item[]{Items.ARROW, Items.SPECTRAL_ARROW}, buff(MobEffects.NIGHT_VISION,60,0)),
        INSIGHT(1, new Item[]{Items.EXPERIENCE_BOTTLE}, buff(MobEffects.DIG_SPEED,30,1)),
        FORGE_WARD(4, new Item[]{Items.IRON_INGOT,Items.IRON_BLOCK}, buff(MobEffects.DAMAGE_RESISTANCE,15,0)),
        RAMPART(8, new Item[]{Items.STONE_BRICKS}, buff(MobEffects.ABSORPTION,30,0)),
        DAWN(1, new Item[]{Items.SPLASH_POTION}, buff(MobEffects.REGENERATION,4,1)),
        ROAD(1, new Item[]{Items.POTION}, buff(MobEffects.MOVEMENT_SPEED,10,1),buff(MobEffects.JUMP,10,0)),
        HUNT(4, new Item[]{Items.TIPPED_ARROW}, buff(MobEffects.NIGHT_VISION,30,0),buff(MobEffects.MOVEMENT_SPEED,15,0)),
        TRIBUTE(1, new Item[]{Items.GOLD_INGOT,Items.DIAMOND}, buff(MobEffects.LUCK,120,1)),
        FURNACE(1, new Item[]{Items.POTION}, buff(MobEffects.FIRE_RESISTANCE,60,0),buff(MobEffects.DAMAGE_RESISTANCE,8,0)),
        DEEP(1, new Item[]{Items.POTION}, buff(MobEffects.WATER_BREATHING,30,0),buff(MobEffects.DOLPHINS_GRACE,30,0)),
        PLATFORM(4, new Item[]{Items.SCAFFOLDING}, buff(MobEffects.SLOW_FALLING,30,0)),
        ASCENT(4, new Item[]{Items.LADDER}, buff(MobEffects.JUMP,30,1),buff(MobEffects.SLOW_FALLING,10,0)),
        HARVEST(1, new Item[]{Items.GOLDEN_CARROT}, buff(MobEffects.REGENERATION,5,0)),
        ORCHARD(1, new Item[]{Items.GOLDEN_APPLE}, buff(MobEffects.DAMAGE_RESISTANCE,20,0),buff(MobEffects.MOVEMENT_SPEED,20,0)),
        AMBROSIA(1, new Item[]{Items.ENCHANTED_GOLDEN_APPLE}, buff(MobEffects.DAMAGE_BOOST,30,0),buff(MobEffects.REGENERATION,10,1));
        final int cost;
        final Item[] items;
        final List<Buff> buffs;
        Power(int cost, Item[] items, Buff... buffs) { this.cost=cost; this.items=items; this.buffs=List.of(buffs); }
        int cost(ItemStack stack) { return this==FORGE_WARD && stack.is(Items.IRON_BLOCK) ? 1 : cost; }
        boolean accepts(ItemStack stack) { return Arrays.stream(items).anyMatch(stack::is); }
    }
    private static Buff buff(MobEffect effect,int seconds,int amplifier) { return new Buff(effect,seconds,amplifier); }

    static ItemStack imbue(ItemStack stack,Power power) {
        stack.getOrCreateTag().putString(POWER,power.name());
        var display=stack.getOrCreateTagElement("display");
        var lore=display.getList("Lore",Tag.TAG_STRING);
        line(lore,"Sneak-use in air: spend " + power.cost(stack) + " for:",ChatFormatting.GOLD);
        for(var buff:power.buffs)line(lore,buff.description(),ChatFormatting.AQUA);
        if(power==Power.TRIBUTE)line(lore,"Luck improves eligible fishing treasure rolls, not mob loot.",ChatFormatting.GRAY);
        line(lore,"10s shared supply cooldown. Normal use stays vanilla.",ChatFormatting.DARK_GRAY);
        display.put("Lore",lore);
        return stack;
    }
    private static void line(ListTag lore,String text,ChatFormatting color) {
        lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(text).withStyle(color))));
    }
    static Power power(ItemStack stack) {
        if(stack.isEmpty() || !stack.hasTag())return null;
        try {
            var power=Power.valueOf(stack.getTag().getString(POWER));
            return power.accepts(stack)?power:null;
        } catch(IllegalArgumentException ignored) { return null; }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.RightClickItem event) {
        var result=activate(event.getLevel(),event.getEntity(),event.getItemStack());
        if(result!=InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
    static InteractionResult activate(Level level,Player player,ItemStack stack) {
        var power=power(stack);
        if(power==null || !player.isShiftKeyDown())return InteractionResult.PASS;
        if(player.isSpectator() || !player.isAlive())return InteractionResult.FAIL;
        if(!(level instanceof ServerLevel server))return InteractionResult.SUCCESS;
        long now=server.getGameTime(),next=player.getPersistentData().getLong(NEXT);
        if(next>now && next<=now+COOLDOWN) {
            player.displayClientMessage(Component.literal("Supply blessing ready in " + ((next-now+19)/20) + "s."),true);
            return InteractionResult.FAIL;
        }
        int cost=power.cost(stack);
        if(!player.getAbilities().instabuild && stack.getCount()<cost) {
            player.displayClientMessage(Component.literal("This blessing needs " + cost + " items."),true);
            return InteractionResult.FAIL;
        }
        boolean applied=false;
        for(var buff:power.buffs) {
            var current=player.getEffect(buff.effect());
            // Never replace a stronger buff, or spend supplies refreshing an equal longer buff.
            if(current!=null && (current.getAmplifier()>buff.amplifier()
                    || current.getAmplifier()==buff.amplifier() && (current.isInfiniteDuration() || current.getDuration()>=buff.seconds()*20)))continue;
            applied |= player.addEffect(buff.create());
        }
        if(!applied) {
            player.displayClientMessage(Component.literal("No supplies spent: these blessings are already active or blocked."),true);
            return InteractionResult.FAIL;
        }
        player.getPersistentData().putLong(NEXT,now+COOLDOWN);
        if(!player.getAbilities().instabuild)stack.shrink(cost);
        player.getInventory().setChanged();
        server.sendParticles(ParticleTypes.ENCHANT,player.getX(),player.getY()+1,player.getZ(),12,.4,.4,.4,.02);
        server.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.5F,1.1F);
        return InteractionResult.CONSUME;
    }
}
