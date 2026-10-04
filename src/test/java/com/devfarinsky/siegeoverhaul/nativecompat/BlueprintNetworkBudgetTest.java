package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlueprintNetworkBudgetTest extends MinecraftTestSupport {
    private CompoundTag blueprint(int stone, int oak) {
        CompoundTag tag = new CompoundTag(); tag.putInt("width", 256); tag.putString("facing", "south");
        ListTag blocks = new ListTag();
        for (int i = 0; i < stone + oak; i++) {
            CompoundTag cell = new CompoundTag(), state = new CompoundTag();
            cell.putInt("x", i % 256); cell.putInt("y", i / 65536); cell.putInt("z", i / 256 % 256);
            state.putString("Name", i < stone ? "minecraft:cobblestone" : "minecraft:oak_planks");
            cell.put("state", state); blocks.add(cell);
        }
        tag.put("blocks", blocks); return tag;
    }

    @Test void actualNativeSerializerAcceptsTheEntireLShapedTerritoryWithoutChangingItsRecipe() {
        var tag = blueprint(1836, 540); var before = tag.copy();
        assertNull(BlueprintNetworkBudget.problem(tag)); assertEquals(before, tag);
    }

    @Test void nativeAccountingRatherThanWireLengthDefinesTheSafeCapacity() {
        var tag = blueprint(5100, 1500);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeNbt(tag);
            assertTrue(buffer.readableBytes() < 2_097_152);
            var accounting = new NbtAccounter(Long.MAX_VALUE);
            assertEquals(tag, buffer.readNbt(accounting));
            assertTrue(accounting.getUsage() > 2_097_152);
        } finally { buffer.release(); }
        assertNotNull(BlueprintNetworkBudget.problem(tag));
    }

    @Test void malformedLargeSavedPayloadCannotGrowTheTemporaryEncoderWithoutBound() {
        var tag = new CompoundTag();
        tag.putByteArray("foreign:payload", new byte[BlueprintNetworkBudget.MAX_ENCODED_BYTES + 1]);
        assertNotNull(BlueprintNetworkBudget.problem(tag));
        assertEquals(BlueprintNetworkBudget.MAX_ENCODED_BYTES + 1, tag.getByteArray("foreign:payload").length);
    }

    @Test void accountingRejectsNormalLargeWallsBeforeAnAreaIsSentToClients() {
        // 5x5 flat territory: 1,500 columns, 600 parapets, 6,600 native block targets.
        var tag = blueprint(5100, 1500); var before = tag.copy();
        assertNotNull(BlueprintNetworkBudget.problem(tag)); assertEquals(before, tag);
        assertNotNull(BlueprintNetworkBudget.problem(blueprint(32768, 0)));
    }
}
