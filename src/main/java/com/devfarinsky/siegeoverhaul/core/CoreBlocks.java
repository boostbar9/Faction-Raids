package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

@Mod.EventBusSubscriber(modid = SiegeOverhaul.MOD_ID)
public final class CoreBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SiegeOverhaul.MOD_ID);
    public static final RegistryObject<Block> CORE = BLOCKS.register("siege_core", CoreBlock::new);
    public static class CoreItem extends BlockItem {
        public CoreItem() { super(CORE.get(), new Item.Properties().stacksTo(1).rarity(Rarity.RARE)); }
        @Override public InteractionResult place(BlockPlaceContext context) {
            if (!context.getLevel().isClientSide && (!(context.getPlayer() instanceof ServerPlayer player)
                    || !SiegeCore.mayPlace(player, context.getClickedPos()))) {
                if (context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.literal(
                        "Place one Siege Core in your faction's Recruits claim in the Overworld. Remove your old core first; cores cannot move during a siege."), false);
                return InteractionResult.FAIL;
            }
            return super.place(context);
        }
    }
    public static class CoreBlock extends Block {
        public CoreBlock() { super(BlockBehaviour.Properties.of().strength(5, 3600000).lightLevel(s -> 10).pushReaction(PushReaction.BLOCK)); }
        @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack) {
            if (entity instanceof ServerPlayer player) SiegeCore.placed(player, pos);
        }
        @Override public boolean canEntityDestroy(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.entity.Entity entity) { return false; }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (player.isShiftKeyDown()) {
                if (level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> com.devfarinsky.siegeoverhaul.client.NativeRecruitsMenus.factions());
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (player instanceof ServerPlayer sp) {
                if (SiegeCore.canUse(sp, pos)) sp.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new CoreHireMenu(id, inv, pos), Component.literal("Siege Core • Recruit offers")));
                else sp.displayClientMessage(Component.literal("This core needs your faction's claim to recruit soldiers."), false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
    @SubscribeEvent public static void protectCore(BlockEvent.BreakEvent event) {
        if (event.getState().is(CORE.get()) && event.getPlayer() instanceof ServerPlayer player && !SiegeCore.canBreak(player, event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("Your faction's core must stay in place until the siege ends."), false);
        }
    }
}
