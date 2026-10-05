package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.UUID;

/** Bounded, read-only observations. Unknown villagers never acquire invented identity or care data. */
public final class CivilianReport {
    public static final int NAME_LIMIT = 80;
    private CivilianReport() {}

    public record Resident(UUID id, String name, ResourceLocation profession, ResourceLocation type,
                           int level, boolean loaded, boolean baby, boolean bed, boolean workstation, boolean paused) {
        public Resident {
            if (id == null || name == null || name.length() > NAME_LIMIT || profession == null || type == null
                    || level < 1 || level > 5) throw new IllegalArgumentException("Invalid resident details");
            if (!loaded && (!name.isEmpty() || baby || bed || workstation))
                throw new IllegalArgumentException("Unavailable resident cannot claim live details");
        }
        public String label() { return loaded && !name.isBlank() ? name : "Resident " + id.toString().substring(0, 8); }
        public String status() { return !loaded ? "Details unavailable" : paused ? "Stranded · taxes paused" : "Loaded"; }
        public static Resident unavailable(UUID id, boolean paused) {
            return new Resident(id, "", new ResourceLocation("minecraft", "none"),
                    new ResourceLocation("minecraft", "plains"), 1, false, false, false, false, paused);
        }
    }
    public record Snapshot(List<Resident> residents, boolean taxEligible) {
        public Snapshot {
            residents = List.copyOf(residents);
            if (residents.size() > CivilianLedger.LIMIT || residents.stream().map(Resident::id).distinct().count() != residents.size())
                throw new IllegalArgumentException("Invalid resident list");
        }
        public long loaded() { return residents.stream().filter(Resident::loaded).count(); }
        public void write(FriendlyByteBuf b) {
            b.writeBoolean(taxEligible); b.writeVarInt(residents.size());
            for (var r : residents) {
                b.writeUUID(r.id()); b.writeUtf(r.name(), NAME_LIMIT);
                b.writeResourceLocation(r.profession()); b.writeResourceLocation(r.type()); b.writeByte(r.level());
                b.writeByte((r.loaded() ? 1 : 0) | (r.baby() ? 2 : 0) | (r.bed() ? 4 : 0)
                        | (r.workstation() ? 8 : 0) | (r.paused() ? 16 : 0));
            }
        }
        public static Snapshot read(FriendlyByteBuf b) {
            int eligible = b.readUnsignedByte(), count = b.readVarInt();
            if (eligible > 1 || count < 0 || count > CivilianLedger.LIMIT) throw new IllegalArgumentException("Invalid resident report");
            var residents = new java.util.ArrayList<Resident>(count);
            for (int i = 0; i < count; i++) {
                UUID id = b.readUUID(); String name = b.readUtf(NAME_LIMIT);
                var profession = ResourceLocation.tryParse(b.readUtf(128));
                var type = ResourceLocation.tryParse(b.readUtf(128));
                int level = b.readUnsignedByte(), flags = b.readUnsignedByte();
                if (flags > 31) throw new IllegalArgumentException("Invalid resident flags");
                residents.add(new Resident(id, name, profession, type, level, (flags & 1) != 0,
                        (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0, (flags & 16) != 0));
            }
            if (b.isReadable()) throw new IllegalArgumentException("Unexpected resident data");
            return new Snapshot(residents, eligible == 1);
        }
    }
}
