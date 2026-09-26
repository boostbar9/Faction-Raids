package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.RaidSavedData.RaidState;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Purpose and payoff for the three war-camp upgrade buildings.
 *
 * <p>Each stage-built structure is now a named installation with a live
 * gameplay effect and a keystone block. While the keystone stands the effect
 * applies; destroying or burning it (the keystone vanishes) ends the effect and
 * announces the loss, so raiding the buildings is worthwhile. State lives on the
 * raid's {@code campaign} compound under a namespaced key so it survives reloads.
 */
public final class CampStructures {
    private CampStructures() {}

    public enum Kind {
        GRANARY(0, "granary", "Sanctuary of Demeter", "minecraft:hay_block",
                "Sacred Stores: enemy camp guards mend their wounds while it stands."),
        ARMOURY(1, "armoury", "Forge of Hephaestus", "minecraft:anvil",
                "Divine Forge: enemy camp guards fight with sharper weapons while it stands."),
        COMMAND_POST(2, "command", "Strategion of Athena", "minecraft:cartography_table",
                "War Council: enemy waves are coordinated to arrive faster while it stands.");
        public final int stage;
        public final String key, title, keystoneBlock, purpose;
        Kind(int stage, String key, String title, String keystoneBlock, String purpose) {
            this.stage = stage; this.key = key; this.title = title;
            this.keystoneBlock = keystoneBlock; this.purpose = purpose;
        }
        public static Kind forStage(int stage) {
            for (Kind kind : values()) if (kind.stage == stage) return kind;
            return null;
        }
    }

    /** Keystone (identity centrepiece) local offset x=0,y=1,z=-2 in every stage layout. */
    static BlockPos keystone(BlockPos center, Direction entrance) {
        return center.relative(entrance, -2).above();
    }

    private static CompoundTag root(RaidState raid) {
        return raid.campaign.getCompound(ModConstants.Tags.CAMP_STRUCTURES);
    }

    /** Record a freshly commissioned structure. It becomes active once its keystone is placed. */
    public static void record(RaidState raid, int stage, BlockPos center, Direction entrance) {
        Kind kind = Kind.forStage(stage);
        if (kind == null) return;
        CompoundTag all = root(raid);
        CompoundTag entry = new CompoundTag();
        entry.putLong("Pos", keystone(center, entrance).asLong());
        entry.putLong("Center", center.asLong());
        entry.putInt("Entrance", entrance.get2DDataValue());
        entry.putString("Block", kind.keystoneBlock);
        entry.putBoolean("Active", false);
        entry.putBoolean("Announced", false);
        all.put(kind.key, entry);
        raid.campaign.put(ModConstants.Tags.CAMP_STRUCTURES, all);
    }

    public static boolean standing(RaidState raid, Kind kind) {
        CompoundTag entry = root(raid).getCompound(kind.key);
        return entry.getBoolean("Active");
    }

    /**
     * Flip each recorded structure between standing and lost based on whether its
     * keystone block is still present, announcing the first time each edge is seen.
     * Unloaded keystones are left untouched so a temporarily unloaded camp does not
     * spuriously report its buildings destroyed.
     */
    public static void tick(ServerLevel level, RaidState raid) {
        CompoundTag all = root(raid);
        if (all.isEmpty()) return;
        boolean dirty = false;
        for (Kind kind : Kind.values()) {
            if (!all.contains(kind.key, Tag.TAG_COMPOUND)) continue;
            CompoundTag entry = all.getCompound(kind.key);
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            if (!level.hasChunkAt(pos)) continue;
            boolean present = matches(level, pos, entry.getString("Block"));
            boolean active = entry.getBoolean("Active");
            if (present && !active) {
                entry.putBoolean("Active", true);
                if (!entry.getBoolean("Announced")) {
                    entry.putBoolean("Announced", true);
                    announce(level.getServer(), raid.teamKey, Component.literal(
                            "Enemy " + kind.title + " raised. " + kind.purpose).withStyle(ChatFormatting.RED));
                }
                dirty = true;
            } else if (!present && active) {
                entry.putBoolean("Active", false);
                announce(level.getServer(), raid.teamKey, Component.literal(
                        "Enemy " + kind.title + " destroyed. Its advantage is gone.").withStyle(ChatFormatting.GREEN));
                FactionLogger.LOG.info("Camp {} lost its {}", raid.teamKey, kind.title);
                dirty = true;
            }
            if (present && level.getGameTime() % 20L == 0L) emitActivity(level, kind, pos);
        }
        if (dirty) {
            raid.campaign.put(ModConstants.Tags.CAMP_STRUCTURES, all);
            RaidSavedData.get(level.getServer()).setDirty();
        }
    }

    private static boolean matches(ServerLevel level, BlockPos pos, String block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        return id != null && id.toString().equals(block);
    }

