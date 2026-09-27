package com.devfarinsky.siegeoverhaul.compat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WallProjectionTest {
    public static class Area {
        boolean enabled;
        public void setAlwaysShowProjection(boolean value) { enabled=value; }
    }
    @Test void smallWallUsesNativeProjectionWithoutChangingBlueprint() {
        var area=new Area();assertTrue(WorkersBridge.enableWallProjection(area,240));assertTrue(area.enabled);
    }
    @Test void largeEmptyOrOlderAreasKeepSafeManualFallback() {
        for(int count:new int[]{0,-1,1025,20000}) {
            var area=new Area();assertFalse(WorkersBridge.enableWallProjection(area,count));assertFalse(area.enabled);
        }
        assertFalse(WorkersBridge.enableWallProjection(new Object(),200));
        var edge=new Area();assertTrue(WorkersBridge.enableWallProjection(edge,1024));
    }
}
