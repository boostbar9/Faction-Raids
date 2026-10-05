package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EarthworksCommissionTest extends MinecraftTestSupport {
    @Test void retainedPaidBitCannotAuthenticateAbsentMismatchedOrMalformedCoreDebit() {
        var job=job();job.acknowledgeDebit();var core=new CompoundTag();core.putLong("BankEmeralds",1234);
        var original=core.copy();assertFalse(EarthworksCommission.paidMatches(core,job));assertEquals(original,core);
        var receipt=new CompoundTag();receipt.putUUID("Project",job.manifest.header().project());receipt.putUUID("Area",job.area);
        receipt.putString("Manifest",job.manifest.hash());receipt.putString("Receipt",job.read().journal().binding().paymentReceipt());receipt.putInt("Price",64);
        var rows=new ListTag();rows.add(receipt);core.put("SiegeEarthworksDebitsV1",rows);
        assertTrue(EarthworksCommission.paidMatches(core,job));
        for(String key:new String[]{"Manifest","Receipt"}){
            var changed=core.copy();changed.getList("SiegeEarthworksDebitsV1",10).getCompound(0).putString(key,"f".repeat(64));
            var before=changed.copy();assertFalse(EarthworksCommission.paidMatches(changed,job));assertEquals(before,changed);assertEquals(1234,changed.getLong("BankEmeralds"));
        }
        var malformed=core.copy();malformed.getList("SiegeEarthworksDebitsV1",10).getCompound(0).putString("Extra","unknown");
        assertFalse(EarthworksCommission.paidMatches(malformed,job));assertEquals(1234,malformed.getLong("BankEmeralds"));
    }
    @Test void retryRequiresExactLiveBindingAndRejectsDeserializedRecoveryOrUnresolvedFence() {
        var ledger=new EarthworksJobLedger();var job=job(ledger);job.acknowledgeDebit();var binding=job.read().journal().binding();
        assertTrue(EarthworksCommission.retryMatches(job,job.area,job.manifest.hash(),binding,job.core));
        assertFalse(EarthworksCommission.retryMatches(job,UUID.randomUUID(),job.manifest.hash(),binding,job.core));
        assertFalse(EarthworksCommission.retryMatches(job,job.area,job.manifest.hash(),binding,job.core.above()));
        var loaded=EarthworksJobLedger.load(ledger.save(new CompoundTag())).job(job.manifest.header().project());
        assertFalse(EarthworksCommission.retryMatches(loaded,job.area,job.manifest.hash(),binding,job.core));
    }
    @Test void airborneActualStandingRefusesBeforeAnyWorldStateOrCollisionRead() {
        var job=job();var level=mock(ServerLevel.class);var worker=mock(BuilderEntity.class);var area=mock(EarthworksBuildArea.class);
        when(worker.blockPosition()).thenReturn(new BlockPos(2,64,0));when(worker.getBoundingBox()).thenReturn(new AABB(2,64,0,2.6,66,0.6));
        when(worker.onGround()).thenReturn(false);
        assertNotNull(EarthworksStandingAccess.problem(level,worker,area,job));
        verifyNoInteractions(level);
    }
    private static EarthworksJobLedger.Job job(){return job(new EarthworksJobLedger());}
    private static EarthworksJobLedger.Job job(EarthworksJobLedger ledger){
        var all=NativeEarthworksAdapterTest.manifest();var manifest=new PerimeterEarthworksManifest(all.header(),new ArrayList<>(all.observations().values()),all.steps().subList(0,2));
        return ledger.prepare(manifest,new PerimeterEarthworksJournal.Binding(UUID.randomUUID(),"a".repeat(64),"b".repeat(64)),UUID.randomUUID(),BlockPos.ZERO);
    }
}
