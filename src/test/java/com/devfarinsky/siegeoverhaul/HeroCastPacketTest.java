package com.devfarinsky.siegeoverhaul;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class HeroCastPacketTest {
    @Test void roundTripPreservesTrackingIdentityOriginAndPhase() {
        for(int role:new int[]{22,27,29,30})for(int phase=0;phase<=2;phase++) {
            var packet=new RaidNetwork.HeroCast(42,UUID.randomUUID(),role,1984L,phase,-15.2,64,345.3);
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {packet.encode(buffer);assertEquals(packet,RaidNetwork.HeroCast.decode(buffer));assertEquals(0,buffer.readableBytes());}
            finally {buffer.release();}
        }
    }
    @Test void rejectsInvalidEffectAndNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,()->new RaidNetwork.HeroCast(1,UUID.randomUUID(),21,0,0,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new RaidNetwork.HeroCast(1,UUID.randomUUID(),22,0,3,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new RaidNetwork.HeroCast(1,UUID.randomUUID(),22,0,0,Double.NaN,0,0));
    }
}
