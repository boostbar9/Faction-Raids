package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Set;
import java.util.UUID;

/** Hand-only provenance, independently authenticated by the world ledger. Never a work/storage lease. */
final class ProtectedBuilderHandLifecycle {
    static final String KEY = "SiegeProtectedHandLifecycle";
    static final int MAX_BUILDERS = 4096;
    private static final Set<String> KEYS = Set.of("Version", "Receipt", "Builder", "Owner", "Area", "Generation");
    private ProtectedBuilderHandLifecycle() {}

    record Receipt(UUID receipt, UUID builder, UUID owner, UUID area, UUID generation) {
        Receipt {
            for (UUID id : new UUID[]{receipt, builder, owner, area, generation})
                if (id == null || id.equals(new UUID(0, 0))) throw new IllegalArgumentException("Missing hand lifecycle identity");
        }
        CompoundTag save() {
            var tag = new CompoundTag(); tag.putInt("Version", 1); tag.putUUID("Receipt", receipt);
            tag.putUUID("Builder", builder); tag.putUUID("Owner", owner); tag.putUUID("Area", area);
            tag.putUUID("Generation", generation); return tag;
        }
    }

    static Receipt read(CompoundTag tag) {
        if (tag == null || !tag.getAllKeys().equals(KEYS) || !tag.contains("Version", Tag.TAG_INT)
                || tag.getInt("Version") != 1 || !tag.hasUUID("Receipt") || !tag.hasUUID("Builder")
                || !tag.hasUUID("Owner") || !tag.hasUUID("Area") || !tag.hasUUID("Generation"))
            throw new IllegalArgumentException("Malformed hand lifecycle identity");
        return new Receipt(tag.getUUID("Receipt"), tag.getUUID("Builder"), tag.getUUID("Owner"),
                tag.getUUID("Area"), tag.getUUID("Generation"));
    }

    static boolean selected(CompoundTag data) { return data.contains(KEY); }

    static boolean matches(CompoundTag data, UUID builder, ConstructionEditLedger ledger) {
        try {
            if (!data.contains(KEY, Tag.TAG_COMPOUND) || ledger == null) return false;
            var receipt = read(data.getCompound(KEY));
            // Owner is original commissioning provenance, not a restriction on later native ownership transfer.
            return receipt.builder().equals(builder)
                    && ledger.sameGeneration(receipt.generation()) && receipt.equals(ledger.handLifecycle(builder));
        } catch (RuntimeException malformed) { return false; }
    }
}
