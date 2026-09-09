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
        public CoreBlock() { super(BlockBehaviour.Properties.of().strength(5, 3600000).noOcclusion().lightLevel(s -> 14).pushReaction(PushReaction.BLOCK)); }
        private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE=net.minecraft.world.phys.shapes.Shapes.or(
                Block.box(0,0,0,16,5,16),Block.box(3,5,3,13,16,13));
        @Override public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){return SHAPE;}
        @Override public void animateTick(BlockState state,Level level,BlockPos pos,net.minecraft.util.RandomSource random){
            double angle=level.getGameTime()*.07+random.nextDouble()*.25;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.ENCHANT,pos.getX()+.5+Math.cos(angle)*.55,pos.getY()+1.05,pos.getZ()+.5+Math.sin(angle)*.55,0,.02,0);
            if(random.nextInt(4)==0)level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,pos.getX()+.5,pos.getY()+1.05,pos.getZ()+.5,0,.01,0);
        }
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
                        (id, inv, p) -> new CoreHireMenu(id, inv, pos), Component.literal("Siege Core • Arcane Command")));
                else {
                    var data=com.devfarinsky.siegeoverhaul.RaidSavedData.get(sp.server);
                    var raid=data.raids.get(SiegeCore.key(sp));
                    if(raid!=null && pos.equals(EnemyCore.position(raid)))sp.displayClientMessage(Component.literal("Enemy command core: outnumber its defenders within "+com.devfarinsky.siegeoverhaul.RaidConfig.CORE_CAPTURE_RADIUS.get()+" blocks for "+com.devfarinsky.siegeoverhaul.RaidConfig.CORE_RECAPTURE_SECONDS.get()+" seconds to end the invasion."),false);
                    else sp.displayClientMessage(Component.literal("This core needs your faction's claim to open command services."), false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }
    @SubscribeEvent public static void protectCore(BlockEvent.BreakEvent event) {
        if (event.getState().is(CORE.get()) && event.getPlayer() instanceof ServerPlayer player && (!SiegeCore.canBreak(player, event.getPos()) || com.devfarinsky.siegeoverhaul.RaidSavedData.get(player.server).raids.values().stream()
                .anyMatch(raid -> event.getPos().equals(EnemyCore.position(raid))))) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("Your faction's core must stay in place until the siege ends."), false);
        }
    }
}
