package com.devfarinsky.siegeoverhaul.client;

import com.devfarinsky.siegeoverhaul.core.HeroCasting;

/** Original, deterministic spell silhouettes. No world queries or per-segment allocations. */
public final class HeroCastShape {
    @FunctionalInterface public interface Segment {
        void line(double x1,double y1,double z1,double x2,double y2,double z2);
    }
    public static final int MAX_SEGMENTS=96;
    private HeroCastShape() {}
    public static void emit(int role,int phase,float age,Segment out) {
        int duration=phase==0?HeroCasting.WINDUP:HeroCasting.RELEASE;
        if(!HeroCasting.supported(role) || phase<0 || phase>1 || !Float.isFinite(age) || age<0 || age>=duration)return;
        double progress=age/duration;
        if(phase==0) {
            double radius=.45+.25*progress;
            for(int i=0;i<24;i++) {
                double a=i*Math.PI/12+progress,b=a+Math.PI/12;
                out.line(Math.cos(a)*radius,.1,Math.sin(a)*radius,Math.cos(b)*radius,.1,Math.sin(b)*radius);
            }
            // Each charge has its own upright crest above the hands.
            if(role==22) {
                out.line(.2,2.65,0,-.16,2.22,0);out.line(-.16,2.22,0,.16,2.22,0);out.line(.16,2.22,0,-.2,1.8,0);
            } else if(role==27) {
                out.line(0,1.8,0,-.3,2.2,0);out.line(-.3,2.2,0,0,2.8,0);
                out.line(0,2.8,0,.3,2.2,0);out.line(.3,2.2,0,0,1.8,0);
            } else {
                out.line(0,1.8,0,0,2.8,0);out.line(-.35,2.7,0,-.35,2.25,0);
                out.line(-.35,2.25,0,.35,2.25,0);out.line(.35,2.25,0,.35,2.7,0);
            }
            return;
        }
        double reach=HeroCasting.radius(role)*(.2+.8*progress);
        if(role==22) {
            // Six jagged spokes and a central bolt; stable geometry avoids strobing.
            for(int arm=0;arm<6;arm++) {
                double angle=arm*Math.PI/3;
                double px=0,py=1.25,pz=0;
                for(int step=1;step<=5;step++) {
                    double r=reach*step/5,side=step%2==0?.32:-.32;
                    double x=Math.cos(angle)*r-Math.sin(angle)*side,z=Math.sin(angle)*r+Math.cos(angle)*side;
                    double y=.25+(5-step)*.2;
                    out.line(px,py,pz,x,y,z);px=x;py=y;pz=z;
                }
            }
            out.line(0,3.6,0,-.35,2.4,0);out.line(-.35,2.4,0,.25,2.4,0);out.line(.25,2.4,0,0,.2,0);
        } else if(role==27) {
            // Flame-shaped rising ribbons, not a flat ring of vanilla fire particles.
            for(int plume=0;plume<8;plume++) {
                double angle=plume*Math.PI/4;
                double x=Math.cos(angle)*reach,z=Math.sin(angle)*reach;
                double height=.5+1.8*Math.sin(Math.PI*progress);
                out.line(x,.1,z,x-Math.sin(angle)*.3,height*.55,z+Math.cos(angle)*.3);
                out.line(x-Math.sin(angle)*.3,height*.55,z+Math.cos(angle)*.3,x*.88,height,z*.88);
                out.line(x*.88,height,z*.88,x+Math.sin(angle)*.25,height*.4,z-Math.cos(angle)*.25);
                out.line(x+Math.sin(angle)*.25,height*.4,z-Math.cos(angle)*.25,x,.1,z);
            }
        } else {
            // Twin curling crests form a low vortex that stays below most nameplates.
            for(int strand=0;strand<2;strand++)for(int i=0;i<32;i++) {
                double a=i*Math.PI/16+progress*2+strand*Math.PI,b=a+Math.PI/16;
                double r1=reach*(.35+.65*i/32),r2=reach*(.35+.65*(i+1)/32);
                out.line(Math.cos(a)*r1,.2+i/32.0,Math.sin(a)*r1,
                        Math.cos(b)*r2,.2+(i+1)/32.0,Math.sin(b)*r2);
            }
        }
    }
}
