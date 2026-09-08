package com.devfarinsky.siegeoverhaul;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.network.NetworkHooks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.mockStatic;

public abstract class MinecraftTestSupport {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        // Plain JUnit has no ModLauncher event transformation or network
        // channels. Bootstrap real blocks/items, but leave networking to Forge.
        try (var networkHooks = mockStatic(NetworkHooks.class)) {
            Bootstrap.bootStrap();
        }
    }

    @BeforeEach
    void resetConfig() {
        RaidConfig.SPEC.setConfig(CommentedConfig.inMemory());
    }
}
