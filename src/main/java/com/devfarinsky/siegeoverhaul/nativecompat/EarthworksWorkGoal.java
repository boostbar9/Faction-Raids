package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.core.WallBuilderAccess;
import com.talhanation.workers.entities.BuilderEntity;
import com.talhanation.workers.entities.ai.BuilderWorkGoal;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.ArrayList;
import java.util.function.Supplier;

/** One selector slot, retaining the exact original native/legacy goal and its priority. */
final class EarthworksWorkGoal extends Goal {
    private final BuilderEntity worker;
    private final Goal original;
    private final Supplier<Goal> grading;
    private Goal running;
    private boolean failed;

    EarthworksWorkGoal(BuilderEntity worker, Goal original, Supplier<Goal> grading) {
        this.worker=worker;this.original=original;this.grading=grading;setFlags(original.getFlags());
    }
    static boolean install(BuilderEntity worker, Supplier<Goal> grading) {
        if(worker.goalSelector==null)return false;
        var goals=new ArrayList<>(worker.goalSelector.getAvailableGoals());
        if(goals.size()>128)return false;
        var existing=goals.stream().filter(g->g.getGoal() instanceof EarthworksWorkGoal).toList();
        if(!existing.isEmpty())return existing.size()==1 && ((EarthworksWorkGoal)existing.get(0).getGoal()).worker==worker;
        var nativeGoals=goals.stream().filter(g->g.getGoal().getClass()==BuilderWorkGoal.class || g.getGoal() instanceof WallBuilderAccess).toList();
        if(nativeGoals.size()!=1)return false;
        var old=nativeGoals.get(0); if(old.isRunning() && worker.currentBuildArea!=null)return false; // Never interrupt an existing assignment.
        // GoalSelector.removeGoal owns the one native stop call for an idle-but-eligible original.
        var replacement=new EarthworksWorkGoal(worker,old.getGoal(),grading);
        worker.goalSelector.removeGoal(old.getGoal()); worker.goalSelector.addGoal(old.getPriority(),replacement); return true;
    }
    private Goal selected(){return NativeEarthworksJobs.selected(worker)?grading.get():original;}
    @Override public boolean canUse(){if(failed)return false;try{Goal next=selected();return next!=null && next.canUse();}catch(RuntimeException|LinkageError unavailable){failed=true;return false;}}
    @Override public boolean canContinueToUse(){if(failed||running==null)return false;try{return running==selected() && running.canContinueToUse();}catch(RuntimeException|LinkageError unavailable){failed=true;return false;}}
    @Override public boolean requiresUpdateEveryTick(){return NativeEarthworksJobs.selected(worker)||original.requiresUpdateEveryTick();}
    @Override public boolean isInterruptable(){return running==null||running.isInterruptable();}
    @Override public void start(){if(failed||running!=null)return;try{Goal next=selected();if(next!=null && next.canUse()){running=next;next.start();}}catch(RuntimeException|LinkageError unavailable){failed=true;}}
    @Override public void stop(){Goal old=running;running=null;if(old==null||failed)return;try{old.stop();}catch(RuntimeException|LinkageError unavailable){failed=true;}}
    @Override public void tick(){if(!canContinueToUse())return;try{running.tick();}catch(RuntimeException|LinkageError unavailable){failed=true;}}
}
