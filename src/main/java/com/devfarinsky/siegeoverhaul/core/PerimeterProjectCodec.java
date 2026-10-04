package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded lossless SavedData codec. Never use this payload in an item/entity/client packet. */
final class PerimeterProjectCodec {
    private static final int MAX_PALETTE = PerimeterStageLayout.MAX_RESERVED + 4;
    private PerimeterProjectCodec() {}

    static CompoundTag save(PerimeterProject project) {
        var h = project.header(); CompoundTag out = new CompoundTag();
        out.putInt("Version", PerimeterProject.FORMAT_VERSION);
        out.putUUID("Project", h.projectId()); out.putLong("Generation", h.generation());
        out.putUUID("Owner", h.owner()); out.putUUID("Builder", h.builder());
        out.putString("CoreKey", h.coreKey()); out.putLong("Core", h.originalCore().asLong()); out.putString("Faction", h.faction());
        out.putInt("Material", h.material()); out.putInt("FeeVersion", h.feeVersion()); out.putInt("Price", h.quotedPrice());
        out.putString("Review", h.reviewedFingerprint()); out.putString("Hash", project.manifestHash());
        out.putLongArray("Territory", h.territory().stream().mapToLong(ChunkPos::toLong).sorted().toArray());
        out.putString("State", project.state().name()); out.putLong("Revision", project.revision()); out.putInt("Active", project.activeStage());
        out.putString("Blocker", project.blocker());
        if (project.recoveryState() != null) out.putString("RecoveryState", project.recoveryState().name());
        ListTag palette = new ListTag(); Map<BlockState, Integer> paletteIds = new HashMap<>();
        ListTag targets = new ListTag(), clearances = new ListTag();
        project.targets().keySet().stream().sorted().forEach(cell -> {
            CompoundTag value = new CompoundTag(); value.putLong("Pos", cell);
            value.putInt("Target", paletteId(project.targets().get(cell), palette, paletteIds));
            value.putInt("Before", paletteId(project.before().get(cell), palette, paletteIds)); targets.add(value);
        });
        project.clearanceBefore().keySet().stream().sorted().forEach(cell -> {
            CompoundTag value = new CompoundTag(); value.putLong("Pos", cell);
            value.putInt("Before", paletteId(project.clearanceBefore().get(cell), palette, paletteIds)); clearances.add(value);
        });
        out.put("Palette", palette); out.put("Targets", targets); out.put("Clearance", clearances);
        ListTag columns = new ListTag();
        project.plan().columns().stream().sorted(java.util.Comparator.comparingLong(c -> c.base().asLong())).forEach(column -> {
            CompoundTag value = new CompoundTag(); value.putLong("Base", column.base().asLong()); value.putInt("Support", column.supportDepth());
            value.putInt("Inward", column.inwardDistance()); value.putInt("Component", column.componentId()); columns.add(value);
        });
        out.put("Columns", columns); out.putLong("Min", project.plan().min().asLong()); out.putLong("Max", project.plan().max().asLong());
        out.putString("Layout", project.layout().digest());
        ListTag stages = new ListTag();
        for (var stage : project.stages()) {
            CompoundTag value = new CompoundTag(); value.putInt("Index", stage.index()); value.putUUID("Area", stage.areaId());
            value.putString("Digest", stage.digest()); value.putString("LayoutDigest", stage.layout().digest());
            value.putLongArray("Columns", stage.layout().columns().stream().mapToLong(Long::longValue).toArray());
            value.putLong("Min", stage.layout().min().asLong()); value.putLong("Max", stage.layout().max().asLong());
            value.putLong("Origin", stage.layout().origin().asLong()); stages.add(value);
        }
        out.put("Stages", stages);
        ListTag receipts = new ListTag();
        for (var receipt : project.receipts()) {
            CompoundTag value = commonReceipt(receipt.projectId(), receipt.generation(), receipt.manifestHash());
            value.putInt("Index", receipt.stageIndex()); value.putUUID("Area", receipt.areaId());
            value.putString("Digest", receipt.stageDigest()); value.putInt("Targets", receipt.verifiedTargets()); receipts.add(value);
        }
        out.put("Receipts", receipts);
        if (project.payment() != null) {
            var p = project.payment(); CompoundTag payment = commonReceipt(p.projectId(), p.generation(), p.manifestHash());
            payment.putInt("FeeVersion", p.feeVersion()); payment.putInt("Price", p.quotedPrice());
            payment.putInt("Debited", p.debited()); payment.putBoolean("Creative", p.creative()); out.put("Payment", payment);
        }
        return out;
    }

