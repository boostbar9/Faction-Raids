package com.devfarinsky.siegeoverhaul.core;

/** Compact two-column command panels at every supported Minecraft GUI scale. */
public record CoreHireLayout(int x,int y,int width,int height,boolean compact) {
    public static CoreHireLayout fit(int screenWidth,int screenHeight) {
        int w=Math.min(660,screenWidth-16),h=Math.min(350,screenHeight-16);
        return new CoreHireLayout((screenWidth-w)/2,(screenHeight-h)/2,w,h,w<500||h<300);
    }
    public int cardWidth(){return (width-30)/2;}
    // Reserve 30px at the bottom of the card grid so siege yard row on the
    // Army tab (bottom-32) does not overlap the hire button of the bottom cards.
    public int cardHeight(){return (height-118)/2;}
    public int cardX(int i){return x+10+(i%2)*(cardWidth()+10);}
    public int cardY(int i){return y+62+(i/2)*(cardHeight()+6);}
    public int marketHeight(){return (height-88)/3;}
    public int marketY(int i){return y+62+i*(marketHeight()+4);}
}
