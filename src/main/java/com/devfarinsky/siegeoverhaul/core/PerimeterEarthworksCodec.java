package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest.*;

/** Strict server-save format for the staged proposal. No migration from an accepted v1 project. */
final class PerimeterEarthworksCodec {
    private static final int MAX_PALETTE = MAX_OBSERVATIONS + MAX_CUTS + MAX_PLACEMENTS;
    private PerimeterEarthworksCodec() {}

    static CompoundTag save(PerimeterEarthworksManifest manifest) {
        CompoundTag out = new CompoundTag(), header = new CompoundTag(); var h = manifest.header();
        out.putInt("EarthworksVersion", FORMAT_VERSION); out.putString("Hash", manifest.hash());
        header.putUUID("Project", h.project()); header.putLong("Generation", h.generation());
        header.putUUID("Owner", h.owner()); header.putUUID("Builder", h.builder());
        header.putString("Dimension", h.dimension()); header.putString("Faction", h.faction());
        header.putString("Claims", h.claimsDigest()); header.putString("Layout", h.layoutDigest()); header.putString("Policy", h.policy());
        header.putInt("Component", h.component()); header.putInt("Plane", h.planeY());
        header.putInt("MinY", h.minY()); header.putInt("MaxY", h.maxY());
        header.putInt("FeeVersion", h.feeVersion()); header.putInt("Price", h.price()); out.put("Header", header);
        ListTag palette = new ListTag(), observations = new ListTag(), steps = new ListTag(), stages = new ListTag();
        Map<BlockState, Integer> paletteIds = new HashMap<>();
        for (Observation cell : manifest.observations().values()) {
            CompoundTag tag = new CompoundTag(); tag.putLong("Pos", cell.pos());
            tag.putInt("Original", paletteId(cell.original(), palette, paletteIds)); tag.putString("Role", cell.role().name());
            tag.putLong("EditRevision", cell.editRevision()); observations.add(tag);
        }
        for (Step step : manifest.steps()) {
            CompoundTag tag = new CompoundTag(); tag.putInt("Stage", step.stage()); tag.putString("Kind", step.kind().name());
            tag.putLong("Pos", step.pos()); tag.putInt("Before", paletteId(step.before(), palette, paletteIds));
            tag.putInt("After", paletteId(step.after(), palette, paletteIds));
            if (step.removal() != null) {
                Removal r = step.removal(); CompoundTag removal = new CompoundTag();
                removal.putString("Origin", r.origin().name()); removal.putString("Family", r.family().name());
                removal.putString("Adapter", r.adapter()); removal.putString("Version", r.version());
                removal.putString("Source", r.sourceDigest()); tag.put("Removal", removal);
            }
            steps.add(tag);
        }
        for (String digest : manifest.stageDigests()) { CompoundTag tag = new CompoundTag(); tag.putString("Hash", digest); stages.add(tag); }
        out.put("Palette", palette); out.put("Observations", observations); out.put("Steps", steps); out.put("Stages", stages);
        return out;
    }

    static PerimeterEarthworksManifest load(CompoundTag tag) {
        keys(tag, Set.of("EarthworksVersion", "Hash", "Header", "Palette", "Observations", "Steps", "Stages"), Set.of());
        if (integer(tag, "EarthworksVersion") != FORMAT_VERSION) throw invalid("Unknown earthworks format");
        String expectedHash = hash(tag, "Hash"); require(tag, "Header", Tag.TAG_COMPOUND); CompoundTag h = tag.getCompound("Header");
        keys(h, Set.of("Project", "Generation", "Owner", "Builder", "Dimension", "Faction", "Claims", "Layout", "Policy",
                "Component", "Plane", "MinY", "MaxY", "FeeVersion", "Price"), Set.of());
        Header header = new Header(uuid(h, "Project"), number(h, "Generation"), uuid(h, "Owner"), uuid(h, "Builder"),
                string(h, "Dimension", 256), string(h, "Faction", 256), hash(h, "Claims"), hash(h, "Layout"), string(h, "Policy", 128),
                integer(h, "Component"), integer(h, "Plane"), integer(h, "MinY"), integer(h, "MaxY"), integer(h, "FeeVersion"), integer(h, "Price"));
        ListTag savedPalette = list(tag, "Palette", MAX_PALETTE); List<BlockState> palette = new ArrayList<>();
        Set<BlockState> seenStates = new HashSet<>(); Set<Integer> used = new HashSet<>();
        for (Tag raw : savedPalette) {
            BlockState state = state((CompoundTag) raw);
            if (!seenStates.add(state)) throw invalid("Duplicate earthworks state palette entry"); palette.add(state);
        }
        List<Observation> observations = new ArrayList<>();
        for (Tag raw : list(tag, "Observations", MAX_OBSERVATIONS)) {
            CompoundTag cell = (CompoundTag) raw; keys(cell, Set.of("Pos", "Original", "Role", "EditRevision"), Set.of());
            observations.add(new Observation(number(cell, "Pos"), palette(palette, integer(cell, "Original"), used),
                    enumeration(Role.class, cell, "Role"), number(cell, "EditRevision")));
        }
        List<Step> steps = new ArrayList<>();
        for (Tag raw : list(tag, "Steps", MAX_CUTS + MAX_PLACEMENTS)) {
            CompoundTag step = (CompoundTag) raw; keys(step, Set.of("Stage", "Kind", "Pos", "Before", "After"), Set.of("Removal"));
            Removal removal = null;
            if (step.contains("Removal")) {
                require(step, "Removal", Tag.TAG_COMPOUND); CompoundTag r = step.getCompound("Removal");
                keys(r, Set.of("Origin", "Family", "Adapter", "Version", "Source"), Set.of());
                removal = new Removal(enumeration(Origin.class, r, "Origin"), enumeration(Family.class, r, "Family"),
                        string(r, "Adapter", 256), string(r, "Version", 64), hash(r, "Source"));
            }
            steps.add(new Step(integer(step, "Stage"), enumeration(Kind.class, step, "Kind"), number(step, "Pos"),
                    palette(palette, integer(step, "Before"), used), palette(palette, integer(step, "After"), used), removal));
        }
        if (used.size() != palette.size()) throw invalid("Unused earthworks palette entry");
        var manifest = new PerimeterEarthworksManifest(header, observations, steps);
        if (!manifest.hash().equals(expectedHash)) throw invalid("Changed earthworks manifest digest");
        ListTag stages = list(tag, "Stages", MAX_STAGES);
        if (stages.size() != manifest.stageDigests().size()) throw invalid("Changed earthworks stage membership");
        for (int i = 0; i < stages.size(); i++) {
            CompoundTag stage = stages.getCompound(i); keys(stage, Set.of("Hash"), Set.of());
            if (!hash(stage, "Hash").equals(manifest.stageDigests().get(i))) throw invalid("Changed earthworks stage digest");
        }
        return manifest;
    }