    static PerimeterProject load(CompoundTag tag) {
        keys(tag, Set.of("Version", "Project", "Generation", "Owner", "Builder", "CoreKey", "Core", "Faction", "Material",
                "FeeVersion", "Price", "Review", "Hash", "Territory", "State", "Revision", "Active", "Blocker", "Palette",
                "Targets", "Clearance", "Columns", "Min", "Max", "Layout", "Stages", "Receipts"), Set.of("RecoveryState", "Payment"));
        if (integer(tag, "Version") != PerimeterProject.FORMAT_VERSION) throw invalid("Unknown perimeter manifest version");
        String expectedHash = hash(tag, "Hash");
        long[] territory = longs(tag, "Territory", PerimeterTerritory.MAX_CHUNKS, false);
        Set<ChunkPos> chunks = new HashSet<>();
        for (long packed : territory) if (!chunks.add(new ChunkPos(packed))) throw invalid("Duplicate reviewed territory");
        var header = new PerimeterProject.Header(uuid(tag, "Project"), number(tag, "Generation"), uuid(tag, "Owner"), uuid(tag, "Builder"),
                string(tag, "CoreKey", 256), BlockPos.of(number(tag, "Core")), string(tag, "Faction", 256), integer(tag, "Material"),
                hash(tag, "Review"), chunks, integer(tag, "FeeVersion"), integer(tag, "Price"));
        ListTag savedPalette = list(tag, "Palette", MAX_PALETTE, false);
        List<BlockState> palette = new ArrayList<>(); Set<BlockState> distinctStates = new HashSet<>();
        for (Tag entry : savedPalette) {
            BlockState state = blockState((CompoundTag) entry);
            if (!distinctStates.add(state)) throw invalid("Duplicate saved block-state palette entry");
            palette.add(state);
        }
        ListTag savedTargets = list(tag, "Targets", PerimeterStageLayout.MAX_TARGETS, false);
        ListTag savedClearance = list(tag, "Clearance", PerimeterStageLayout.MAX_RESERVED, true);
        if ((long) savedTargets.size() + savedClearance.size() > PerimeterStageLayout.MAX_RESERVED) throw invalid("Oversize perimeter reservation");
        Map<Long, BlockState> targets = new LinkedHashMap<>(), before = new LinkedHashMap<>(), clearance = new LinkedHashMap<>();
        Map<Long, String> materials = new LinkedHashMap<>(); Map<String, Integer> counts = new LinkedHashMap<>();
        Set<Integer> usedPalette = new HashSet<>();
        for (Tag raw : savedTargets) {
            CompoundTag cell = (CompoundTag) raw; keys(cell, Set.of("Pos", "Target", "Before"), Set.of());
            long pos = number(cell, "Pos"); BlockState target = palette(palette, integer(cell, "Target"), usedPalette);
            if (targets.putIfAbsent(pos, target) != null) throw invalid("Duplicate target cell");
            before.put(pos, palette(palette, integer(cell, "Before"), usedPalette));
            String name = String.valueOf(BuiltInRegistries.BLOCK.getKey(target.getBlock())); materials.put(pos, name); counts.merge(name, 1, Integer::sum);
        }
        for (Tag raw : savedClearance) {
            CompoundTag cell = (CompoundTag) raw; keys(cell, Set.of("Pos", "Before"), Set.of());
            long pos = number(cell, "Pos");
            if (targets.containsKey(pos) || clearance.putIfAbsent(pos, palette(palette, integer(cell, "Before"), usedPalette)) != null)
                throw invalid("Duplicate or overlapping clearance cell");
        }
        if (usedPalette.size() != palette.size()) throw invalid("Unused saved block-state palette");
        ListTag savedColumns = list(tag, "Columns", PerimeterStageLayout.MAX_COLUMNS, false);
        List<PerimeterBlueprint.Column> columns = new ArrayList<>();
        for (Tag raw : savedColumns) {
            CompoundTag column = (CompoundTag) raw; keys(column, Set.of("Base", "Support", "Inward", "Component"), Set.of());
            columns.add(new PerimeterBlueprint.Column(BlockPos.of(number(column, "Base")), integer(column, "Support"),
                    integer(column, "Inward"), integer(column, "Component")));
        }
        var plan = new PerimeterBlueprint.Plan(materials, columns, clearance.keySet(), BlockPos.of(number(tag, "Min")),
                BlockPos.of(number(tag, "Max")), List.of(), List.of(), counts, List.of());
        ListTag savedStages = list(tag, "Stages", PerimeterStageLayout.MAX_STAGES, false);
        List<List<Long>> memberships = new ArrayList<>(); int columnCount = 0;
        for (Tag raw : savedStages) {
            CompoundTag stage = (CompoundTag) raw;
            keys(stage, Set.of("Index", "Area", "Digest", "LayoutDigest", "Columns", "Min", "Max", "Origin"), Set.of());
            if (integer(stage, "Index") != memberships.size()) throw invalid("Changed stage order");
            long[] members = longs(stage, "Columns", PerimeterStageLayout.MAX_COLUMNS, false);
            columnCount += members.length;
            if (columnCount > columns.size()) throw invalid("Duplicated saved stage membership");
            memberships.add(java.util.Arrays.stream(members).boxed().toList());
        }
        var layout = PerimeterStageLayout.restoreLayout(memberships, plan);
        if (!layout.digest().equals(hash(tag, "Layout"))) throw invalid("Changed perimeter stage layout");
        ListTag savedReceipts = list(tag, "Receipts", savedStages.size(), true);
        List<PerimeterProject.StageReceipt> receipts = new ArrayList<>();
        for (Tag raw : savedReceipts) {
            CompoundTag receipt = (CompoundTag) raw;
            keys(receipt, Set.of("Project", "Generation", "Hash", "Index", "Area", "Digest", "Targets"), Set.of());
            receipts.add(new PerimeterProject.StageReceipt(uuid(receipt, "Project"), number(receipt, "Generation"), hash(receipt, "Hash"),
                    integer(receipt, "Index"), uuid(receipt, "Area"), hash(receipt, "Digest"), integer(receipt, "Targets")));
        }
        PerimeterProject.PaymentReceipt payment = null;
        if (tag.contains("Payment")) {
            require(tag, "Payment", Tag.TAG_COMPOUND); CompoundTag p = tag.getCompound("Payment");
            keys(p, Set.of("Project", "Generation", "Hash", "FeeVersion", "Price", "Debited", "Creative"), Set.of());
            require(p, "Creative", Tag.TAG_BYTE);
            if (p.getByte("Creative") != 0 && p.getByte("Creative") != 1) throw invalid("Invalid creative payment receipt");
            payment = new PerimeterProject.PaymentReceipt(uuid(p, "Project"), number(p, "Generation"), hash(p, "Hash"),
                    integer(p, "FeeVersion"), integer(p, "Price"), integer(p, "Debited"), p.getBoolean("Creative"));
        }
        var state = state(tag, "State");
        var recovery = tag.contains("RecoveryState") ? state(tag, "RecoveryState") : null;
        var project = PerimeterProject.restore(header, plan, layout, targets, before, clearance, state, recovery,
                integer(tag, "Active"), number(tag, "Revision"), receipts, payment, string(tag, "Blocker", 256), expectedHash);
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < savedStages.size(); i++) {
            CompoundTag saved = savedStages.getCompound(i); var actual = project.stages().get(i);
            UUID id = uuid(saved, "Area");
            if (!ids.add(id) || !actual.areaId().equals(id) || !actual.digest().equals(hash(saved, "Digest"))
                    || !actual.layout().digest().equals(hash(saved, "LayoutDigest"))
                    || actual.layout().min().asLong() != number(saved, "Min") || actual.layout().max().asLong() != number(saved, "Max")
                    || actual.layout().origin().asLong() != number(saved, "Origin")) throw invalid("Changed native stage identity or bounds");
        }
        return project;
    }

    private static int paletteId(BlockState state, ListTag palette, Map<BlockState, Integer> ids) {
        Integer existing = ids.get(state); if (existing != null) return existing;
        int id = palette.size(); if (id >= MAX_PALETTE) throw invalid("Oversize block-state palette");
        ids.put(state, id); palette.add(NbtUtils.writeBlockState(state)); return id;
    }
    private static BlockState palette(List<BlockState> values, int index, Set<Integer> used) {
        if (index < 0 || index >= values.size()) throw invalid("Invalid saved state reference"); used.add(index); return values.get(index);
    }
    private static BlockState blockState(CompoundTag tag) {
        keys(tag, Set.of("Name"), Set.of("Properties"));
        ResourceLocation name = ResourceLocation.tryParse(string(tag, "Name", 256));
        if (name == null || !BuiltInRegistries.BLOCK.containsKey(name)) throw invalid("Unknown original block state");
        if (tag.contains("Properties")) {
            require(tag, "Properties", Tag.TAG_COMPOUND); CompoundTag properties = tag.getCompound("Properties");
            if (properties.getAllKeys().size() > 32) throw invalid("Too many original state properties");
            for (String key : properties.getAllKeys()) if (!PerimeterProject.bounded(key, 64)
                    || !PerimeterProject.bounded(string(properties, key, 128), 128)) throw invalid("Invalid original state property");
        }
        BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag);
        if (!NbtUtils.writeBlockState(state).equals(tag)) throw invalid("Original block state cannot be restored losslessly");
        return state;
    }
    private static CompoundTag commonReceipt(UUID project, long generation, String hash) {
        CompoundTag out = new CompoundTag(); out.putUUID("Project", project); out.putLong("Generation", generation); out.putString("Hash", hash); return out;
    }
    private static PerimeterProject.State state(CompoundTag tag, String key) {
        try { return PerimeterProject.State.valueOf(string(tag, key, 40)); }
        catch (RuntimeException invalid) { throw invalid("Unknown saved perimeter state"); }
    }
    private static String hash(CompoundTag tag, String key) {
        String value = string(tag, key, 64); if (!PerimeterProject.digest(value)) throw invalid("Invalid digest: " + key); return value;
    }
    private static UUID uuid(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_INT_ARRAY); if (!tag.hasUUID(key)) throw invalid("Invalid UUID: " + key); return tag.getUUID(key);
    }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static String string(CompoundTag tag, String key, int max) {
        require(tag, key, Tag.TAG_STRING); String value = tag.getString(key); if (value.length() > max) throw invalid("Oversize field: " + key); return value;
    }
    private static long[] longs(CompoundTag tag, String key, int max, boolean empty) {
        require(tag, key, Tag.TAG_LONG_ARRAY); long[] values = tag.getLongArray(key);
        if ((!empty && values.length == 0) || values.length > max) throw invalid("Invalid list length: " + key); return values;
    }
    private static ListTag list(CompoundTag tag, String key, int max, boolean empty) {
        require(tag, key, Tag.TAG_LIST); Tag raw = tag.get(key); ListTag values = (ListTag) raw;
        if ((!empty && values.isEmpty()) || values.size() > max || !values.isEmpty() && values.getElementType() != Tag.TAG_COMPOUND)
            throw invalid("Invalid compound list: " + key); return values;
    }
    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw invalid("Missing or wrongly typed field: " + key);
    }
    private static void keys(CompoundTag tag, Set<String> required, Set<String> optional) {
        if (tag == null || !tag.getAllKeys().containsAll(required)) throw invalid("Incomplete perimeter record");
        for (String key : tag.getAllKeys()) if (!required.contains(key) && !optional.contains(key)) throw invalid("Unexpected perimeter record field");
    }
    private static IllegalArgumentException invalid(String reason) { return PerimeterProject.invalid(reason); }
}
