package com.devfarinsky.siegeoverhaul.nativecompat;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializers;

/** Use the actual native STRUCTURE serializer and its unchanged client NBT accounting limit. */
public final class BlueprintNetworkBudget {
    // A malformed saved recipe must not grow an unbounded temporary output buffer.
    // This is only an encoder allocation ceiling; the native decoder still enforces
    // its unchanged 2 MiB object-accounting quota independently of wire length.
    static final int MAX_ENCODED_BYTES = 4 * 1024 * 1024;
    private BlueprintNetworkBudget() {}

    public static String problem(CompoundTag blueprint) {
        if (blueprint == null) return "The complete native blueprint is unavailable.";
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(256, MAX_ENCODED_BYTES));
        try {
            EntityDataSerializers.COMPOUND_TAG.write(buffer, blueprint);
            CompoundTag received = EntityDataSerializers.COMPOUND_TAG.read(buffer);
            if (!blueprint.equals(received) || buffer.isReadable())
                return "The complete native blueprint could not be synchronized safely.";
            return null;
        } catch (RuntimeException tooLarge) {
            return "The entire perimeter exceeds native Workers' client blueprint capacity. No payment or partial job was created.";
        } finally { buffer.release(); }
    }
}
