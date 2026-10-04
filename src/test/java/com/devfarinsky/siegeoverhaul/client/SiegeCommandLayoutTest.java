package com.devfarinsky.siegeoverhaul.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SiegeCommandLayoutTest {
    @Test void tabsBodyFooterAndPaginationFitEachSupportedViewport() {
        for (int[] size : List.of(new int[]{320,240}, new int[]{480,360}, new int[]{854,480}, new int[]{1920,1080})) {
            var layout = SiegeCommandLayout.fit(size[0], size[1]);
            for (var rect : List.of(layout.panel(), layout.header(), layout.navigation(), layout.body(), layout.footer(),
                    layout.guideViewport(), layout.previous(), layout.next())) inside(rect, size[0], size[1]);
            assertTrue(layout.header().bottom() < layout.navigation().y());
            assertTrue(layout.navigation().bottom() < layout.body().y());
            assertTrue(layout.body().bottom() < layout.footer().y());
            assertTrue(layout.guideViewport().bottom() < layout.previous().y());
            for (int i=0; i<3; i++) {
                inside(layout.tab(i),size[0],size[1]);
                if(i>0) assertTrue(layout.tab(i-1).right()<layout.tab(i).x());
            }
            for (int i=0; i<4; i++) {
                inside(layout.action(i),size[0],size[1]);
                assertTrue(layout.action(i).width()>=60);
                if(i>0) assertTrue(layout.action(i-1).right()<layout.action(i).x());
            }
        }
    }

    @Test void everyRetainedJournalRowIsReachableAndNeverCoversThePager() {
        for (int[] size : List.of(new int[]{320,240},new int[]{480,360},new int[]{854,480})) {
            var layout=SiegeCommandLayout.fit(size[0],size[1]);
            for(int count=0;count<=10;count++) {
                assertTrue(layout.journalPages(count)>=1);
                assertTrue(layout.journalPages(count)*layout.journalRows()>=count);
                if(count>0) assertTrue((layout.journalPages(count)-1)*layout.journalRows()<count);
            }
            assertTrue(layout.body().y()+(layout.journalRows()-1)*44+40<layout.previous().y());
        }
    }
    @Test void containsUsesTheSameHalfOpenBoundsAsWidgets() {
        var box=SiegeCommandLayout.fit(320,240).body();
        assertTrue(box.contains(box.x(),box.y()));
        assertFalse(box.contains(box.right(),box.y()));
        assertFalse(box.contains(box.x(),box.bottom()));
    }
    private void inside(SiegeCommandLayout.Rect box,int width,int height) {
        assertTrue(box.width()>0 && box.height()>0);
        assertTrue(box.x()>=0 && box.y()>=0);
        assertTrue(box.right()<=width && box.bottom()<=height);
    }
}
