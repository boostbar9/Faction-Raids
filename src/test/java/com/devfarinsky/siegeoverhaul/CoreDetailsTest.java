package com.devfarinsky.siegeoverhaul;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreDetailsTest extends MinecraftTestSupport {
    @Test void oversizedRosterAndNamesEncodeSafelyAndRoundTripAsImmutableSnapshot() {
        var names=new ArrayList<String>(); for(int i=0;i<1000;i++)names.add("Online " + "x".repeat(200));
        var packet=new RaidNetwork.CoreDetails(17,"f".repeat(200),names); names.clear();
        assertEquals(100,packet.members().size()); assertEquals(128,packet.faction().length());
        assertTrue(packet.members().stream().allMatch(n->n.length()==64));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {packet.encode(buffer);assertEquals(packet,RaidNetwork.CoreDetails.decode(buffer));assertEquals(0,buffer.readableBytes());}
        finally {buffer.release();}
        assertThrows(UnsupportedOperationException.class,()->packet.members().add("changed"));
    }
    @Test void excessiveWireRosterIsRejectedBeforeReadingOrAllocatingEntries() {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {buffer.writeVarInt(1);buffer.writeUtf("Test");buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class,()->RaidNetwork.CoreDetails.decode(buffer));}
        finally {buffer.release();}
    }
    @Test void truncationDoesNotSplitSupplementaryCharacters() {
        var packet=new RaidNetwork.CoreDetails(1,"f".repeat(127)+"\uD83D\uDE00",List.of("x".repeat(63)+"\uD83D\uDE00"));
        assertEquals(127,packet.faction().length());assertEquals(63,packet.members().get(0).length());
    }
}
