package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.*;
import java.util.List;

/** Finite hire-time supplies in native cargo slots; never refill existing workers. */
public final class WorkerStartingKit {
    private static final String MARKER = "SiegeWorkerOutfitted";
    private WorkerStartingKit() {}
    static List<ItemStack> kit(int role) {
        return switch (role) {
            case 4 -> List.of(new ItemStack(Items.DIAMOND_HOE), new ItemStack(Items.WATER_BUCKET),
                    new ItemStack(Items.WHEAT_SEEDS,32), new ItemStack(Items.BREAD,8));
            case 5 -> List.of(new ItemStack(Items.DIAMOND_AXE), new ItemStack(Items.OAK_SAPLING,16),
                    new ItemStack(Items.BREAD,8));
            case 6 -> List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_SHOVEL),
                    new ItemStack(Items.TORCH,32), new ItemStack(Items.COBBLESTONE,32), new ItemStack(Items.BREAD,8));
            case 7 -> List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.DIAMOND_AXE),
                    new ItemStack(Items.DIAMOND_SHOVEL), new ItemStack(Items.OAK_PLANKS,32),
                    new ItemStack(Items.COBBLESTONE,32), new ItemStack(Items.BREAD,8));
            case 8 -> List.of(new ItemStack(Items.COAL,16), new ItemStack(Items.BEEF,16), new ItemStack(Items.BREAD,8));
            case 9 -> List.of(new ItemStack(Items.BREAD,8)); // Couriers need cargo space, not a special tool.
            default -> List.of();
        };
    }
    public static void prepare(Mob worker, int role, SimpleContainer inventory) {
        if (role < CoreOffers.WORKER_START || role >= 10 || worker.getPersistentData().getBoolean(MARKER)) return;
        if (inventory.getContainerSize() <= 6) throw new IllegalStateException("Worker cargo inventory missing");
        SimpleContainer cargo = new SimpleContainer(inventory.getContainerSize()-6);
        for (int i=0;i<cargo.getContainerSize();i++) cargo.setItem(i,inventory.getItem(i+6).copy());
        for (ItemStack desired : kit(role)) {
            int missing=Math.max(0,desired.getCount()-inventory.countItem(desired.getItem()));
            if (missing>0 && !cargo.addItem(new ItemStack(desired.getItem(),missing)).isEmpty())
                throw new IllegalStateException("Worker starter kit does not fit");
        }
        for (int i=0;i<cargo.getContainerSize();i++) inventory.setItem(i+6,cargo.getItem(i));
        worker.getPersistentData().putBoolean(MARKER,true);
        inventory.setChanged();
    }
}
