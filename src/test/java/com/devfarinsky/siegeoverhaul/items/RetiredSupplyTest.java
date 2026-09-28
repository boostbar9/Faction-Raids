package com.devfarinsky.siegeoverhaul.items;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RetiredSupplyTest extends MinecraftTestSupport {
    @Test void newBoxesAndCreativeCatalogExcludeRetiredMaterialRewards() {
        try(var registry=new OlympianRelicRegistryFixture()) {
            for(var tier:LootBoxItem.Tier.values())for(int seed=0;seed<300;seed++)
                for(var item:LootBoxItem.roll(RandomSource.create(seed),tier)) {
                    assertFalse(item.getItem() instanceof BlockItem,item.getHoverName().getString());
                    assertFalse(item.getHoverName().getString().contains("Forge Stock"));
                    assertFalse(item.getHoverName().getString().contains("Tribute"));
                }
            for(var item:CreativeCatalog.entries(java.util.List.of())) {
                // Faction banner standards remain intentional creative entries.
                if(item.getItem() instanceof BlockItem block)
                    assertTrue(block.getBlock() instanceof net.minecraft.world.level.block.AbstractBannerBlock);
            }
        }
    }
}
