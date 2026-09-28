package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaptureBeaconTest extends MinecraftTestSupport {
    private static RaidNetwork.CaptureBeam packet(int x,int percent,long time) {
        return new RaidNetwork.CaptureBeam(new ResourceLocation("minecraft","overworld"),new BlockPos(x,64,0),percent,time);
    }
    @Test void packetPreservesDimensionPositionProgressTimeAndClearSignal() {
        for(int progress:new int[]{-1,0,25,50,75,100}) {
            var p=packet(-120,progress,3456);var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {p.encode(buffer);assertEquals(p,RaidNetwork.CaptureBeam.decode(buffer));assertEquals(0,buffer.readableBytes());}
            finally {buffer.release();}
        }
        assertThrows(IllegalArgumentException.class,()->packet(0,101,0));
        assertThrows(IllegalArgumentException.class,()->packet(0,-2,0));
    }
    @Test void progressClampsWithoutOverflowAndDoesNotShowCompletionEarly() {
        assertEquals(99,CaptureBeacon.percent(2399,2400));assertEquals(100,CaptureBeacon.percent(2400,2400));
        assertEquals(0,CaptureBeacon.percent(-1,2400));assertEquals(0,CaptureBeacon.percent(1,0));
        assertEquals(100,CaptureBeacon.percent(Integer.MAX_VALUE,1));
    }
    @Test void colorHasDistinctMilestonesAndInterpolatesContinuously() {
        float[][] stops={{1F,.12F,.08F},{1F,.48F,.05F},{1F,.9F,.12F},{.55F,1F,.12F},{.12F,1F,.35F}};
        for(int i=0;i<stops.length;i++)assertArrayEquals(stops[i],CaptureBeacon.color(i*25),.0001F);
        for(int i=1;i<=100;i++) {
            var before=CaptureBeacon.color(i-1);var after=CaptureBeacon.color(i);
            for(int channel=0;channel<3;channel++) {
                assertTrue(after[channel]>=0 && after[channel]<=1);
                assertTrue(Math.abs(after[channel]-before[channel])<.03F);
            }
        }
    }
    @Test void lostProgressChangesColorAndStalePacketsCannotRestoreIt() {
        var state=new CaptureBeacon.State();state.accept(packet(0,75,100),100);
        state.accept(packet(0,50,120),120);state.accept(packet(0,100,100),120);
        assertEquals(50,state.active(120).get(0).percent());
        assertArrayEquals(CaptureBeacon.color(50),CaptureBeacon.color(state.active(120).get(0).percent()));
    }
    @Test void completionInterruptedCapturesAndWorldChangesDoNotLeavePermanentBeams() {
        var state=new CaptureBeacon.State();state.accept(packet(0,100,100),100);
        assertEquals(1,state.active(159).size());assertTrue(state.active(160).isEmpty());
        state.accept(packet(0,40,200),200);state.accept(packet(0,-1,220),220);
        assertTrue(state.active(220).isEmpty());
        state.accept(packet(0,40,300),300);state.clear();assertTrue(state.active(300).isEmpty());
        state.accept(packet(0,40,100),300);state.accept(packet(0,40,400),300);
        assertTrue(state.active(300).isEmpty());
    }
    @Test void clientCacheNeverExceedsItsBeamBudget() {
        var state=new CaptureBeacon.State();
        for(int i=0;i<100;i++)state.accept(packet(i,50,100),100);
        assertEquals(CaptureBeacon.LIMIT,state.active(100).size());
        assertTrue(state.active(100).stream().noneMatch(p->p.pos().getX()==0));
    }
    @Test void snapshotsOnlyReachNearbyMembersOfTheCapturingFaction() {
        var level=mock(ServerLevel.class);var near=mock(ServerPlayer.class);var far=mock(ServerPlayer.class);var foreign=mock(ServerPlayer.class);
        when(level.dimension()).thenReturn(Level.OVERWORLD);when(level.getGameTime()).thenReturn(200L);
        when(level.players()).thenReturn(List.of(near,far,foreign));
        when(far.distanceToSqr(any(net.minecraft.world.phys.Vec3.class))).thenReturn(193D*193);
        // Plain JUnit lacks Forge's transformed NetworkEvent constructor. Substitute
        // channel registration only; the real audience selection and packets run below.
        var builder=mock(net.minecraftforge.network.NetworkRegistry.ChannelBuilder.class,RETURNS_SELF);
        when(builder.simpleChannel()).thenReturn(mock(net.minecraftforge.network.simple.SimpleChannel.class));
        try(var registration=mockStatic(net.minecraftforge.network.NetworkRegistry.ChannelBuilder.class)) {
            registration.when(()->net.minecraftforge.network.NetworkRegistry.ChannelBuilder.named(any())).thenReturn(builder);
            try(var keys=mockStatic(SiegeCore.class);var network=mockStatic(RaidNetwork.class)) {
            keys.when(()->SiegeCore.key(near)).thenReturn("team:blue");keys.when(()->SiegeCore.key(far)).thenReturn("team:blue");
            keys.when(()->SiegeCore.key(foreign)).thenReturn("team:red");
            var pos=new BlockPos(0,64,0);
            CaptureBeacon.send(level,"team:blue",pos,1200,2400,2);
            network.verify(()->RaidNetwork.sendCaptureBeam(near,packet(0,50,200)));
            CaptureBeacon.send(level,"team:blue",pos,0,2400,0);
            network.verify(()->RaidNetwork.sendCaptureBeam(near,packet(0,-1,200)));
                network.verifyNoMoreInteractions();
            }
        }
        verify(level,never()).getChunk(anyInt(),anyInt());
        verify(level,never()).setBlock(any(),any(),anyInt());
    }
}
