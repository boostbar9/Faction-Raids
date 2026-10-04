package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClaimBridgeFailureTest extends MinecraftTestSupport {
    @BeforeEach @AfterEach void clearProviderState() throws Exception {
        for (String name : new String[]{"PROVIDER_AVAILABLE", "BROKEN_PROVIDERS"}) {
            var field = ClaimBridge.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?,?> map) map.clear();
            if (value instanceof Set<?> set) set.clear();
        }
        for (String name : new String[]{"ftbInit", "opacInit"}) setField(name, false);
        for (String name : new String[]{"ftbGetManager", "ftbGetChunk", "ftbGetTeamId",
                "opacGetInstance", "opacGetClaimStatePos", "opacClaimGetPlayerId"}) setField(name, null);
        Provider.instance = null;
    }

    @Test void absentOptionalModsDoNotBlockPlacement() {
        ModList mods = mock(ModList.class);
        try (var modList = mockStatic(ModList.class); var recruits = mockStatic(RecruitsClaimsBridge.class)) {
            modList.when(ModList::get).thenReturn(mods);
            assertFalse(ClaimBridge.isForeignClaim(mock(ServerLevel.class), BlockPos.ZERO, null));
            assertFalse(ClaimBridge.anyProviderAvailable());
        }
    }

    @Test void brokenFtbApiBlocksInitialAndLaterQueries() { checkBroken("ftbchunks", "FTB Chunks"); }
    @Test void brokenOpacApiBlocksInitialAndLaterQueries() { checkBroken("openpartiesandclaims", "Open Parties"); }

    @Test void installedFtbWithNullManagerIsUnavailableNotUnclaimed() throws Exception {
        setField("ftbInit", true);
        setField("ftbGetManager", Provider.class.getMethod("instance"));
        checkBroken("ftbchunks", "FTB Chunks");
    }

    @Test void installedOpacWithNullInstanceIsUnavailableNotUnclaimed() throws Exception {
        setField("opacInit", true);
        setField("opacGetInstance", Provider.class.getMethod("instance"));
        checkBroken("openpartiesandclaims", "Open Parties");
    }

    @Test void installedOpacWithNullServerDataIsUnavailableNotUnclaimed() throws Exception {
        Provider.instance = new Instance(null);
        setField("opacInit", true);
        setField("opacGetInstance", Provider.class.getMethod("instance"));
        checkBroken("openpartiesandclaims", "Open Parties");
    }

    @Test void installedOpacWithNullClaimManagerIsUnavailableNotUnclaimed() throws Exception {
        Provider.instance = new Instance(new Data());
        setField("opacInit", true);
        setField("opacGetInstance", Provider.class.getMethod("instance"));
        checkBroken("openpartiesandclaims", "Open Parties");
    }

    @Test void existingClaimsRequireAnActualUuidOwner() {
        var owner = java.util.UUID.randomUUID();
        assertSame(owner, ClaimBridge.requireClaimOwner(owner));
        assertThrows(IllegalStateException.class, () -> ClaimBridge.requireClaimOwner(null));
        assertThrows(IllegalStateException.class, () -> ClaimBridge.requireClaimOwner(owner.toString()));
        assertThrows(IllegalStateException.class, () -> ClaimBridge.requireClaimOwner(new Object()));
    }

    @Test void healthyProviderValueIsReturnedWithoutReplacement() {
        var manager = new Object();
        assertSame(manager, ClaimBridge.requireAvailable(manager, "test manager"));
        assertThrows(IllegalStateException.class, () -> ClaimBridge.requireAvailable(null, "test manager"));
    }

    private static void setField(String name, Object value) throws Exception {
        var field = ClaimBridge.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }

    public static final class Provider {
        static Object instance;
        public static Object instance() { return instance; }
    }
    public static final class Instance {
        private final Object data;
        Instance(Object data) { this.data = data; }
        public Object getServerData() { return data; }
    }
    public static final class Data {
        public Object getServerClaimsManager() { return null; }
    }

    private void checkBroken(String modId, String label) {
        // The optional APIs are deliberately absent from this test runtime, reproducing
        // a loaded provider whose expected class/signature cannot be resolved.
        ModList mods = mock(ModList.class);
        when(mods.isLoaded(modId)).thenReturn(true);
        ServerLevel level = mock(ServerLevel.class);
        try (var modList = mockStatic(ModList.class); var recruits = mockStatic(RecruitsClaimsBridge.class)) {
            modList.when(ModList::get).thenReturn(mods);
            assertTrue(ClaimBridge.isForeignClaim(level, BlockPos.ZERO, null));
            assertTrue(ClaimBridge.isForeignClaim(level, new BlockPos(80,64,80), null));
            assertTrue(ClaimBridge.anyProviderAvailable(), "Caller must not skip protection after API failure");
            assertTrue(ClaimBridge.diagnosticStatus().contains(label + ": ERROR (placement blocked)"));
            assertEquals(9, ClaimBridge.collectClaimedChunks(level, BlockPos.ZERO, 1, null).size());
            verify(mods, times(1)).isLoaded(modId);
            verifyNoInteractions(level);
        }
    }
}
