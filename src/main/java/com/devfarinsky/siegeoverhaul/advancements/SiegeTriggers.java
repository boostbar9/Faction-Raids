package com.devfarinsky.siegeoverhaul.advancements;

import com.devfarinsky.siegeoverhaul.SiegeOverhaul;
import com.google.gson.JsonObject;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Central registry for the mod's custom advancement triggers.
 *
 * <p>Custom {@link SimpleCriterionTrigger} classes let advancement JSON in
 * {@code data/siegeoverhaul/advancements/} listen for siege events that
 * vanilla has no concept of: winning a raid, felling a commander, surviving
 * a specific endless wave, discovering a faction, or clearing a raid with
 * zero breached blocks or zero deaths. Each trigger is a static singleton
 * registered at mod init and fired from the appropriate site in
 * {@link com.devfarinsky.siegeoverhaul.RaidEvents}.</p>
 *
 * <p>Two shapes of trigger live here:
 * <ul>
 *   <li>{@link SimpleSiegeTrigger} — no fields, just fires. Used for
 *       "did the player do this thing" checks like winning a raid or
 *       killing a raider.</li>
 *   <li>{@link FactionSiegeTrigger} — carries an optional faction id.
 *       Advancement JSON can require {@code "faction": "hollowfang"} to
 *       fire only for that faction, or omit it to accept any. Used for
 *       faction-scoped goals like Regicide (all 5 commanders) and
 *       Know Thy Enemy (all 5 factions discovered).</li>
 *   <li>{@link WaveSiegeTrigger} — carries a wave threshold. Used for
 *       endless-siege wave milestones so one advancement can require
 *       {@code "wave": 25} without needing a separate trigger per wave.</li>
 * </ul>
 * </p>
 */
public final class SiegeTriggers {
    private SiegeTriggers() {}

    public static final CountingSiegeTrigger RAID_WON = new CountingSiegeTrigger("raid_won",
            "SiegeOverhaulRaidWins");
    public static final SimpleSiegeTrigger RAIDER_KILLED = new SimpleSiegeTrigger("raider_killed");
    public static final SimpleSiegeTrigger WAVE_SURVIVED = new SimpleSiegeTrigger("wave_survived");
    public static final SimpleSiegeTrigger NO_BREACH_VICTORY = new SimpleSiegeTrigger("no_breach_victory");
    public static final SimpleSiegeTrigger NO_DEATH_VICTORY = new SimpleSiegeTrigger("no_death_victory");
    public static final SimpleSiegeTrigger ENDLESS_STARTED = new SimpleSiegeTrigger("endless_started");
    public static final FactionSiegeTrigger COMMANDER_KILLED = new FactionSiegeTrigger("commander_killed");
    public static final FactionSiegeTrigger FACTION_DISCOVERED = new FactionSiegeTrigger("faction_discovered");
    public static final WaveSiegeTrigger ENDLESS_WAVE_REACHED = new WaveSiegeTrigger("endless_wave_reached");

    /**
     * Registered from mod bootstrap so advancement JSON can resolve
     * {@code "trigger": "siegeoverhaul:raid_won"} against our class.
     * Vanilla ignores unknown triggers and the whole advancement file
     * silently fails to load, so registration must run before any
     * datapack reload.
     */
    public static void register() {
        CriteriaTriggers.register(RAID_WON);
        // Note: RAID_WON is a CountingSiegeTrigger. It counts total wins in
        // the player's persistent NBT so advancements can require "win 10
        // raids" via a "count" field in JSON without spawning throwaway
        // items or loot tables.
        CriteriaTriggers.register(RAIDER_KILLED);
        CriteriaTriggers.register(WAVE_SURVIVED);
        CriteriaTriggers.register(NO_BREACH_VICTORY);
        CriteriaTriggers.register(NO_DEATH_VICTORY);
        CriteriaTriggers.register(ENDLESS_STARTED);
        CriteriaTriggers.register(COMMANDER_KILLED);
        CriteriaTriggers.register(FACTION_DISCOVERED);
        CriteriaTriggers.register(ENDLESS_WAVE_REACHED);
    }

    // ---- Simple ------------------------------------------------------------

    /**
     * Fires without a matching payload. Advancement JSON either has the
     * criterion or it doesn't; there's nothing to filter on.
     */
    public static final class SimpleSiegeTrigger extends SimpleCriterionTrigger<SimpleSiegeTrigger.Instance> {
        private final ResourceLocation id;

        SimpleSiegeTrigger(String path) {
            this.id = new ResourceLocation(SiegeOverhaul.MOD_ID, path);
        }

        @Override public @NotNull ResourceLocation getId() { return id; }

        @Override
        protected @NotNull Instance createInstance(@NotNull JsonObject json,
                                                    @NotNull ContextAwarePredicate player,
                                                    @NotNull DeserializationContext ctx) {
            return new Instance(id, player);
        }

        public void trigger(ServerPlayer player) {
            this.trigger(player, instance -> true);
        }

