package com.devfarinsky.siegeoverhaul.camp;

import com.devfarinsky.siegeoverhaul.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.*;

/** Reuse the complete native job implementation while giving only the siege crew a night shift. */
public final class BuilderWorkShift extends Goal {
    private final Mob worker;
    private final Goal nativeGoal;
    private final java.lang.reflect.Method shouldWork,needsChest;
    private final java.lang.reflect.Field fleeing;
    private BuilderWorkShift(Mob worker,Goal original) throws ReflectiveOperationException {
        this.worker=worker;nativeGoal=original;
        shouldWork=worker.getClass().getMethod("shouldWork");needsChest=worker.getClass().getMethod("needsToGetToChest");fleeing=worker.getClass().getField("isFleeing");
        setFlags(original.getFlags());
    }
    public static void install(Mob worker) throws ReflectiveOperationException {
        var goals=new ArrayList<>(worker.goalSelector.getAvailableGoals());
        if(goals.stream().anyMatch(g->g.getGoal() instanceof BuilderWorkShift))return;
        for(var wrapped:goals)if(wrapped.getGoal().getClass().getName().equals("com.talhanation.workers.entities.ai.BuilderWorkGoal")) {
            var replacement=new BuilderWorkShift(worker,wrapped.getGoal());
            worker.goalSelector.removeGoal(wrapped.getGoal());worker.goalSelector.addGoal(wrapped.getPriority(),replacement);
            // Camp crew has no native home assignment. Bells in the defended settlement should not stop enemy construction.
            worker.goalSelector.removeAllGoals(g->g.getClass().getName().equals("com.talhanation.workers.entities.ai.WorkerGoHomeGoal")
                    || g.getClass().getName().equals("com.talhanation.workers.entities.ai.WorkerTakeCoverGoal"));
            return;
        }
    }
    @Override public boolean canUse() {
        if(!(worker.level() instanceof ServerLevel level) || worker.isNoAi() || !worker.isAlive())return false;
        String team=worker.getPersistentData().getString(ModConstants.Tags.CAMP_WORKER_TEAM);
        var raid=RaidSavedData.get(level.getServer()).raids.get(team);
        if(raid==null || !NativeCampConstruction.active(raid) || raid.pendingCampBlocks.isEmpty())return false;
        try {return !fleeing.getBoolean(worker) && Boolean.TRUE.equals(shouldWork.invoke(worker)) && !Boolean.TRUE.equals(needsChest.invoke(worker));}
        catch(ReflectiveOperationException ex){return false;}
    }
    @Override public boolean canContinueToUse(){return canUse();}
    @Override public void start(){nativeGoal.start();}
    @Override public void tick(){nativeGoal.tick();}
    @Override public void stop(){nativeGoal.stop();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
}
