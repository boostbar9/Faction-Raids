package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraftforge.registries.*;

public final class CoreMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SiegeOverhaul.MOD_ID);
    public static final RegistryObject<MenuType<CoreHireMenu>> HIRING = MENUS.register("core_hiring",
            () -> new MenuType<>(CoreHireMenu::new, FeatureFlags.DEFAULT_FLAGS));
    private CoreMenus() {}
}
