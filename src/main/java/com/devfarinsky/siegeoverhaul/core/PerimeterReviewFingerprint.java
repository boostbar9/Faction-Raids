package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** The free review binds the original world states, stage order, owner, builder and one quote. */
public final class PerimeterReviewFingerprint {
    private PerimeterReviewFingerprint() {}

    public static String create(PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout,
                                Map<Long, BlockState> before, Map<Long, BlockState> clearance,
                                BlockPos core, int material, String territoryIdentity, UUID owner, UUID builder) {
        return create(plan, layout, before, clearance, null, core, material, territoryIdentity, owner, builder);
    }

    public static String create(PerimeterBlueprint.Plan plan, PerimeterStageLayout.Layout layout,
                                Map<Long, BlockState> before, Map<Long, BlockState> clearance,
                                PerimeterGateContract gateContract, BlockPos core, int material,
                                String territoryIdentity, UUID owner, UUID builder) {
        if (plan == null || !plan.valid() || layout == null || before == null || clearance == null
                || !before.keySet().equals(plan.blocks().keySet()) || !clearance.keySet().equals(plan.clearance())
                || core == null || material < 0 || material > 2 || territoryIdentity == null
                || territoryIdentity.length() > 200_000 || owner == null || builder == null)
            throw new IllegalArgumentException("Incomplete whole-perimeter review");
        PerimeterStageLayout.validate(plan, layout);
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            part(hash,"siege-perimeter-review-v1"); part(hash,Long.toString(core.asLong()));
            part(hash,Integer.toString(material)); part(hash,territoryIdentity);
            part(hash,owner.toString()); part(hash,builder.toString());
            part(hash,Integer.toString(PerimeterProject.FEE_VERSION));
            part(hash,Integer.toString(PerimeterProject.NEW_PROJECT_PRICE));
            part(hash,layout.digest());
            new TreeMap<>(plan.blocks()).forEach((cell,id)-> { part(hash,"target"); part(hash,Long.toString(cell)); part(hash,id); });
            states(hash,"before",before); states(hash,"clearance",clearance);
            if (gateContract != null) {
                part(hash,"gates"); part(hash,gateContract.digest());
                states(hash,"gate-observation",gateContract.observations());
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }

    private static void states(MessageDigest hash, String kind, Map<Long,BlockState> states) {
        new TreeMap<>(states).forEach((cell,state)-> {
            if (state == null) throw new IllegalArgumentException("Missing original perimeter block state");
            part(hash,kind); part(hash,Long.toString(cell)); part(hash,BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            var properties=new TreeMap<String,String>();
            state.getValues().forEach((property,value)-> properties.put(property.getName(),valueName(property,value)));
            part(hash,Integer.toString(properties.size()));
            properties.forEach((name,value)-> { part(hash,name); part(hash,value); });
        });
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static String valueName(net.minecraft.world.level.block.state.properties.Property property, Comparable value) {
        return property.getName(value);
    }
    private static void part(MessageDigest hash,String value) {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); hash.update(bytes);
    }
}
