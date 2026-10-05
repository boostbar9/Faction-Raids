package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksJournal;
import com.devfarinsky.siegeoverhaul.core.PerimeterEarthworksManifest;
import com.talhanation.recruits.inventory.RecruitSimpleContainer;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import com.talhanation.workers.entities.workarea.BuildArea;
import com.talhanation.workers.world.BuildBlock;
import com.talhanation.workers.world.NeededItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NativeEarthworksReviewTest extends MinecraftTestSupport {
    @Test void canonicalCompoundsCannotCollideThroughRawStringDelimitersAndIgnoreKeyInsertionOrder() {
        CompoundTag a = new CompoundTag(), b = new CompoundTag(), reordered = new CompoundTag();
        a.putString("a", "x;1:b=8:y"); b.putString("a", "x"); b.putString("b", "y");
        reordered.putString("b", "y"); reordered.putString("a", "x");
        assertNotEquals(canonical(a), canonical(b)); assertEquals(canonical(b), canonical(reordered));
        a = new CompoundTag(); b = new CompoundTag(); a.putString("a=8:x;1:b", "y"); b.putString("a", "x;1:b=8:y");
        assertNotEquals(canonical(a), canonical(b));
    }

    @Test void canonicalListsNestedCapabilitiesAndPrimitiveTypesRemainDistinct() {
        ListTag one = new ListTag(), two = new ListTag(); one.add(StringTag.valueOf("x;8:y"));
        two.add(StringTag.valueOf("x")); two.add(StringTag.valueOf("y")); assertNotEquals(canonical(one), canonical(two));
        CompoundTag a = new CompoundTag(), b = new CompoundTag(), caps = new CompoundTag(); caps.put("items", one);
        a.put("ForgeCaps", caps); a.putString("tag", "payload"); b.putString("ForgeCaps", canonical(caps) + ";3:tag=payload");
        assertNotEquals(canonical(a), canonical(b));
        List<Tag> different = List.of(ByteTag.valueOf((byte)1), IntTag.valueOf(1), LongTag.valueOf(1), StringTag.valueOf("1"),
                new ByteArrayTag(new byte[]{1, 2}), new IntArrayTag(new int[]{1, 2}), new LongArrayTag(new long[]{1, 2}));
        assertEquals(different.size(), different.stream().map(NativeEarthworksReviewTest::canonical).distinct().count());
        assertNotEquals(canonical(new IntArrayTag(new int[]{1, 23})), canonical(new IntArrayTag(new int[]{12, 3})));
    }

    @Test void emptyTypedListsAndAllUtf16CodeUnitsAreLossless() throws Exception {
        assertNotEquals(canonical(emptyList(Tag.TAG_STRING)), canonical(emptyList(Tag.TAG_INT)));
        assertNotEquals(canonical(StringTag.valueOf("\ud800")), canonical(StringTag.valueOf("\ud801")));
        assertNotEquals(canonical(StringTag.valueOf("\ud800")), canonical(StringTag.valueOf("?")));
        assertNotEquals(canonical(StringTag.valueOf("\ud83d\ude42")), canonical(StringTag.valueOf("\ud83d\ude43")));
        assertNotEquals(frameHash("\ud800"), frameHash("\ud801"));
        assertNotEquals(frameHash("\ud800"), frameHash("?"));
        assertEquals(canonical(StringTag.valueOf("plain\0;:=\n🙂")), canonical(StringTag.valueOf("plain\0;:=\n🙂")));
    }

    @Test void nativePreparationCannotSilentlyWaitBehindAnotherLowerAvailableMaterial() {
        var higherMissing = build(0, 65, Blocks.COBBLESTONE.defaultBlockState());
        var lowerAvailable = build(1, 64, Blocks.OAK_PLANKS.defaultBlockState());
        assertFalse(NativeEarthworksAdapter.nativePreparationOrder(List.of(higherMissing, lowerAvailable)));
        assertTrue(NativeEarthworksAdapter.nativePreparationOrder(List.of(lowerAvailable, higherMissing)));
        assertFalse(NativeEarthworksAdapter.nativePreparationOrder(List.of(build(0, 64, Blocks.COBBLESTONE.defaultBlockState()), lowerAvailable)));
        assertTrue(NativeEarthworksAdapter.nativePreparationOrder(List.of(build(0, 65, Blocks.DIRT.defaultBlockState()), build(1, 64, Blocks.DIRT.defaultBlockState()))));
        // Real pinned BuilderWorkGoal method, mocked world/worker. This reproduces the native scheduling trap, not gameplay QA.
        var worker = mock(BuilderEntity.class); var level = mock(ServerLevel.class); var area = mock(BuildArea.class);
        worker.tickCount = 5; worker.currentBuildArea = area; worker.neededItems = new ArrayList<>();
        when(worker.getCommandSenderWorld()).thenReturn(level);
        var inventory = new RecruitSimpleContainer(15, worker); when(worker.getInventory()).thenReturn(inventory);
        inventory.setItem(6, new ItemStack(Items.OAK_PLANKS, 4));
        area.stackToPlace = new Stack<>(); area.stackToPlaceMultiBlock = new Stack<>();
        area.stackToPlace.push(new BuildBlock(BlockPos.of(higherMissing.pos()), higherMissing.after()));
        area.stackToPlace.push(new BuildBlock(BlockPos.of(lowerAvailable.pos()), lowerAvailable.after()));
        when(area.getArea()).thenReturn(new AABB(0, 64, 0, 2, 66, 1));
        when(area.getRequiredMaterials()).thenAnswer(call -> new ArrayList<>(List.of(new ItemStack(Items.COBBLESTONE, 1), new ItemStack(Items.OAK_PLANKS, 1))));
        doCallRealMethod().when(worker).addNeededItem(any(NeededItem.class));
        var goal = new BuilderWorkGoal(worker); goal.setState(BuilderWorkGoal.State.PREPARE_PLACE_BLOCKS); goal.tick();
        assertTrue(worker.neededItems.isEmpty(), "Native prepare sees lower wood stock and requests no missing cobblestone");
        area.stackToPlace.removeIf(block -> block.getState().is(Blocks.OAK_PLANKS));
        goal.setState(BuilderWorkGoal.State.PREPARE_PLACE_BLOCKS); goal.tick();
        assertEquals(1, worker.neededItems.size()); assertTrue(worker.neededItems.get(0).matches(new ItemStack(Items.COBBLESTONE)));
        assertTrue(ProtectedTransferCapacity.trustedMatcher(worker.neededItems.get(0).matcher));
        verify(level, never()).setBlockAndUpdate(any(), any());
    }

    @Test void modifiedLiveMiningTagCannotSelectAnUnreviewedNativeTool() {
        BlockState dirt = mock(BlockState.class); when(dirt.is(Blocks.DIRT)).thenReturn(true);
        when(dirt.is(BlockTags.MINEABLE_WITH_SHOVEL)).thenReturn(true); assertTrue(WorkersEarthworksPort.reviewedDirtToolDispatch(dirt));
        when(dirt.is(BlockTags.MINEABLE_WITH_SHOVEL)).thenReturn(false); when(dirt.is(BlockTags.MINEABLE_WITH_PICKAXE)).thenReturn(true);
        assertFalse(WorkersEarthworksPort.reviewedDirtToolDispatch(dirt));
    }

    @Test void groundInventoryAndGroundGroundObjectAliasesAreRejectedBeforeMutation() {
        var seen = new IdentityHashMap<ItemStack, Boolean>(); ItemStack stack = new ItemStack(Items.DIRT, 4);
        WorkersEarthworksPort.claimStackIdentity(seen, stack); // Independent inventory object.
        assertThrows(IllegalStateException.class, () -> WorkersEarthworksPort.claimStackIdentity(seen, stack)); // Ground aliases inventory.
        seen.clear(); WorkersEarthworksPort.claimStackIdentity(seen, stack); // First ground entity.
        assertThrows(IllegalStateException.class, () -> WorkersEarthworksPort.claimStackIdentity(seen, stack)); // Second ground entity aliases it.
        assertDoesNotThrow(() -> WorkersEarthworksPort.claimStackIdentity(seen, stack.copy()));
        assertEquals(4, stack.getCount());
    }

    @Test void existingExperienceIsRefusedWithoutAssumingUuidAndValueCaptureMergedPickupCount() {
        var orb = mock(ExperienceOrb.class);
        assertThrows(IllegalStateException.class, () -> WorkersEarthworksPort.requireNoExperience(List.of(orb)));
        assertThrows(IllegalStateException.class, () -> WorkersEarthworksPort.requireNoExperience(List.of(orb)));
        verify(orb, never()).getValue(); // Every existing orb is refused, regardless of invisible same-value merge count changes.
        assertDoesNotThrow(() -> WorkersEarthworksPort.requireNoExperience(List.of()));
    }

    @Test void owningGoalPausesOnThrowingStoreAndDoesNotStartOrCleanUpNativeWork() {
        var manifest = NativeEarthworksAdapterTest.manifest(); var store = mock(NativeEarthworksAdapter.Store.class);
        var port = mock(WorkersEarthworksPort.class); var nativeGoal = mock(BuilderWorkGoal.class);
        when(port.nativeGoal()).thenReturn(nativeGoal); when(nativeGoal.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        var goal = new LocalEarthworksGoal(manifest, store, port);
        when(store.read()).thenThrow(new IllegalStateException("unreadable store"));
        assertFalse(goal.canUse()); assertDoesNotThrow(goal::start); assertDoesNotThrow(goal::tick); assertDoesNotThrow(goal::stop);
        verify(nativeGoal, never()).start(); verify(nativeGoal, never()).stop(); verify(port, never()).invokeExact(any());
        assertTrue(goal.status().contains("unavailable"));
    }

    @Test void throwingRouteAndPartialNativeStartNeverDispatchOrRepeatCleanup() {
        var manifest = NativeEarthworksAdapterTest.manifest(); var store = mock(NativeEarthworksAdapter.Store.class);
        var journal = PerimeterEarthworksJournal.begin(manifest, new PerimeterEarthworksJournal.Binding(UUID.randomUUID(), "a".repeat(64), "b".repeat(64)));
        when(store.read()).thenReturn(new NativeEarthworksAdapter.State(journal, null)); when(store.recoveryAdmissionEstablished()).thenReturn(true);
        var port = mock(WorkersEarthworksPort.class); var nativeGoal = mock(BuilderWorkGoal.class);
        when(port.nativeGoal()).thenReturn(nativeGoal); when(nativeGoal.getFlags()).thenReturn(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        when(port.nativeEligible()).thenReturn(true); when(nativeGoal.canContinueToUse()).thenReturn(true);
        var goal = new LocalEarthworksGoal(manifest, store, port); goal.start();
        doThrow(new IllegalStateException("route unavailable")).when(port).navigate(any(), any());
        assertDoesNotThrow(goal::tick); assertFalse(goal.canUse()); assertDoesNotThrow(goal::stop);
        verify(port, never()).invokeExact(any()); verify(nativeGoal, never()).stop();
        var failedStart = new LocalEarthworksGoal(manifest, store, port);
        doThrow(new LinkageError("partial start")).when(nativeGoal).start();
        assertDoesNotThrow(failedStart::start); assertDoesNotThrow(failedStart::start); assertDoesNotThrow(failedStart::stop);
        verify(nativeGoal, times(2)).start(); // One successful first goal start and one failed new start; no retry.
        verify(nativeGoal, never()).stop();
    }

    @Test void initialFullBlockAdapterRefusesRecipeBaseItemsAndChangedEffectiveStates() {
        assertTrue(WorkersEarthworksPort.exactFullBlockMaterial(Blocks.COBBLESTONE.defaultBlockState(), Items.COBBLESTONE, false));
        assertTrue(WorkersEarthworksPort.exactFullBlockMaterial(Blocks.DIRT.defaultBlockState(), Items.DIRT, true));
        assertFalse(WorkersEarthworksPort.exactFullBlockMaterial(Blocks.STONE_BRICKS.defaultBlockState(), Items.STONE, false));
        assertFalse(WorkersEarthworksPort.exactFullBlockMaterial(Blocks.OAK_PLANKS.defaultBlockState(), Items.OAK_LOG, true));
        assertFalse(WorkersEarthworksPort.exactFullBlockMaterial(Blocks.COBBLESTONE.defaultBlockState(), null, false));
        assertFalse(WorkersEarthworksPort.exactFullBlockMaterial(Blocks.OAK_SLAB.defaultBlockState(), Items.OAK_SLAB, false));
    }

    @Test void actualPinnedParserAbiAndMaterialProbeMatchTheDisassembledPlacementBranch() throws Exception {
        assertNull(WorkersEarthworksPort.pinnedMaterialApiProblem());
        Class<?> type = com.talhanation.workers.world.BuildBlockParse.class;
        assertThrows(NoSuchMethodException.class, () -> type.getMethod("parseBlock", net.minecraft.world.level.block.Block.class, net.minecraft.world.level.Level.class));
        List<BlockState> targets = List.of(Blocks.DIRT.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
                Blocks.STONE_BRICKS.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState(),
                Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.TOP),
                Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
        for (BlockState target : targets) {
            var parsed = com.talhanation.workers.world.BuildBlockParse.parseBlock(target.getBlock());
            var item = parsed.getItem(); assertNotNull(item);
            var placed = parsed.wasParsed() && item instanceof net.minecraft.world.item.BlockItem block ? block.getBlock().defaultBlockState() : target;
            System.out.println("EARTHWORKS_PINNED_PARSER target=" + target + " item=" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)
                    + " wasParsed=" + parsed.wasParsed() + " effective=" + placed + " preserves=" + target.equals(placed));
            if (target.is(Blocks.DIRT) || target.is(Blocks.COBBLESTONE)) assertTrue(WorkersEarthworksPort.exactFullBlockMaterial(target, item, parsed.wasParsed()));
            if (target.is(Blocks.OAK_SLAB)) assertFalse(WorkersEarthworksPort.exactFullBlockMaterial(target, item, parsed.wasParsed()), "Initial target contract still refuses slab states");
        }
        try (var in = type.getResourceAsStream("BuildBlockParse.class")) {
            assertNotNull(in); System.out.println("EARTHWORKS_PINNED_PARSER_CLASS_SHA256=" + java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())));
        }
    }

    private static String canonical(Tag tag) { return WorkersEarthworksPort.canonical(tag); }
    private static ListTag emptyList(byte elementType) throws Exception {
        byte[] bytes = {10, 0, 0, 9, 0, 1, 'x', elementType, 0, 0, 0, 0, 0};
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes))).getList("x", elementType);
    }
    private static String frameHash(String metadata) {
        return NativeEarthworksAdapter.frameHash(new NativeEarthworksAdapter.Frame(Map.of(0L, new NativeEarthworksAdapter.Cell(Blocks.AIR.defaultBlockState(), 0)),
                Map.of(new NativeEarthworksAdapter.Stock("minecraft:dirt", metadata, 0), 1), Map.of(), Map.of()));
    }
    private static PerimeterEarthworksManifest.Step build(int x, int y, BlockState after) {
        return new PerimeterEarthworksManifest.Step(0, PerimeterEarthworksManifest.Kind.BUILD, new BlockPos(x, y, 0).asLong(), Blocks.AIR.defaultBlockState(), after, null);
    }
}
