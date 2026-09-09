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
    @Test void lootReelNeverOverlapsPurchaseButtons() {
        // Sweep the thresholds where the old fixed 26px reel crossed the button.
        boolean animated=false;
        for(int height=240;height<=1080;height++) {
            var l=CoreHireLayout.fit(854,height);
            for(int i=0;i<3;i++) {
                assertTrue(l.marketButtonY(i)+17<=l.marketY(i)+l.marketHeight());
                if(l.reelHeight()>=12) {
                    animated=true;
                    assertTrue(l.marketY(i)+31+l.reelHeight()<=l.marketButtonY(i)-4);
                }
            }
        }
        assertTrue(animated);
    }
    @Test void rosterStaysWithinContentAndClampsAfterResizeOrMembershipChanges() {
        for(int[] size:new int[][]{{320,240},{640,360},{854,480},{1920,1080}}){
            var l=CoreHireLayout.fit(size[0],size[1]);
            assertTrue(l.rosterY()+l.rosterLines()*12<=l.contentY()+l.contentHeight());
            assertEquals(0,l.rosterOffset(99,0));
            assertEquals(0,l.rosterOffset(-1,100));
            assertEquals(100-l.rosterLines(),l.rosterOffset(100,100));
            assertEquals(0,l.rosterOffset(90,2));
            assertFalse(l.overRoster(l.x()+15,l.contentY()+40));
            assertTrue(l.overRoster(l.x()+15,l.rosterY()));
            assertFalse(l.overRoster(l.x()+15,l.rosterY()+l.rosterLines()*12));
        }
    }
    @Test void smallScreensCollapseOverviewButNormalScreensShowIt() {
        assertEquals(0,CoreHireLayout.fit(320,240).overviewHeight());
        assertTrue(CoreHireLayout.fit(640,360).overviewHeight()>=80);
        assertEquals(150,CoreHireLayout.fit(1920,1080).overviewHeight());
    }
}