    private static int paletteId(BlockState state, ListTag palette, Map<BlockState, Integer> ids) {
        Integer id = ids.get(state); if (id != null) return id;
        int next = palette.size(); if (next >= MAX_PALETTE) throw invalid("Earthworks palette limit");
        ids.put(state, next); palette.add(NbtUtils.writeBlockState(state)); return next;
    }
    private static BlockState palette(List<BlockState> palette, int index, Set<Integer> used) {
        if (index < 0 || index >= palette.size()) throw invalid("Invalid earthworks palette reference");
        used.add(index); return palette.get(index);
    }
    private static BlockState state(CompoundTag tag) {
        keys(tag, Set.of("Name"), Set.of("Properties")); ResourceLocation name = ResourceLocation.tryParse(string(tag, "Name", 256));
        if (name == null || !BuiltInRegistries.BLOCK.containsKey(name)) throw invalid("Unknown earthworks block state");
        if (tag.contains("Properties")) {
            require(tag, "Properties", Tag.TAG_COMPOUND); CompoundTag properties = tag.getCompound("Properties");
            if (properties.getAllKeys().size() > 32) throw invalid("Too many earthworks block properties");
            for (String key : properties.getAllKeys()) if (!bounded(key, 64) || !bounded(string(properties, key, 128), 128))
                throw invalid("Invalid earthworks block property");
        }
        BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag);
        if (!NbtUtils.writeBlockState(state).equals(tag)) throw invalid("Lossy earthworks block state"); return state;
    }
    private static <E extends Enum<E>> E enumeration(Class<E> type, CompoundTag tag, String key) {
        try { return Enum.valueOf(type, string(tag, key, 40)); }
        catch (RuntimeException bad) { throw invalid("Unknown earthworks enum: " + key); }
    }
    private static String hash(CompoundTag tag, String key) {
        String value = string(tag, key, 64); if (!digest(value)) throw invalid("Invalid earthworks hash"); return value;
    }
    private static UUID uuid(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_INT_ARRAY); if (!tag.hasUUID(key)) throw invalid("Invalid earthworks UUID"); return tag.getUUID(key);
    }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static String string(CompoundTag tag, String key, int max) {
        require(tag, key, Tag.TAG_STRING); String value = tag.getString(key);
        if (value.length() > max) throw invalid("Oversize earthworks string"); return value;
    }
    private static ListTag list(CompoundTag tag, String key, int max) {
        require(tag, key, Tag.TAG_LIST); ListTag list = (ListTag) tag.get(key);
        if (list.isEmpty() || list.size() > max || list.getElementType() != Tag.TAG_COMPOUND)
            throw invalid("Invalid earthworks list: " + key); return list;
    }
    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw invalid("Missing or wrongly typed earthworks field: " + key);
    }
    private static void keys(CompoundTag tag, Set<String> required, Set<String> optional) {
        if (tag == null || !tag.getAllKeys().containsAll(required)) throw invalid("Incomplete earthworks record");
        for (String key : tag.getAllKeys()) if (!required.contains(key) && !optional.contains(key)) throw invalid("Unexpected earthworks field");
    }
}
