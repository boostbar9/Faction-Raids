package com.devfarinsky.siegeoverhaul.naval.prototype;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** Hard QA opt-in plus exact native versions; never included in production JAR. */
public final class FinalShipsPrototypePlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!Boolean.getBoolean("siegeoverhaul.nativeShipsQa.adapter")) return false;
        var list = FMLLoader.getLoadingModList();
        if (list == null) return false;
        return list.getMods().stream().anyMatch(mod -> mod.getModId().equals("smallships") && mod.getVersion().toString().equals("2.0.0"))
                && list.getMods().stream().anyMatch(mod -> mod.getModId().equals("recruits") && mod.getVersion().toString().equals("1.15.2"));
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
