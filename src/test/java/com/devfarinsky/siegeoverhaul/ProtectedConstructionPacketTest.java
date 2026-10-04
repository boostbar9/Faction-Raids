package com.devfarinsky.siegeoverhaul;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ProtectedConstructionPacketTest {
    @Test void exactAreaAndBoundedActionRoundTripInSeventeenBytes() {
        UUID id=UUID.randomUUID();
        for(int action=0;action<3;action++) {
            var packet=new RaidNetwork.ProtectedConstructionAction(id,action);var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {packet.encode(buffer);assertEquals(17,buffer.readableBytes());assertEquals(packet,RaidNetwork.ProtectedConstructionAction.decode(buffer));assertEquals(0,buffer.readableBytes());}
            finally {buffer.release();}
        }
    }
    @Test void InvalidActionsCannotSmuggleGeometryOwnershipOrInstantPlacement() {
        UUID id=UUID.randomUUID();assertThrows(IllegalArgumentException.class,()->new RaidNetwork.ProtectedConstructionAction(id,3));
        assertThrows(IllegalArgumentException.class,()->new RaidNetwork.ProtectedConstructionAction(id,-1));
        assertThrows(IllegalArgumentException.class,()->new RaidNetwork.ProtectedConstructionAction(null,2));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {buffer.writeUUID(id);buffer.writeByte(255);assertThrows(IllegalArgumentException.class,()->RaidNetwork.ProtectedConstructionAction.decode(buffer));}
        finally {buffer.release();}
    }
}
