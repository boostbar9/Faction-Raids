package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.*;
import com.devfarinsky.siegeoverhaul.compat.EnemyHiringProtection;
import com.devfarinsky.siegeoverhaul.waves.WaveComposer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;

/** One saved hero opportunity per wave, using the same roster, gear and abilities as player hires. */
public final class EnemyHeroes {
    private EnemyHeroes() {}
    public static boolean active(Mob mob) {
        return mob.getPersistentData().getBoolean("SiegeEnemyHero")
                && CoreHiring.isHero(mob.getPersistentData().getInt("SiegeHeroRole"))
                && EnemyHiringProtection.enemy(mob);
    }
    public static void plan(RaidSavedData.RaidState state, int wave, int totalWaves, int size,
                            boolean enabled, int chance, boolean commander, boolean illusioners, RandomSource random) {
        state.enemyHeroRole = -1;
        state.enemyHeroSlot = -1;
        if (!enabled || wave < 2 || chance <= 0 || random.nextInt(100) >= chance) return;
        for (int index = 0; index < size; index++) {
            if (!WaveComposer.reserved(wave, totalWaves, index, commander, illusioners)) {
                state.enemyHeroSlot = index;
                state.enemyHeroRole = CoreOffers.hero(random.nextInt(100));
                return;
            }
        }
    }
    public static int roleAt(RaidSavedData.RaidState state, int slot) {
        return state != null && slot >= 0 && slot == state.enemyHeroSlot && CoreHiring.isHero(state.enemyHeroRole)
                ? state.enemyHeroRole : -1;
    }
    public static boolean prepare(Mob mob, int role) {
        if (!CoreHiring.isHero(role) || !RecruitsBridge.isRecruitSoldier(mob)) return true;
        try {
            CoreHiring.prepareHero(mob, role, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FactionLogger.LOG.warn("Enemy hero initialization failed before world registration", ex);
            mob.discard();
            return false;
        }
    }
    static String defendedTeam(LivingEntity entity) {
        var tag = entity.getPersistentData();
        for (String key : new String[]{ModConstants.Tags.RAID_TEAM, ModConstants.Tags.CAMP_WORKER_TEAM,
                com.devfarinsky.siegeoverhaul.camp.CampGuards.TEAM_TAG}) {
            String team = tag.getString(key);
            if (!team.isBlank()) return team;
        }
        return "";
    }
    static boolean ally(Mob hero, LivingEntity other) {
        String team = defendedTeam(hero);
        return !team.isBlank() && team.equals(defendedTeam(other)) && EnemyHiringProtection.enemy(other);
    }
    static boolean defender(Mob hero, LivingEntity other) {
        if (!(hero.level() instanceof ServerLevel level)) return false;
        String team = defendedTeam(hero);
        var anchor = RaidSavedData.get(level.getServer()).anchors.get(team);
        return anchor != null && defender(other, team, anchor.members());
    }
    static boolean defender(LivingEntity other, String team, Iterable<UUID> members) {
        if (!other.isAlive() || other.isSpectator() || EnemyHiringProtection.enemy(other)) return false;
        if (other instanceof Player player) {
            if (player.isCreative()) return false;
            for (UUID member : members) if (member.equals(player.getUUID())) return true;
            return false;
        }
        return RecruitsBridge.belongsTo(other, team, members);
    }
    /** Summons share raid cleanup, ownership and population accounting. Never exceed raid caps. */
    static boolean prepareShadow(ServerLevel level, Mob hero, Mob wolf) {
        if (!active(hero)) return true;
        var data = RaidSavedData.get(level.getServer());
        String team = defendedTeam(hero);
        var raid = data.raids.get(team);
        if (raid == null || raid.raiders.size() + raid.campGuards.size() >= RaidConfig.MAX_ACTIVE_RAIDERS.get()) return false;
        int total = data.raids.values().stream().mapToInt(r -> r.raiders.size() + r.campGuards.size() + r.campWorkers.size()).sum();
        if (total >= RaidConfig.MAX_GLOBAL_RAIDERS.get()) return false;
        wolf.getPersistentData().putString(ModConstants.Tags.RAID_TEAM, team);
        wolf.setPersistenceRequired();
        return level.noCollision(wolf) && level.getFluidState(wolf.blockPosition()).isEmpty();
    }
}
