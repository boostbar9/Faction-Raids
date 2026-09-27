package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.items.ModItems;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import java.util.UUID;

/** Loaded from common setup only when the optional Curios mod is present. */
public final class CuriosCompat {
    private CuriosCompat() {}
    public static void register() {
        CuriosApi.registerCurio(ModItems.FORGE_EMBER.get(),new Relic(Attributes.ARMOR_TOUGHNESS,1));
        CuriosApi.registerCurio(ModItems.OWL_SEAL.get(),new Relic(Attributes.ARMOR,2));
        CuriosApi.registerCurio(ModItems.SUN_LAUREL.get(),new Relic(Attributes.MAX_HEALTH,2));
    }
    static final class Relic implements ICurioItem {
        private final Attribute attribute;
        private final double amount;
        Relic(Attribute attribute,double amount) { this.attribute=attribute;this.amount=amount; }
        @Override public Multimap<Attribute,AttributeModifier> getAttributeModifiers(SlotContext context,UUID uuid,ItemStack stack) {
            if(context.cosmetic() || stack.isEmpty())return ImmutableMultimap.of();
            return ImmutableMultimap.of(attribute,new AttributeModifier(uuid,"Olympian relic",amount,AttributeModifier.Operation.ADDITION));
        }
        // Manual Curios inventory placement avoids taking over the relic's normal consumable action.
        @Override public boolean canEquipFromUse(SlotContext context,ItemStack stack) { return false; }
    }
}