    /**
     * Keep a visible, low-cost sign that each installation is operational.
     * This is intentionally a handful of server particles once per second,
     * not a per-tick emitter.
     */
    private static void emitActivity(ServerLevel level, Kind kind, BlockPos pos) {
        var particle = switch (kind) {
            case GRANARY -> net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
            case ARMOURY -> net.minecraft.core.particles.ParticleTypes.CRIT;
            case COMMAND_POST -> net.minecraft.core.particles.ParticleTypes.ENCHANT;
        };
        level.sendParticles(particle, pos.getX()+0.5D, pos.getY()+1.2D, pos.getZ()+0.5D,
                3, 0.45D, 0.35D, 0.45D, 0.02D);
    }

    /**
     * Preserve entrances of camps created by older releases whose pavilions
     * were centred outside the radius-12 palisade. The saved keystone points
     * two blocks behind the centre, so five blocks back toward camp is the
     * three-wide doorway on the wall line.
     */
    static boolean legacyPerimeterOpening(RaidState raid, BlockPos wallColumn) {
        if (raid.campPos == null) return false;
        CompoundTag all = root(raid);
        for (Kind kind : Kind.values()) {
            if (!all.contains(kind.key, Tag.TAG_COMPOUND)) continue;
            CompoundTag entry = all.getCompound(kind.key);
            BlockPos door;
            if (entry.contains("Center", Tag.TAG_LONG) && entry.contains("Entrance", Tag.TAG_INT)) {
                Direction entrance=Direction.from2DDataValue(entry.getInt("Entrance"));
                door=BlockPos.of(entry.getLong("Center")).relative(entrance,3);
            } else {
                BlockPos keystone=BlockPos.of(entry.getLong("Pos"));
                int dx=keystone.getX()-raid.campPos.getX(),dz=keystone.getZ()-raid.campPos.getZ();
                Direction outward=Math.abs(dx)>=Math.abs(dz)
                        ? (dx>=0?Direction.EAST:Direction.WEST)
                        : (dz>=0?Direction.SOUTH:Direction.NORTH);
                door=keystone.relative(outward.getOpposite(),5).below();
            }
            if (Math.abs(door.getX()-wallColumn.getX())+Math.abs(door.getZ()-wallColumn.getZ())<=1)
                return true;
        }
        return false;
    }

    /** Keep later buildings and supply barrels out of an installation's entrance. */
    static boolean accessColumn(RaidState raid, BlockPos pos) {
        for (Kind kind : Kind.values()) {
            CompoundTag entry=root(raid).getCompound(kind.key);
            BlockPos center;
            Direction front;
            if(entry.contains("Center",Tag.TAG_LONG) && entry.contains("Entrance",Tag.TAG_INT)) {
                center=BlockPos.of(entry.getLong("Center"));
                front=Direction.from2DDataValue(entry.getInt("Entrance"));
            } else {
                if(raid.campPos==null || !entry.contains("Pos",Tag.TAG_LONG))continue;
                BlockPos keystone=BlockPos.of(entry.getLong("Pos"));
                int dx=raid.campPos.getX()-keystone.getX(),dz=raid.campPos.getZ()-keystone.getZ();
                front=Math.abs(dx)>=Math.abs(dz)?(dx>=0?Direction.EAST:Direction.WEST)
                        :(dz>=0?Direction.SOUTH:Direction.NORTH);
                center=keystone.relative(front,2).below();
            }
            int dx=pos.getX()-center.getX(), dz=pos.getZ()-center.getZ();
            int depth=dx*front.getStepX()+dz*front.getStepZ();
            int side=dx*front.getClockWise().getStepX()+dz*front.getClockWise().getStepZ();
            if(depth>=3 && depth<=7 && Math.abs(side)<=1) return true;
        }
        return false;
    }

    // --- Pure, configurable effect helpers, so wave/guard code stays declarative. ---

    /** Between-wave delay (ticks) with the Command Post's coordination applied when it stands. */
    public static int waveIntervalTicks(int baseTicks, boolean commandPost) {
        if (!commandPost) return baseTicks;
        return Math.max(20, baseTicks * RaidConfig.COMMAND_POST_WAVE_INTERVAL_PERCENT.get() / 100);
    }

    /** Health the Granary restores to a camp guard each bookkeeping pass while it stands. */
    public static int guardRegen(boolean granary) {
        return granary ? RaidConfig.GRANARY_GUARD_REGEN.get() : 0;
    }

    /** Bonus attack damage the Armoury grants each camp guard while it stands. */
    public static double armouryDamageBonus(boolean armoury) {
        return armoury ? RaidConfig.ARMOURY_GUARD_DAMAGE.get() : 0.0D;
    }

    private static void announce(MinecraftServer server, String teamKey, Component message) {
        Component styled = Component.empty().append(ModConstants.MESSAGE_PREFIX).append(message);
        if (RaidConfig.ANNOUNCE_GLOBALLY.get()) server.getPlayerList().broadcastSystemMessage(styled, false);
        else for (var player : server.getPlayerList().getPlayers())
            if (teamKey.equals(SiegeCore.key(player))) player.sendSystemMessage(styled);
    }
}
