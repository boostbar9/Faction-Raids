package com.devfarinsky.factionraids;

import com.devfarinsky.factionraids.client.ClientDashboardOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class RaidNetwork {
    // v3 introduced in 2.11.0: added 10 Codex fields to DashboardSync.
    // Bump whenever the wire format changes so mismatched builds refuse to connect
    // instead of silently corrupting the dashboard payload.
    private static final String PROTOCOL = "3";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(FactionRaids.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static int messageId;

    public static void init() {
        CHANNEL.messageBuilder(DashboardSync.class, messageId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DashboardSync::encode)
                .decoder(DashboardSync::decode)
                .consumerMainThread(DashboardSync::handle)
                .add();
        CHANNEL.messageBuilder(DashboardAction.class, messageId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DashboardAction::encode)
                .decoder(DashboardAction::decode)
                .consumerMainThread(DashboardAction::handle)
                .add();
    }

    public static void openDashboard(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new DashboardSync(RaidEvents.dashboardSnapshot(player)));
    }

    public static void sendDashboardAction(Action action) {
        CHANNEL.sendToServer(new DashboardAction(action.ordinal()));
    }

    public enum Action {
        REFRESH_HOME,
        START_PRACTICE,
        HELP,
        SYNC
    }

    public record DashboardSync(RaidEvents.DashboardSnapshot snapshot) {
        private static void encode(DashboardSync packet, FriendlyByteBuf buffer) {
            RaidEvents.DashboardSnapshot s = packet.snapshot;
            buffer.writeUtf(s.faction());
            buffer.writeBoolean(s.registered());
            buffer.writeBoolean(s.active());
            buffer.writeUtf(s.stronghold());
            buffer.writeVarInt(s.wave());
            buffer.writeVarInt(s.totalWaves());
            buffer.writeVarInt(s.deployed());
            buffer.writeVarInt(s.reinforcing());
            buffer.writeVarInt(s.defeated());
            buffer.writeVarInt(s.occupationPercent());
            buffer.writeBoolean(s.breached());
            buffer.writeVarInt(s.breachPercent());
            buffer.writeVarInt(s.recruits());
            buffer.writeVarInt(s.workers());
            buffer.writeVarInt(s.ships());
            buffer.writeVarInt(s.siegeWeapons());
            buffer.writeVarInt(s.assetScalingEnemies());
            buffer.writeVarInt(s.breachedBlockCount());
            buffer.writeUtf(s.gateTarget());
            buffer.writeVarInt(s.gateBreachPercent());
            buffer.writeUtf(s.cooldown());
            buffer.writeVarInt(s.emeraldReward());
            buffer.writeBoolean(s.rewardEligible());
            // v2.11.0 Codex additions:
            buffer.writeUtf(s.factionId());
            buffer.writeUtf(s.casusBelliId());
            buffer.writeUtf(s.factionOpening());
            buffer.writeUtf(s.factionChant());
            buffer.writeUtf(s.campDirection());
            buffer.writeVarInt(s.campDistance());
            buffer.writeUtf(s.nextWaveLabel());
            buffer.writeUtf(s.nextWaveComposition());
            buffer.writeVarInt(s.defenseScore());
            buffer.writeUtf(s.defenseScoreLabel());
        }

        private static DashboardSync decode(FriendlyByteBuf buffer) {
            return new DashboardSync(new RaidEvents.DashboardSnapshot(
                    buffer.readUtf(), buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(), buffer.readVarInt(),
                    buffer.readUtf(), buffer.readVarInt(), buffer.readBoolean(),
                    // v2.11.0 Codex additions:
                    buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readVarInt(), buffer.readUtf(), buffer.readUtf(),
                    buffer.readVarInt(), buffer.readUtf()));
        }

        private static void handle(DashboardSync packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientDashboardOpener.open(packet.snapshot)));
            context.setPacketHandled(true);
        }
    }

    public record DashboardAction(int actionId) {
        private static void encode(DashboardAction packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.actionId);
        }

        private static DashboardAction decode(FriendlyByteBuf buffer) {
            return new DashboardAction(buffer.readVarInt());
        }

        private static void handle(DashboardAction packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            context.enqueueWork(() -> {
                if (sender == null || packet.actionId < 0 || packet.actionId >= Action.values().length) return;
                switch (Action.values()[packet.actionId]) {
                    case REFRESH_HOME -> RaidEvents.dashboardRefreshHome(sender);
                    case START_PRACTICE -> RaidEvents.dashboardStart(sender);
                    case HELP -> RaidEvents.dashboardHelp(sender);
                    case SYNC -> { }
                }
                openDashboard(sender);
            });
            context.setPacketHandled(true);
        }
    }

    private RaidNetwork() {}
}
