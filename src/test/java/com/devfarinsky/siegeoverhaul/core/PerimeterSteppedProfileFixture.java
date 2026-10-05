package com.devfarinsky.siegeoverhaul.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedProfile.*;

/** Synthetic ground samples on a five-wide rectangular ring; no Minecraft world/native proof. */
final class PerimeterSteppedProfileFixture {
    private PerimeterSteppedProfileFixture() {}
    static Loop ring(int component, int loop, int firstId, int offsetX, int offsetZ, int straightBands,
                     boolean outer, boolean gates, int[] heights) {
        int last = 5 + straightBands * 3, n = 4 * (straightBands + 1);
        if (heights.length != n) throw new IllegalArgumentException("One fixture height per band");
        List<Band> bands = new ArrayList<>();
        for (int side = 0; side < 4; side++) {
            int cx = side == 1 || side == 2 ? last : 0, cz = side >= 2 ? last : 0;
            add(bands, firstId, offsetX, offsetZ, cx, cz, 5, 5, Kind.CORNER, null, heights[bands.size()], 5);
            for (int run = 0; run < straightBands; run++) {
                int low = 5 + 3 * run, reverse = last - 3 - 3 * run;
                int x = side == 0 ? low : side == 1 ? last : side == 2 ? reverse : 0;
                int z = side == 0 ? 0 : side == 1 ? low : side == 2 ? last : reverse;
                Facing facing = gates && run == straightBands / 2 ? Facing.values()[side] : null;
                int width = side % 2 == 0 ? 3 : 5, depth = side % 2 == 0 ? 5 : 3;
                add(bands, firstId, offsetX, offsetZ, x, z, width, depth,
                        facing == null ? Kind.STRAIGHT : Kind.GATE, facing, heights[bands.size()], 3);
            }
        }
        List<Seam> seams = new ArrayList<>();
        for (int i = 0; i < bands.size(); i++) {
            Band from = bands.get(i), to = bands.get((i + 1) % bands.size());
            List<Lane> lanes = new ArrayList<>();
            for (Sample a : from.samples()) for (Sample b : to.samples())
                if (a.role() == Role.WALL && b.role() == Role.WALL && Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) == 1)
                    lanes.add(new Lane(a.x(), a.z(), b.x(), b.z()));
            lanes.sort(Comparator.comparingInt(Lane::fromX).thenComparingInt(Lane::fromZ));
            Lane first = lanes.get(0);
            Facing direction = first.toX() > first.fromX() ? Facing.EAST : first.toX() < first.fromX() ? Facing.WEST
                    : first.toZ() > first.fromZ() ? Facing.SOUTH : Facing.NORTH;
            seams.add(new Seam(from.id(), to.id(), direction, lanes));
        }
        return new Loop(component, loop, outer, bands, seams);
    }
    private static void add(List<Band> bands, int firstId, int offsetX, int offsetZ, int x, int z, int width, int depth,
                            Kind kind, Facing facing, int height, int length) {
        List<Sample> samples = new ArrayList<>();
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++)
            samples.add(new Sample(offsetX + x + dx, offsetZ + z + dz, height, Role.WALL, true));
        if (kind == Kind.GATE) for (int along = 0; along < 3; along++) for (int across = 1; across <= 3; across++) {
            int ix = facing == Facing.EAST ? x - across : facing == Facing.WEST ? x + width - 1 + across : x + along;
            int iz = facing == Facing.NORTH ? z + depth - 1 + across : facing == Facing.SOUTH ? z - across : z + along;
            int ox = facing == Facing.EAST ? x + width - 1 + across : facing == Facing.WEST ? x - across : x + along;
            int oz = facing == Facing.NORTH ? z - across : facing == Facing.SOUTH ? z + depth - 1 + across : z + along;
            samples.add(new Sample(offsetX + ix, offsetZ + iz, height, Role.INSIDE_GATE_PAD, false));
            samples.add(new Sample(offsetX + ox, offsetZ + oz, height, Role.OUTSIDE_GATE_PAD, false));
        }
        bands.add(new Band(firstId + bands.size(), length, kind, facing, samples, true));
    }
    static int[] level(int bands, int y) { int[] result = new int[bands]; java.util.Arrays.fill(result, y); return result; }
    static Limits limits(int cut, int fill, long operations, long cuts, long fills, boolean gates) {
        return new Limits(cut, fill, -63, 314, 4096, 20_480, operations, cuts, fills, gates);
    }
}
