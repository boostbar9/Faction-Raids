package com.devfarinsky.siegeoverhaul.items;

import com.devfarinsky.siegeoverhaul.core.SiegeYard;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A paid, one-use order for a friendly siege engine and its Recruits crew.
 *
 * <p>Purchasing and deployment are intentionally separate. Siege Cores are
 * commonly installed inside command rooms, where automatically searching
 * around the Core cannot find enough clearance for a vehicle. The kit lets
 * the player carry the order outdoors and choose the exact deployment spot.</p>
 */
public final class CrewDeploymentItem extends Item {
    private final int crewIndex;

    public CrewDeploymentItem(int crewIndex, Properties properties) {
        super(properties);
        this.crewIndex = crewIndex;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!isPlacementFace(context.getClickedFace())) {
            if (context.getPlayer() instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal(
                        "Aim at the top face of the center ground block, then use the kit again."));
            }
            return InteractionResult.FAIL;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return context.getLevel().isClientSide
                    ? InteractionResult.SUCCESS
                    : InteractionResult.FAIL;
        }

        BlockPos center = deploymentCenter(context.getClickedPos(), context.getClickedFace());
        if (!SiegeYard.deploy(player, center, crewIndex)) {
            // Never consume a paid order when the selected ground, dependency
            // integration or native vehicle spawn rejects the deployment.
            return InteractionResult.FAIL;
        }

        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** The vehicle's center occupies the block immediately beyond the clicked face. */
    public static BlockPos deploymentCenter(BlockPos clicked, Direction face) {
        return clicked.relative(face);
    }

    /** Deployment is intentionally anchored to the top of a ground block. */
    public static boolean isPlacementFace(Direction face) {
        return face == Direction.UP;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.siegeoverhaul.crew_deployment_kit.tooltip.line1")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.siegeoverhaul.crew_deployment_kit.tooltip.line2")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
