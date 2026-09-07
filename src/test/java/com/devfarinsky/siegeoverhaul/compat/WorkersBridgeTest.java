package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.RaidConfig;
import com.devfarinsky.siegeoverhaul.RecruitsBridge;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkersBridgeTest extends MinecraftTestSupport {
    @Test
    void workers2ApiReceivesRaiderOwnerAndNonWorkingHoldMode() throws Exception {
        Workers2Api worker = new Workers2Api();
        WorkersBridge.configureBuilder(worker, Vec3.ZERO);
        assertEquals(Optional.of(RecruitsBridge.RAIDERS_LEADER_UUID), worker.owner);
        assertTrue(worker.owned);
        assertFalse(worker.listen);
        assertEquals(3, worker.followState);
        assertEquals(Vec3.ZERO, worker.hold);
    }

    @Test
    void unsupportedApiFailsBeforeCallerCanRegisterWorker() {
        assertThrows(ReflectiveOperationException.class,
                () -> WorkersBridge.configureBuilder(new Object(), Vec3.ZERO));
    }

    @Test
    void disabledCompatibilityNeverRequiresModLoader() {
        RaidConfig.ENABLE_WORKERS_COMPAT.set(false);
        assertFalse(WorkersBridge.available());
    }

    /** Public signatures verified against Workers 2 / Recruits upstream. */
    public static class Workers2Api {
        Optional<UUID> owner;
        boolean owned, listen = true;
        int followState;
        Vec3 hold;
        public void setOwnerUUID(Optional<UUID> owner) { this.owner = owner; }
        public void setIsOwned(boolean owned) { this.owned = owned; }
        public void setListen(boolean listen) { this.listen = listen; }
        public void setFollowState(int state) { followState = state; }
        public void setHoldPos(Vec3 pos) { hold = pos; }
    }
}
