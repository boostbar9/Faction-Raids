package com.devfarinsky.siegeoverhaul;

import com.devfarinsky.siegeoverhaul.client.ClientDashboardOpener;
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
    private static final String PROTOCOL = "19";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(SiegeOverhaul.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static int messageId;

    public static void init() {
        CHANNEL.messageBuilder(CaptureBeam.class,messageId++,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CaptureBeam::encode).decoder(CaptureBeam::decode)
                .consumerMainThread((packet,supplier) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> com.devfarinsky.siegeoverhaul.client.CaptureBeaconRenderer.accept(packet));
                    supplier.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(HeroCast.class,messageId++,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HeroCast::encode).decoder(HeroCast::decode)
                .consumerMainThread((packet,supplier) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> com.devfarinsky.siegeoverhaul.client.HeroCastVisuals.accept(packet));
                    supplier.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(CoreDetails.class,messageId++,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CoreDetails::encode)
                .decoder(CoreDetails::decode)
                .consumerMainThread((p,supplier) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> {
                        var player=net.minecraft.client.Minecraft.getInstance().player;
                        if(player!=null && player.containerMenu instanceof com.devfarinsky.siegeoverhaul.core.CoreHireMenu menu && menu.containerId==p.menuId()) menu.details(p.faction(),p.members(),p.ledger());
                    });
                    supplier.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(ConstructionDetails.class, messageId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConstructionDetails::encode).decoder(ConstructionDetails::decode)
                .consumerMainThread((packet, supplier) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                        var player = net.minecraft.client.Minecraft.getInstance().player;
                        if (player != null && player.containerMenu instanceof com.devfarinsky.siegeoverhaul.core.CoreHireMenu menu
                                && menu.containerId == packet.menuId()) menu.construction(packet.jobs());
                    });
                    supplier.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(PerimeterProjectCancel.class, messageId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PerimeterProjectCancel::encode).decoder(PerimeterProjectCancel::decode)
                .consumerMainThread((packet, supplier) -> {
                    var context = supplier.get();
                    packet.handle(context.getSender());
                    context.setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(CorePurchase.class, messageId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> { buffer.writeVarInt(packet.menuId()); buffer.writeVarInt(packet.index()); buffer.writeLong(packet.rotation()); })
                .decoder(buffer -> new CorePurchase(buffer.readVarInt(), buffer.readVarInt(), buffer.readLong()))
                .consumerMainThread((packet, supplier) -> {
                    var context = supplier.get();
                    var player = context.getSender();
                    if (player != null && player.containerMenu instanceof com.devfarinsky.siegeoverhaul.core.CoreHireMenu menu
                            && menu.containerId == packet.menuId()) menu.purchase(player, packet.index(), packet.rotation());
                    context.setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(DashboardSync.class, messageId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DashboardSync::encode)
                .decoder(DashboardSync::decode)
                .consumerMainThread(DashboardSync::handle)
                .add();
        CHANNEL.messageBuilder(ArmyMarkers.class, messageId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ArmyMarkers::encode)
                .decoder(ArmyMarkers::decode)
                .consumerMainThread((packet, supplier) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> com.devfarinsky.siegeoverhaul.client.ArmyMapMarkers.accept(packet));
                    supplier.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(DashboardAction.class, messageId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DashboardAction::encode)
                .decoder(DashboardAction::decode)
                .consumerMainThread(DashboardAction::handle)
                .add();
        CHANNEL.messageBuilder(ProtectedConstructionAction.class, messageId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ProtectedConstructionAction::encode).decoder(ProtectedConstructionAction::decode)
                .consumerMainThread((packet, supplier) -> {
                    var context = supplier.get();
                    var sender = context.getSender();
                    if (sender != null) com.devfarinsky.siegeoverhaul.nativecompat.ProtectedConstructionActions.handle(
                            sender, packet.areaId(), packet.action());
                    context.setPacketHandled(true);
                }).add();
    }

    public record CaptureBeam(ResourceLocation dimension,net.minecraft.core.BlockPos pos,int percent,long time) {
        public CaptureBeam {
            if(dimension==null || pos==null || percent< -1 || percent>100) throw new IllegalArgumentException("Invalid capture beam");
            pos=pos.immutable();
        }
        public void encode(FriendlyByteBuf b) {
            b.writeResourceLocation(dimension);b.writeBlockPos(pos);b.writeByte(percent);b.writeLong(time);
        }
        public static CaptureBeam decode(FriendlyByteBuf b) {
            return new CaptureBeam(b.readResourceLocation(),b.readBlockPos(),b.readByte(),b.readLong());
        }
    }
    public static void sendCaptureBeam(ServerPlayer player,CaptureBeam packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),packet);
    }

    public record HeroCast(int entityId,java.util.UUID uuid,int role,long start,int phase,double x,double y,double z) {
        public HeroCast {
            if(uuid==null || (!com.devfarinsky.siegeoverhaul.core.HeroCasting.supported(role) && role != 30)
                    || phase<0 || phase>2 || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Invalid hero cast");
        }
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(entityId);b.writeUUID(uuid);b.writeVarInt(role);b.writeLong(start);b.writeByte(phase);
            b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);
        }
        public static HeroCast decode(FriendlyByteBuf b) {
            return new HeroCast(b.readVarInt(),b.readUUID(),b.readVarInt(),b.readLong(),b.readUnsignedByte(),b.readDouble(),b.readDouble(),b.readDouble());
        }
    }
    public static void sendHeroCast(net.minecraft.world.entity.LivingEntity hero,HeroCast packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> hero),packet);
    }
    public static void sendHeroCast(ServerPlayer player,HeroCast packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),packet);
    }

    public record ConstructionDetails(int menuId, java.util.List<com.devfarinsky.siegeoverhaul.core.ConstructionReport.Job> jobs) {
        public static final int LIMIT = 12, TEXT_LIMIT = 256;
        public ConstructionDetails {
            if (menuId < 1 || menuId > 100) throw new IllegalArgumentException("Invalid construction menu");
            jobs = java.util.List.copyOf(jobs.stream().limit(LIMIT).toList());
        }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(menuId); buffer.writeVarInt(jobs.size());
            for (var job : jobs) {
                buffer.writeUtf(job.label(), TEXT_LIMIT); buffer.writeVarInt(job.percent());
                buffer.writeUtf(job.progressText(), TEXT_LIMIT); buffer.writeUtf(job.location(), TEXT_LIMIT);
                buffer.writeUtf(job.activity(), TEXT_LIMIT); buffer.writeUtf(job.supplies(), TEXT_LIMIT);
                buffer.writeBoolean(job.projectId() != null);
                if (job.projectId() != null) { buffer.writeUUID(job.projectId()); buffer.writeLong(job.generation()); }
                buffer.writeUtf(job.sectionText(), TEXT_LIMIT);
                buffer.writeByte((job.cancelable() ? 1 : 0) | (job.complete() ? 2 : 0));
            }
        }
        public static ConstructionDetails decode(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt(), count = buffer.readVarInt();
            if (id < 1 || id > 100 || count < 0 || count > LIMIT)
                throw new IllegalArgumentException("Invalid construction menu or list size");
            var jobs = new java.util.ArrayList<com.devfarinsky.siegeoverhaul.core.ConstructionReport.Job>(count);
            for (int i = 0; i < count; i++) {
                String label = buffer.readUtf(TEXT_LIMIT); int percent = buffer.readVarInt();
                if (percent < -1 || percent > 100) throw new IllegalArgumentException("Invalid construction progress");
                String progress = buffer.readUtf(TEXT_LIMIT), location = buffer.readUtf(TEXT_LIMIT);
                String activity = buffer.readUtf(TEXT_LIMIT), supplies = buffer.readUtf(TEXT_LIMIT);
                int projectPresent = buffer.readUnsignedByte();
                if (projectPresent > 1) throw new IllegalArgumentException("Invalid construction identity flag");
                java.util.UUID projectId = projectPresent == 1 ? buffer.readUUID() : null;
                long generation = projectPresent == 1 ? buffer.readLong() : 0;
                String sectionText = buffer.readUtf(TEXT_LIMIT); int flags = buffer.readUnsignedByte();
                if (flags > 2 || projectId == null && (!sectionText.isEmpty() || flags != 0)
                        || projectId != null && (projectId.equals(new java.util.UUID(0, 0)) || generation <= 0))
                    throw new IllegalArgumentException("Invalid construction project metadata");
                jobs.add(new com.devfarinsky.siegeoverhaul.core.ConstructionReport.Job(label, percent, progress,
                        location, activity, supplies, projectId, generation, sectionText, (flags & 1) != 0, (flags & 2) != 0));
            }
            if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected construction report data");
            return new ConstructionDetails(id, jobs);
        }
    }

    /** Only the current owner at their open, valid Siege Core menu can cancel a whole commission. */
    public record PerimeterProjectCancel(int menuId, java.util.UUID projectId, long generation) {
        public PerimeterProjectCancel {
            // ServerPlayer assigns ordinary container IDs in the range 1..100; zero is the player inventory.
            if (menuId < 1 || menuId > 100 || projectId == null || projectId.equals(new java.util.UUID(0, 0)) || generation <= 0)
                throw new IllegalArgumentException("Invalid perimeter cancellation identity");
        }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(menuId); buffer.writeUUID(projectId); buffer.writeLong(generation);
        }
        public static PerimeterProjectCancel decode(FriendlyByteBuf buffer) {
            var packet = new PerimeterProjectCancel(buffer.readVarInt(), buffer.readUUID(), buffer.readLong());
            if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected perimeter cancellation data");
            return packet;
        }
        public boolean handle(ServerPlayer sender) {
            try {
                if (sender == null || !sender.isAlive() || sender.isSpectator()
                        || !(sender.containerMenu instanceof com.devfarinsky.siegeoverhaul.core.CoreHireMenu menu)
                        || menu.containerId != menuId || !menu.stillValid(sender)) return false;
                String key = com.devfarinsky.siegeoverhaul.core.SiegeCore.key(sender);
                if (key == null || !key.startsWith("team:") || key.length() <= 5 || key.length() > 256) return false;
                var core = RaidSavedData.get(sender.server).siegeCores.get(key);
                if (core == null || !core.contains("Position", net.minecraft.nbt.Tag.TAG_LONG)
                        || core.getBoolean("Occupied") || core.getBoolean("CoreRemoved")) return false;
                var pos = net.minecraft.core.BlockPos.of(core.getLong("Position"));
                if (!com.devfarinsky.siegeoverhaul.core.SiegeCore.canUse(sender, pos)) return false;
                var project = com.devfarinsky.siegeoverhaul.core.PerimeterProjectStore.get(core, projectId);
                if (project == null || project.header().generation() != generation
                        || !project.header().coreKey().equals(key)
                        || !project.header().owner().equals(sender.getUUID())
                        || project.state() == com.devfarinsky.siegeoverhaul.core.PerimeterProject.State.COMPLETE
                        || project.state() == com.devfarinsky.siegeoverhaul.core.PerimeterProject.State.CANCELED) return false;
                return com.devfarinsky.siegeoverhaul.nativecompat.NativePerimeterProjects.cancel(sender, projectId, generation);
            } catch (RuntimeException | LinkageError invalidAuthority) {
                // Missing or malformed authority is not permission to reconstruct or cancel another project.
                return false;
            }
        }
    }
    public static void cancelPerimeterProject(int menuId, java.util.UUID projectId, long generation) {
        CHANNEL.sendToServer(new PerimeterProjectCancel(menuId, projectId, generation));
    }

    public static void constructionDetails(ServerPlayer player, int menuId,
            java.util.List<com.devfarinsky.siegeoverhaul.core.ConstructionReport.Job> jobs) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ConstructionDetails(menuId, jobs));
    }

    public record CoreDetails(int menuId,String faction,java.util.List<String> members,int[] ledger) {
        public CoreDetails {
            faction=PacketText.bounded(faction,128);
            members=members==null?java.util.List.of():members.stream().filter(java.util.Objects::nonNull)
                    .limit(100).map(name->PacketText.bounded(name,64)).toList();
            if (ledger == null) ledger = new int[0];
            if (ledger.length > 64) {
                int[] trimmed = new int[64];
                System.arraycopy(ledger, ledger.length - 64, trimmed, 0, 64);
                ledger = trimmed;
            }
            else ledger = ledger.clone();
        }
        @Override public int[] ledger() { return ledger.clone(); }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(menuId);buffer.writeUtf(faction,128);
            buffer.writeCollection(members,(out,name)->out.writeUtf(name,64));
            buffer.writeVarIntArray(ledger);
        }
        public static CoreDetails decode(FriendlyByteBuf buffer) {
            int id=buffer.readVarInt();String faction=buffer.readUtf(128);int size=buffer.readVarInt();
            if(size<0 || size>100)throw new IllegalArgumentException("Invalid core roster size");
            var members=new java.util.ArrayList<String>(size);
            for(int i=0;i<size;i++)members.add(buffer.readUtf(64));
            int[] ledger = buffer.readVarIntArray(64);
            return new CoreDetails(id,faction,members,ledger);
        }
        // Records with an int[] component would use reference equality for that
        // field, which breaks round-trip tests. Override with array-value equality.
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CoreDetails o)) return false;
            return menuId == o.menuId && faction.equals(o.faction)
                    && members.equals(o.members) && java.util.Arrays.equals(ledger, o.ledger);
        }
        @Override public int hashCode() {
            return java.util.Objects.hash(menuId, faction, members, java.util.Arrays.hashCode(ledger));
        }
    }
    public static void coreDetails(ServerPlayer player,int menuId,String faction,java.util.List<String> members,int[] ledger) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),new CoreDetails(menuId,faction,members,ledger));
    }

    /**
     * Positions of one marching enemy army, for the world map. Sent only while
     * a siege is running and only to the faction being attacked, so the map
     * never becomes a free scouting tool against other people's wars.
     */
    public record ArmyMarkers(String faction, java.util.List<Marker> markers) {
        /** Largest army that will ever be drawn; oversized waves are trimmed. */
        public static final int LIMIT = 64;

        public record Marker(int id, int x, int z, boolean equipment) {}

        public ArmyMarkers {
            faction = PacketText.bounded(faction, 64);
            markers = markers == null ? java.util.List.of()
                    : markers.stream().filter(java.util.Objects::nonNull).limit(LIMIT).toList();
        }

        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUtf(faction, 64);
            buffer.writeCollection(markers, (out, marker) -> {
                out.writeVarInt(marker.id());
                out.writeVarInt(marker.x());
                out.writeVarInt(marker.z());
                out.writeBoolean(marker.equipment());
            });
        }

        public static ArmyMarkers decode(FriendlyByteBuf buffer) {
            String faction = buffer.readUtf(64);
            int size = buffer.readVarInt();
            if (size < 0 || size > LIMIT) throw new IllegalArgumentException("Invalid army marker count");
            var markers = new java.util.ArrayList<Marker>(size);
            for (int i = 0; i < size; i++)
                markers.add(new Marker(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
            return new ArmyMarkers(faction, markers);
        }
    }

    public static void armyMarkers(ServerPlayer player, ArmyMarkers markers) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), markers);
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
            // v2.28.0 GUI-honesty additions:
            buffer.writeBoolean(s.claimLinked());
            buffer.writeUtf(s.claimName());
            buffer.writeBoolean(s.recruitsClaimsBridgeReady());
            buffer.writeBoolean(s.workersBridgeReady());
            buffer.writeBoolean(s.smallShipsBridgeReady());
            buffer.writeBoolean(s.siegeWeaponsBridgeReady());
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
                    readJournalRows(buffer),
                    // v2.28.0 GUI-honesty additions:
                    buffer.readBoolean(), buffer.readUtf(),
                    buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean()));
        }

        static java.util.List<String> readStringList(FriendlyByteBuf buffer) {
            int n = readCount(buffer, 1);
            if (n == 0) return java.util.List.of();
            java.util.List<String> list = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(buffer.readUtf());
            return java.util.List.copyOf(list);
        }

        static java.util.List<RaidEvents.JournalRow> readJournalRows(FriendlyByteBuf buffer) {
            // A row needs a long, four string lengths, and three VarInts even
            // when every string is empty and each VarInt uses one byte.
            int n = readCount(buffer, Long.BYTES + 4 + 3);
            if (n == 0) return java.util.List.of();
            java.util.List<RaidEvents.JournalRow> rows = new java.util.ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                rows.add(new RaidEvents.JournalRow(
                        buffer.readLong(), buffer.readUtf(), buffer.readUtf(),
                        buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readUtf(), buffer.readVarInt()));
            }
            return java.util.List.copyOf(rows);
        }

        private static int readCount(FriendlyByteBuf buffer, int minimumEntryBytes) {
            int count = buffer.readVarInt();
            // Validate against the actual payload before allocating. This keeps
            // valid wire formats unchanged without trusting a remote capacity.
            if (count < 0 || count > buffer.readableBytes() / minimumEntryBytes)
                throw new IllegalArgumentException("Invalid dashboard list size");
            return count;
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

    /** Authenticated native-marker controls: 0 hide, 1 show, 2 cancel. No blueprint or owner data is accepted. */
    public record ProtectedConstructionAction(java.util.UUID areaId, int action) {
        public ProtectedConstructionAction {
            if (areaId == null || action < 0 || action > 2) throw new IllegalArgumentException("Invalid construction action");
        }
        public void encode(FriendlyByteBuf buffer) { buffer.writeUUID(areaId); buffer.writeByte(action); }
        public static ProtectedConstructionAction decode(FriendlyByteBuf buffer) {
            return new ProtectedConstructionAction(buffer.readUUID(), buffer.readUnsignedByte());
        }
    }
    public static void protectedConstructionAction(java.util.UUID areaId, int action) {
        CHANNEL.sendToServer(new ProtectedConstructionAction(areaId, action));
    }

    public record CorePurchase(int menuId, int index, long rotation) {}
    public static void purchaseCoreOffer(int menuId, int index, long rotation) {
        CHANNEL.sendToServer(new CorePurchase(menuId, index, rotation));
    }
    // Keep packet-only utilities separate from CHANNEL initialization.
    private static final class PacketText {
        private static String bounded(String text, int limit) {
            if (text == null) return "";
            int end = Math.min(text.length(), limit);
            if (end > 0 && end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            return text.substring(0, end);
        }
    }
    private RaidNetwork() {}
}
