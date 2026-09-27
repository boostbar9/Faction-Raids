package com.devfarinsky.siegeoverhaul.raid;

import com.devfarinsky.siegeoverhaul.ModConstants;
import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.siege.SiegeDeployment;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** A temporary travelling pace, including native formation movement; combat keeps its normal speed. */
public final class MarchPace {
    static final UUID ID=UUID.fromString("72501361-b6e2-4689-a7af-15f540e64e21");
    private static final AttributeModifier PACE=new AttributeModifier(ID,"Siege marching pace",0.20,
            AttributeModifier.Operation.MULTIPLY_BASE);
    private MarchPace() {}

    public static void update(Mob mob, RaidSavedData.RaidState raid, Vec3 objective) {
        var speed=mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed==null) return;
        var tag=mob.getPersistentData();
        boolean marching=raid.preparationTicks<=0 && !raid.coreCaptured && !raid.offlinePauseAnnounced
                && mob.isAlive() && !mob.isNoAi() && !mob.isPassenger() && !mob.isVehicle()
                && (mob.getTarget()==null || !mob.getTarget().isAlive())
                && mob.distanceToSqr(objective)>64*64
                && raid.teamKey.equals(tag.getString(ModConstants.Tags.RAID_TEAM))
                && !raid.campGuards.contains(mob.getUUID())
                && !tag.contains(ModConstants.Tags.CAMP_WORKER_TEAM)
                && !tag.contains(SiegeDeployment.OPERATOR_ASSIGNED) && !tag.contains(SiegeDeployment.TEAM_TAG)
                && (raid.bridgePlan==null || !mob.getUUID().equals(raid.bridgePlan.builder));
        if (marching) {
            if (speed.getModifier(ID)==null) speed.addTransientModifier(PACE);
        } else speed.removeModifier(ID);
    }
}
