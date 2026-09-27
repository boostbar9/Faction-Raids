package com.devfarinsky.siegeoverhaul.camp;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.stream.Collectors;

/** Saved construction cells accept legacy IDs or IDs followed by explicit block properties. */
public final class CampBlockState {
    private static final Map<String, Optional<BlockState>> CACHE = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Optional<BlockState>> entry) {
            return size() > 512;
        }
    };
    private CampBlockState() {}

    public static synchronized Optional<BlockState> decode(String encoded) {
        if (encoded == null || encoded.length() > 1024) return Optional.empty();
        return CACHE.computeIfAbsent(encoded, CampBlockState::parse);
    }

    private static Optional<BlockState> parse(String encoded) {
        int bracket = encoded.indexOf('[');
        String name = bracket < 0 ? encoded : encoded.substring(0, bracket);
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || !ForgeRegistries.BLOCKS.containsKey(id)) return Optional.empty();
        BlockState state = ForgeRegistries.BLOCKS.getValue(id).defaultBlockState();
        if (bracket < 0) return Optional.of(state);
        if (!encoded.endsWith("]")) return Optional.empty();
        String properties = encoded.substring(bracket + 1, encoded.length() - 1);
        Set<String> seen = new HashSet<>();
        for (String entry : properties.split(",", -1)) {
            String[] pair = entry.split("=", -1);
            if (pair.length != 2 || !seen.add(pair[0])) return Optional.empty();
            Property<?> property = state.getBlock().getStateDefinition().getProperty(pair[0]);
            if (property == null) return Optional.empty();
            state = apply(state, property, pair[1]);
            if (state == null) return Optional.empty();
        }
        return Optional.of(state);
    }

    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(null);
    }

    public static String encode(BlockState state) {
        String id = Objects.requireNonNull(ForgeRegistries.BLOCKS.getKey(state.getBlock())).toString();
        if (state.getProperties().isEmpty()) return id;
        return id + "[" + state.getProperties().stream().sorted(Comparator.comparing(Property::getName))
                .map(p -> assignment(state, p)).collect(Collectors.joining(",")) + "]";
    }

    private static <T extends Comparable<T>> String assignment(BlockState state, Property<T> property) {
        return property.getName() + "=" + property.getName(state.getValue(property));
    }

    public static boolean matches(BlockState current, String planned) {
        return decode(planned).map(expected -> matches(current, expected)).orElse(false);
    }

    private static boolean matches(BlockState current, BlockState expected) {
        if (current.getBlock() != expected.getBlock()) return false;
        // These properties are derived from neighbors, not the builder's placement direction.
        for (var property : current.getProperties()) {
            boolean connection = (current.getBlock() instanceof FenceBlock || current.getBlock() instanceof WallBlock
                    || current.getBlock() instanceof IronBarsBlock)
                    && Set.of("north", "south", "east", "west", "up").contains(property.getName());
            boolean stairShape = current.getBlock() instanceof StairBlock && property.getName().equals("shape");
            if (!connection && !stairShape && !current.getValue(property).equals(expected.getValue(property))) return false;
        }
        return true;
    }

    public static boolean matchesRecord(BlockState current, CompoundTag record) {
        if (record.contains("PlacedState")) return current.isAir() || matches(current, record.getString("PlacedState"));
        return CampTerrain.matchesPlaced(current, record.getString("Placed"));
    }
}
