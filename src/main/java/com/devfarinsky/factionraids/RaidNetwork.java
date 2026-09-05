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
    // v4 introduced in 2.12.0: added threat breakdown, defense explainer,
    // discovered units/factions, and War Journal rows to DashboardSync.
    // Bump whenever the wire format changes so mismatched builds refuse to connect
    // instead of silently corrupting the dashboard payload.
    private static final String PROTOCOL = "4";
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
            // v2.12.0 Know Your Enemy additions:
            buffer.writeUtf(s.threatBreakdown());
            buffer.writeUtf(s.defenseExplainer());
            buffer.writeVarInt(s.discoveredUnits().size());
            for (String u : s.discoveredUnits()) buffer.writeUtf(u);
            buffer.writeVarInt(s.discoveredFactions().size());
            for (String f : s.discoveredFactions()) buffer.writeUtf(f);
            buffer.writeVarInt(s.warJournal().size());
            for (RaidEvents.JournalRow row : s.warJournal()) {
                buffer.writeLong(row.timestamp());
                buffer.writeUtf(row.factionId());
                buffer.writeUtf(row.factionName());
                buffer.writeUtf(row.casusBelliId());
                buffer.writeVarInt(row.wavesReached());
                buffer.writeVarInt(row.totalWaves());
                buffer.writeUtf(row.outcome());
                buffer.writeVarInt(row.emeraldPayout());
            }
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
                    buffer.readVarInt(), buffer.readUtf(),
                    // v2.12.0 Know Your Enemy additions:
                    buffer.readUtf(), buffer.readUtf(),
                    readStringList(buffer), readStringList(buffer),
                    readJournalRows(buffer)));
        }

        private static java.util.List<String> readStringList(FriendlyByteBuf buffer) {
            int n = buffer.readVarInt();
            if (n <= 0) return java.util.List.of();
            java.util.List<String> list = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(buffer.readUtf());
            return java.util.List.copyOf(list);
        }

        private static java.util.List<RaidEvents.JournalRow> readJournalRows(FriendlyByteBuf buffer) {
            int n = buffer.readVarInt();
            if (n <= 0) return java.util.List.of();
            java.util.List<RaidEvents.JournalRow> rows = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                rows.add(new RaidEvents.JournalRow(
                        buffer.readLong(), buffer.readUtf(), buffer.readUtf(),
                        buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readUtf(), buffer.readVarInt()));
            }
            return java.util.List.copyOf(rows);
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
