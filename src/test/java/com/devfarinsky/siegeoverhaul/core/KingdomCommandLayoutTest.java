package com.devfarinsky.siegeoverhaul.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KingdomCommandLayoutTest {
    @Test void overviewTabsAndPurchasesStaySeparatedAcrossGuiScales() {
        for(int[] size:new int[][]{{320,240},{426,240},{480,320},{640,360},{854,480},{960,540},{1920,1080}}) {
            var l=CoreHireLayout.fit(size[0],size[1]);
            assertTrue(l.x()>=0 && l.y()>=0);
            if(l.overviewHeight()>0){
                assertTrue(l.overviewY()+l.overviewHeight()<l.tabY());
                assertTrue(l.mapWidth()>100);
                assertTrue(l.width()-30-l.mapWidth()>100);
            }
            assertTrue(l.tabY()+22<=l.contentY());
            for(int i=0;i<4;i++){
                assertTrue(l.cardHeight()>=40);
                assertTrue(l.cardY(i)>=l.contentY());
                assertTrue(l.cardY(i)+l.cardHeight()<=l.y()+l.height()-18);
                assertTrue(l.cardX(i)+l.cardWidth()<=l.x()+l.width()-10);
            }
            for(int i=0;i<3;i++){
                assertTrue(l.marketHeight()>=35);
                assertTrue(l.marketY(i)>=l.contentY());
                assertTrue(l.marketY(i)+l.marketHeight()<=l.y()+l.height()-18);
            }
        }
    }
    @Test void smallScreensCollapseOverviewButNormalScreensShowIt() {
        assertEquals(0,CoreHireLayout.fit(320,240).overviewHeight());
        assertTrue(CoreHireLayout.fit(640,360).overviewHeight()>=80);
        assertEquals(150,CoreHireLayout.fit(1920,1080).overviewHeight());
    }
}
