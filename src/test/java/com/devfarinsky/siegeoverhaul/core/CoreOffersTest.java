package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CoreOffersTest extends MinecraftTestSupport {
    @Test void exactRoleWeights() {
        int[] counts = new int[3];
        for (int i=0;i<100;i++) counts[CoreOffers.role(i)]++;
        assertArrayEquals(new int[]{80,10,10}, counts);
    }
    @Test void stockPersistsAndRefreshesOnlyAfterFifteenMinutes() {
        RaidSavedData data = new RaidSavedData();
        CompoundTag stock = new CompoundTag();
        data.siegeCores.put("team:test", stock);
        assertTrue(CoreOffers.refresh(stock, 100, RandomSource.create(7)));
        stock.putInt("Sold", 5);
        stock.putLong("Position", 123);
        var restored = RaidSavedData.load(data.save(new CompoundTag()));
        var saved = restored.siegeCores.get("team:test");
        assertEquals(stock, saved);
        saved.remove("Position"); // Breaking the core does not reset offers.
        assertFalse(CoreOffers.refresh(saved, 18099, RandomSource.create(9)));
        assertEquals(5, saved.getInt("Sold"));
        assertTrue(CoreOffers.refresh(saved, 18100, RandomSource.create(9)));
        assertEquals(0, saved.getInt("Sold"));
        assertEquals(36100, saved.getLong("RefreshAt"));
    }
    @Test void closedMenusDoNotShiftTheRotationSchedule() {
        CompoundTag stock = new CompoundTag();
        CoreOffers.refresh(stock, 100, RandomSource.create(7));
        assertTrue(CoreOffers.refresh(stock, 20000, RandomSource.create(9)));
        assertEquals(36100, stock.getLong("RefreshAt"));
    }
    @Test void preparationAndFortificationJobsSurviveRestart() {
        var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        raid.preparationTotalTicks = 14400;
        raid.preparationTicks = 7000;
        raid.pendingFortifications.put(123L,"minecraft:spruce_log");
        var saved = RaidSavedData.RaidState.load(raid.save());
        assertEquals(7000, saved.preparationTicks);
        assertEquals(14400, saved.preparationTotalTicks);
        assertEquals(raid.pendingFortifications, saved.pendingFortifications);
        var legacy = RaidSavedData.RaidState.load(new CompoundTag());
        assertEquals(0, legacy.preparationTicks);
    }
}
