package com.devfarinsky.siegeoverhaul.core;
import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CoreOffersTest extends MinecraftTestSupport {
    @Test void exactRoleWeights() {
        int[] counts = new int[4];
        for (int i=0;i<100;i++) counts[CoreOffers.role(i)]++;
        assertArrayEquals(new int[]{50,25,20,5}, counts);
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
    @Test void workerWeightsAndSlotTypes() {
        int[] counts = new int[6];
        for (int i = 0; i < 100; i++) counts[CoreOffers.worker(i) - 4]++;
        assertArrayEquals(new int[]{25,25,20,15,10,5}, counts);
        for (int seed = 0; seed < 100; seed++) {
            CompoundTag stock = new CompoundTag();
            CoreOffers.refresh(stock, 100, RandomSource.create(seed));
            assertTrue(CoreOffers.valid(stock.getIntArray("Offers")));
        }
        assertThrows(IllegalArgumentException.class, () -> CoreOffers.worker(100));
        assertThrows(IllegalArgumentException.class, () -> CoreOffers.role(-1));
    }
    @Test void legacyMigrationPreservesPurchasesAndDeadline() {
        CompoundTag stock = new CompoundTag();
        stock.putIntArray("Offers", new int[]{1,2,0});
        stock.putInt("Sold", 5);
        stock.putLong("RefreshAt", 18100);
        assertTrue(CoreOffers.refresh(stock, 200, RandomSource.create(4)));
        assertEquals(1, stock.getIntArray("Offers")[0]);
        assertEquals(2, stock.getIntArray("Offers")[1]);
        assertTrue(stock.getIntArray("Offers")[2] >= 4);
        assertEquals(5, stock.getInt("Sold"));
        assertEquals(18100, stock.getLong("RefreshAt"));
        assertFalse(CoreOffers.refresh(stock, 201, RandomSource.create(5)));
    }
    @Test void purchasesRejectStaleRotationsSoldSlotsAndInvalidIndices() {
        CompoundTag stock = new CompoundTag();
        CoreOffers.refresh(stock, 100, RandomSource.create(1));
        assertTrue(CoreOffers.canPurchase(stock, 0, 18100));
        stock.putInt("Sold", 1);
        assertFalse(CoreOffers.canPurchase(stock, 0, 18100));
        assertTrue(CoreOffers.canPurchase(stock, 1, 18100));
        assertFalse(CoreOffers.canPurchase(stock, -1, 18100));
        assertFalse(CoreOffers.canPurchase(stock, 4, 18100));
        CoreOffers.refresh(stock, 18100, RandomSource.create(1));
        assertFalse(CoreOffers.canPurchase(stock, 1, 18100));
        assertTrue(CoreOffers.canPurchase(stock, 1, 36100));
    }
    @Test void heroStockSurvivesReloadAndCannotBePurchasedTwice() {
        CompoundTag stock=new CompoundTag(); CoreOffers.refresh(stock,100,RandomSource.create(42));
        int hero=stock.getInt("HeroRole"); assertTrue(hero>=10 && hero<=29);
        assertTrue(CoreOffers.canPurchase(stock,3,18100));
        stock.putInt("Sold",8);
        var data=new RaidSavedData(); data.siegeCores.put("team:test",stock);
        var loaded=RaidSavedData.load(data.save(new CompoundTag())).siegeCores.get("team:test");
        assertFalse(CoreOffers.refresh(loaded,200,RandomSource.create(5)));
        assertEquals(hero,loaded.getInt("HeroRole"));
        assertFalse(CoreOffers.canPurchase(loaded,3,18100));
        assertTrue(CoreOffers.canPurchase(loaded,0,18100));
    }
    @Test void oldStockGainsHeroWithoutResettingOffersOrPurchases() {
        CompoundTag stock=new CompoundTag(); stock.putIntArray("Offers",new int[]{0,1,7});
        stock.putInt("OfferSchema",2); stock.putInt("Sold",5); stock.putLong("RefreshAt",18100);
        assertTrue(CoreOffers.refresh(stock,200,RandomSource.create(4)));
        assertArrayEquals(new int[]{0,1,7},stock.getIntArray("Offers"));
        assertEquals(5,stock.getInt("Sold")); assertEquals(18100,stock.getLong("RefreshAt"));
        // Weighted roster of 20 heroes: each pick must be in [10,29];
        // Common heroes (roles 10,11 with weight 15 each) should be picked far
        // more often than any single Legendary (roles 27+ with weight 1-2).
        int[] counts=new int[20];
        for(int i=0;i<100;i++) { int h=CoreOffers.hero(i); assertTrue(h>=10 && h<=29,"hero out of range: "+h); counts[h-10]++; }
        int commons=counts[0]+counts[1];
        int legendaries=counts[27-10]+counts[28-10]+counts[29-10];
        assertTrue(commons>legendaries,"commons("+commons+") should outweigh legendaries("+legendaries+")");
    }
    @Test void hiringLayoutFitsGuiScalesAndResizes() {
        for (int[] size : new int[][]{{320,240},{480,270},{600,260},{854,480},{1920,1080}}) {
            var layout = CoreHireLayout.fit(size[0],size[1]);
            assertTrue(layout.x() >= 0 && layout.y() >= 0);
            assertTrue(layout.tabY() + CoreHireLayout.TAB_HEIGHT <= layout.ribbonY());
            assertTrue(layout.ribbonY() + CoreHireLayout.RIBBON_HEIGHT <= layout.contentY());
            for (int i = 0; i < 4; i++) {
                assertTrue(layout.cardWidth() > 70 && layout.cardHeight() >= 40);
                assertTrue(layout.cardX(i) >= layout.x());
                assertTrue(layout.cardX(i) + layout.cardWidth() <= layout.x() + layout.width());
                assertTrue(layout.cardY(i) + layout.cardHeight() <= layout.armyCardsBottom());
            }
            assertTrue(layout.siegeYardY() >= layout.armyCardsBottom());
            assertTrue(layout.siegeYardY() + CoreHireLayout.ARMY_ACTION_HEIGHT < layout.footerY());
            assertTrue(layout.marketY(2) + layout.marketHeight() <= layout.contentBottom());
        }
        assertTrue(CoreHireLayout.fit(320,240).compact());
        assertFalse(CoreHireLayout.fit(854,480).compact());
    }
    @Test void preparationAndFortificationJobsSurviveRestart() {
        var raid = new RaidSavedData.RaidState("team:test", "siege_core", 0);
        raid.preparationTotalTicks = 14400;
        raid.preparationTicks = 7000;
        raid.campUpgradeStage=2; raid.campUpgradeTicks=1200;
        raid.pendingFortifications.put(123L,"minecraft:spruce_log");
        var saved = RaidSavedData.RaidState.load(raid.save());
        assertEquals(7000, saved.preparationTicks);
        assertEquals(2,saved.campUpgradeStage); assertEquals(1200,saved.campUpgradeTicks);
        assertEquals(14400, saved.preparationTotalTicks);
        assertEquals(raid.pendingFortifications, saved.pendingFortifications);
        var legacy = RaidSavedData.RaidState.load(new CompoundTag());
        assertEquals(0, legacy.preparationTicks);
    }
}
