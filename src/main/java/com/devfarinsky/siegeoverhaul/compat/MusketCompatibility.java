package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.function.Function;

/** Uses the existing Recruits crossbowman musket AI only when its expected API is present. */
public final class MusketCompatibility {
    public record Kit(Item weapon,Item ammunition) {}
    private MusketCompatibility() {}
    public static Kit kit() {
        Item weapon=ForgeRegistries.ITEMS.getValue(new ResourceLocation("musketmod","musket"));
        if(weapon==null || weapon==Items.AIR)return null;
        return kit(ForgeRegistries.ITEMS::getValue,apiReady(weapon));
    }
    static Kit kit(Function<ResourceLocation,Item> lookup,boolean apiReady) {
        if(!apiReady)return null;
        Item weapon=lookup.apply(new ResourceLocation("musketmod","musket"));
        Item ammo=lookup.apply(new ResourceLocation("musketmod","cartridge"));
        return weapon==null || ammo==null || weapon==Items.AIR || ammo==Items.AIR ? null : new Kit(weapon,ammo);
    }
    private static boolean apiReady(Item weapon) {
        try {
            if(!Class.forName("com.talhanation.recruits.Main").getField("isMusketModLoaded").getBoolean(null))return false;
            Class<?> items=Class.forName("ewewukek.musketmod.Items");
            items.getConstructor(); // Native Recruits bridge instantiates this holder.
            if(items.getField("MUSKET").get(null)!=weapon)return false;
            Class<?> gun=Class.forName("ewewukek.musketmod.MusketItem");
            gun.getMethod("isLoaded",ItemStack.class);
            gun.getMethod("setLoaded",ItemStack.class,boolean.class);
            Class<?> bullet=Class.forName("ewewukek.musketmod.BulletEntity");
            if(!AbstractHurtingProjectile.class.isAssignableFrom(bullet) || bullet.getField("damage").getType()!=float.class)return false;
            bullet.getConstructor(Level.class);bullet.getMethod("setInitialSpeed",float.class);
            Class<?> sounds=Class.forName("ewewukek.musketmod.Sounds");sounds.getConstructor();
            if(!(sounds.getField("MUSKET_FIRE").get(null) instanceof net.minecraft.sounds.SoundEvent)
                    || !(sounds.getField("MUSKET_READY").get(null) instanceof net.minecraft.sounds.SoundEvent))return false;
            Class.forName("ewewukek.musketmod.MusketMod").getMethod("sendSmokeEffect",ServerLevel.class,Vec3.class,Vec3.class);
            return true;
        } catch(ReflectiveOperationException | LinkageError | RuntimeException ex) {
            FactionLogger.LOG.debug("Optional musket hire unavailable; retaining crossbow",ex);return false;
        }
    }
}
