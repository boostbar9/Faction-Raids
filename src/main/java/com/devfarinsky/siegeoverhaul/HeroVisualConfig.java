package com.devfarinsky.siegeoverhaul;
import net.minecraftforge.common.ForgeConfigSpec;
/** Local presentation only; never changes server combat rules. */
public final class HeroVisualConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ANIMATIONS, REDUCED_FLASHES, CAPTURE_BEAMS;
    public static final ForgeConfigSpec.IntValue PARTICLES, DISTANCE;
    public static final ForgeConfigSpec.DoubleValue VOLUME;
    static {
        var b=new ForgeConfigSpec.Builder();b.push("Hero abilities");
        ANIMATIONS=b.comment("Animate hero arms while casting.").define("Casting poses",true);
        REDUCED_FLASHES=b.comment("Use subdued ability surfaces and omit bright impact sparks.").define("Reduced flashes",false);
        PARTICLES=b.comment("Particle detail: 0 off, 1 low, 2 full. Shared per-tick limits still apply.").defineInRange("Particle detail",2,0,2);
        DISTANCE=b.comment("Maximum distance for hero casting effects in blocks.").defineInRange("Effect distance",48,8,96);
        VOLUME=b.comment("Local volume multiplier for hero ability cues.").defineInRange("Ability volume",0.7,0,1);
        b.pop();b.push("Siege objectives");
        CAPTURE_BEAMS=b.comment("Show a colored beacon above enemy cores during capture. Color follows capture percentage.").define("Capture beacon",true);
        b.pop();SPEC=b.build();
    }
    private HeroVisualConfig() {}
}