        public static final class Instance extends AbstractCriterionTriggerInstance {
            Instance(ResourceLocation id, ContextAwarePredicate player) { super(id, player); }
        }
    }

    // ---- Faction-scoped ----------------------------------------------------

    /**
     * Fires with a faction id string ("hollowfang", "blackbay", ...). JSON
     * may pin a specific faction or accept any. Used for Regicide (kill all
     * 5 commanders, tracked as a 5-requirement goal) and Know Thy Enemy
     * (discover all 5 factions).
     */
    public static final class FactionSiegeTrigger extends SimpleCriterionTrigger<FactionSiegeTrigger.Instance> {
        private final ResourceLocation id;

        FactionSiegeTrigger(String path) {
            this.id = new ResourceLocation(SiegeOverhaul.MOD_ID, path);
        }

        @Override public @NotNull ResourceLocation getId() { return id; }

        @Override
        protected @NotNull Instance createInstance(@NotNull JsonObject json,
                                                    @NotNull ContextAwarePredicate player,
                                                    @NotNull DeserializationContext ctx) {
            String faction = json.has("faction") ? GsonHelper.getAsString(json, "faction") : null;
            return new Instance(id, player, faction);
        }

        public void trigger(ServerPlayer player, String faction) {
            String matchAgainst = faction == null ? "" : faction;
            this.trigger(player, inst -> inst.matches(matchAgainst));
        }

        public static final class Instance extends AbstractCriterionTriggerInstance {
            private final String faction; // null means "any faction"

            Instance(ResourceLocation id, ContextAwarePredicate player, String faction) {
                super(id, player);
                this.faction = faction;
            }

            public boolean matches(String candidate) {
                return faction == null || faction.equalsIgnoreCase(candidate);
            }
        }
    }

    // ---- Counting ----------------------------------------------------------

    /**
     * Fires with a total-count context stored in the player's persistent
     * NBT under {@code counterKey}. Every {@link #trigger(ServerPlayer)}
     * call bumps the counter by 1 and re-fires so any advancement JSON
     * matching {@code "count": N} unlocks once the player has hit that
     * threshold. Persistent NBT survives death/respawn/dimension change,
     * so lifetime counts are honored across play sessions.
     */
    public static final class CountingSiegeTrigger extends SimpleCriterionTrigger<CountingSiegeTrigger.Instance> {
        private final ResourceLocation id;
        private final String counterKey;

        CountingSiegeTrigger(String path, String counterKey) {
            this.id = new ResourceLocation(SiegeOverhaul.MOD_ID, path);
            this.counterKey = counterKey;
        }

        @Override public @NotNull ResourceLocation getId() { return id; }

        @Override
        protected @NotNull Instance createInstance(@NotNull JsonObject json,
                                                    @NotNull ContextAwarePredicate player,
                                                    @NotNull DeserializationContext ctx) {
            int count = json.has("count") ? GsonHelper.getAsInt(json, "count") : 1;
            return new Instance(id, player, count);
        }

        public void trigger(ServerPlayer player) {
            net.minecraft.nbt.CompoundTag data = player.getPersistentData()
                    .getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            int nextCount = data.getInt(counterKey) + 1;
            data.putInt(counterKey, nextCount);
            player.getPersistentData()
                    .put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, data);
            this.trigger(player, inst -> inst.matches(nextCount));
        }

        public static final class Instance extends AbstractCriterionTriggerInstance {
            private final int minCount;

            Instance(ResourceLocation id, ContextAwarePredicate player, int minCount) {
                super(id, player);
                this.minCount = minCount;
            }

            public boolean matches(int candidate) {
                return candidate >= minCount;
            }
        }
    }

    // ---- Wave-scoped -------------------------------------------------------

    /**
     * Fires with a wave number. JSON may require a minimum wave via the
     * {@code "wave"} field so a single advancement type covers "reach
     * endless wave 10," "reach 25," and any other milestone the tree
     * decides to add later.
     */
    public static final class WaveSiegeTrigger extends SimpleCriterionTrigger<WaveSiegeTrigger.Instance> {
        private final ResourceLocation id;

        WaveSiegeTrigger(String path) {
            this.id = new ResourceLocation(SiegeOverhaul.MOD_ID, path);
        }

        @Override public @NotNull ResourceLocation getId() { return id; }

        @Override
        protected @NotNull Instance createInstance(@NotNull JsonObject json,
                                                    @NotNull ContextAwarePredicate player,
                                                    @NotNull DeserializationContext ctx) {
            int wave = json.has("wave") ? GsonHelper.getAsInt(json, "wave") : 1;
            return new Instance(id, player, wave);
        }

        public void trigger(ServerPlayer player, int wave) {
            this.trigger(player, inst -> inst.matches(wave));
        }

        public static final class Instance extends AbstractCriterionTriggerInstance {
            private final int minWave;

            Instance(ResourceLocation id, ContextAwarePredicate player, int minWave) {
                super(id, player);
                this.minWave = minWave;
            }

            public boolean matches(int candidate) {
                return candidate >= minWave;
            }
        }
    }
}
