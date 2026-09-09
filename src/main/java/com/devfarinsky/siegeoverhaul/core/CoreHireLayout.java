package com.devfarinsky.siegeoverhaul.core;

/** Responsive command center: collapse the overview before shrinking service controls. */
public record CoreHireLayout(int x,int y,int width,int height,boolean compact) {
    public static CoreHireLayout fit(int screenWidth,int screenHeight) {
        int w=Math.min(860,screenWidth-16),h=Math.min(520,screenHeight-16);
        return new CoreHireLayout((screenWidth-w)/2,(screenHeight-h)/2,w,h,w<500||h<300);
    }
    public int overviewHeight(){return width>=440 && height>=300?Math.min(150,height-240):0;}
    public int overviewY(){return y+36;}
    public int mapWidth(){return (width-30)*3/5;}
    public int tabY(){return y+32+(overviewHeight()>0?overviewHeight()+12:0);}
    public int contentY(){return tabY()+30;}
    public int contentHeight(){return height-(contentY()-y)-22;}
    public int cardWidth(){return (width-30)/2;}
    public int cardHeight(){return (contentHeight()-6)/2;}
    public int cardX(int i){return x+10+(i%2)*(cardWidth()+10);}
    public int cardY(int i){return contentY()+(i/2)*(cardHeight()+6);}
    public int marketHeight(){return (contentHeight()-8)/3;}
    public int marketY(int i){return contentY()+i*(marketHeight()+4);}
}
