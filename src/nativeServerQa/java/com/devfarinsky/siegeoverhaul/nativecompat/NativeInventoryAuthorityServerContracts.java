package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import com.devfarinsky.siegeoverhaul.compat.WorkersBridge;
import com.devfarinsky.siegeoverhaul.core.CoreBlocks;
import com.devfarinsky.siegeoverhaul.core.SiegeCore;
import com.mojang.authlib.GameProfile;
import com.talhanation.recruits.ClaimEvents;
import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.world.RecruitsClaim;
import com.talhanation.recruits.world.RecruitsFaction;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import com.talhanation.recruits.world.RecruitsTeamSaveData;
import com.talhanation.workers.entities.BuilderEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardSaveData;
import net.minecraftforge.common.util.FakePlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Direct native fixture setup in the isolated GameTest world, never a commissioning/AI test. */
final class NativeInventoryAuthorityServerContracts {
    private static final String FACTION = "server_supply", FOREIGN = "server_other";

    private NativeInventoryAuthorityServerContracts() {}

    static void verify(GameTestHelper helper, FakePlayer owner, FakePlayer outsider,
                       BuilderEntity builder, Map<String, Object> result) throws Exception {
        ServerLevel level = helper.getLevel();
        require(FactionEvents.recruitsFactionManager != null && ClaimEvents.recruitsClaimManager != null,
                "Actual native faction/claim managers are unavailable");
        require(owner.getTeam() == null && outsider.getTeam() == null
                        && FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION) == null
                        && FactionEvents.recruitsFactionManager.getFactionByStringID(FOREIGN) == null,
                "Inventory authority fixture identities already exist");
        require(level.getServer().getPlayerList().getPlayer(owner.getUUID()) == null
                        && level.getServer().getPlayerList().getPlayerCount() == 0 && !level.players().contains(owner),
                "Authority owner must not be in either connected player list");
        require(level.getServer().getProfileCache() == null, "Expected supported GameTest NO_SERVICES profile cache");
        Path directory = Path.of(System.getProperty("siegeoverhaul.nativeServerQa.directory")).toRealPath();
        require(directory.equals(Path.of("").toRealPath())
                        && directory.endsWith(Path.of("build", "native-server-qa", "server")),
                "Unsafe isolated profile-cache fixture directory");
        Path cacheFile = directory.resolve("inventory-authority-fixture-profiles.json");
        require(!Files.exists(cacheFile), "Refusing to reuse inventory-authority fixture profiles");
        AtomicInteger profileLookups = new AtomicInteger();
        // GameTest intentionally supplies NO_SERVICES. Inject this real local cache only into
        // the shared authority-policy seam; never change server services or authentication.
        GameProfileCache profiles = new GameProfileCache((names, agent, callback) -> {
            profileLookups.incrementAndGet();
            throw new AssertionError("Inventory authority attempted a name/network profile lookup");
        }, cacheFile.toFile());
        profiles.add(owner.getGameProfile());

        // Public native command-style setup. No faction menu, fee or authenticated packet is tested.
        FactionEvents.createTeam(false, owner, level, FACTION, "Server Supply QA", owner.getScoreboardName(),
                new ItemStack(Items.BLUE_BANNER), ChatFormatting.BLUE, (byte) 11);
        FactionEvents.createTeam(false, outsider, level, FOREIGN, "Server Other QA", outsider.getScoreboardName(),
                new ItemStack(Items.RED_BANNER), ChatFormatting.RED, (byte) 14);
        var faction = FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION);
        var foreign = FactionEvents.recruitsFactionManager.getFactionByStringID(FOREIGN);
        require(faction != null && foreign != null && faction.getMembers().size() == 1
                        && owner.getUUID().equals(faction.getMembers().get(0).getUUID())
                        && !faction.getMembers().get(0).isOnline(), "Native offline UUID roster was not created");

        BlockPos corePos = helper.absolutePos(new BlockPos(1, 1, 12));
        BlockPos sourcePos = corePos.offset(16, 0, 0);
        // One adjacent chunk is loaded only for setup; the distant authority probe must stay unloaded.
        level.getChunkAt(sourcePos);
        require(!new ChunkPos(corePos).equals(new ChunkPos(sourcePos)), "Core/source must have distinct claims");
        claim(level, faction, owner, corePos);
        var sourceClaim = claim(level, faction, owner, sourcePos);
        ClaimEvents.recruitsClaimManager.save(level);
        level.setBlock(sourcePos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(sourcePos, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(corePos, CoreBlocks.CORE.get().defaultBlockState(), 3);
        SiegeCore.placed(owner, corePos); // Direct core/anchor fixture initialization, not paid commissioning.
        String coreKey = "team:" + FACTION;
        var saved = RaidSavedData.get(level.getServer());
        require(SiegeCore.point(level.getServer(), coreKey) != null && saved.anchors.containsKey(coreKey),
                "Actual Core and SavedData anchor were not initialized");
        CompoundTag originalCore = saved.siegeCores.get(coreKey).copy();
        var originalAnchor = saved.anchors.get(coreKey);
        Set<BlockPos> sources = Set.of(sourcePos);
        int stock = builder.getInventory().countItem(Items.COBBLESTONE);
        result.put("fixtureSetup", "Public native faction/claim APIs, direct Core block and SiegeCore.placed; no commissioning or AI");
        result.put("ownerConnected", false);
        result.put("profileCacheSource", "Explicit isolated GameProfileCache dependency; GameTest server cache remains absent");
        String productionProblem = NativeInventoryAuthority.problem(level, builder, owner.getUUID(), coreKey, corePos, sources);
        require(productionProblem != null && productionProblem.contains("cached UUID identity"),
                "Production entry did not fail closed with GameTest NO_SERVICES: " + productionProblem);
        result.put("productionEntryWithoutProfileCache", productionProblem);
        Map<String, String> outcomes = new LinkedHashMap<>();
        result.put("outcomes", outcomes);
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "offline-native-owner-valid", outcomes);

        // Flush/read the actual anchor file, separately from native faction roster persistence.
        level.getDataStorage().save();
        var file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/" + RaidSavedData.DATA_NAME + ".dat");
        require(Files.isRegularFile(file), "Actual Core/anchor SavedData did not reach the isolated world disk");
        var disk = RaidSavedData.load(NbtIo.readCompressed(file.toFile()).getCompound("data"));
        require(originalCore.equals(disk.siegeCores.get(coreKey)) && originalAnchor.equals(disk.anchors.get(coreKey)),
                "Actual Core/anchor SavedData disk round trip changed authority context");
        result.put("coreAnchorDiskRoundTrip", "passed");

        var nativeFile = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data/" + RecruitsTeamSaveData.FILE_ID + ".dat");
        require(Files.isRegularFile(nativeFile), "Actual native faction SavedData did not reach disk");
        var nativeDisk = RecruitsTeamSaveData.load(NbtIo.readCompressed(nativeFile.toFile()).getCompound("data"));
        var diskFaction = nativeDisk.getTeams().get(FACTION);
        require(diskFaction != null && diskFaction.getMembers().isEmpty(),
                "Pinned native SavedData no longer exhibits the expected omitted-roster persistence boundary");
        result.put("nativeRosterMembersBeforeDisk", faction.getMembers().size());
        result.put("nativeRosterMembersAfterDisk", diskFaction.getMembers().size());
        var scoreboardFile = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("data/" + ScoreboardSaveData.FILE_ID + ".dat");
        require(Files.isRegularFile(scoreboardFile), "Actual scoreboard SavedData did not reach disk");
        var diskBoard = new Scoreboard();
        new ScoreboardSaveData(diskBoard).load(NbtIo.readCompressed(scoreboardFile.toFile()).getCompound("data"));
        require(diskBoard.getPlayersTeam(owner.getScoreboardName()) != null
                        && FACTION.equals(diskBoard.getPlayersTeam(owner.getScoreboardName()).getName()),
                "Persisted scoreboard lost the offline owner's native faction");
        result.put("scoreboardDiskRoundTrip", "passed");
        // Install the actual disk-decoded native dataset through its public loader. This is a
        // SavedData reload fixture, not a process restart or a hand-reconstructed membership list.
        RecruitsTeamSaveData.get(level).setTeams(nativeDisk.getTeams());
        FactionEvents.recruitsFactionManager.load(level);
        faction = FactionEvents.recruitsFactionManager.getFactionByStringID(FACTION);
        require(faction != null && faction.getMembers().isEmpty(), "Native manager did not reload the lossy disk data");
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "native-manager-disk-reload-valid", outcomes);

        FactionEvents.removeOfflinePlayerFromTeam(owner, owner.getScoreboardName(), level);
        require(level.getScoreboard().getPlayersTeam(owner.getScoreboardName()) == null,
                "Public native offline removal did not remove current scoreboard membership");
        expect(profiles, level, builder, owner, coreKey, corePos, sources, "scoreboard", "removed-native-member", outcomes);
        // Explicit fixture restoration: the public native join command requires a connected player.
        faction.addMember(owner.getUUID(), owner.getScoreboardName());
        level.getScoreboard().addPlayerToTeam(owner.getScoreboardName(), level.getScoreboard().getPlayerTeam(FACTION));
        FactionEvents.addPlayerToData(level, FACTION, 1, owner.getScoreboardName());
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "membership-restored", outcomes);

        faction.getMembers().get(0).setName("StaleSupplyName");
        expect(profiles, level, builder, owner, coreKey, corePos, sources, "owner membership is ambiguous", "stale-native-roster-name", outcomes);
        faction.getMembers().get(0).setName(owner.getScoreboardName());
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "native-roster-name-restored", outcomes);
        faction.addMember(UUID.fromString("b108ee44-edf1-4b5f-99b9-871d2c38f9be"), owner.getScoreboardName());
        expect(profiles, level, builder, owner, coreKey, corePos, sources, "conflicts with another native UUID", "conflicting-native-roster-uuid", outcomes);
        faction.removeMember(owner.getScoreboardName());
        faction.addMember(owner.getUUID(), owner.getScoreboardName());
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "native-roster-uuid-restored", outcomes);
        profiles.add(new GameProfile(owner.getUUID(), "StaleSupplyName"));
        expect(profiles, level, builder, owner, coreKey, corePos, sources, "owner membership is ambiguous", "stale-cached-profile-name", outcomes);
        profiles.add(owner.getGameProfile());
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "identity-conflicts-restored", outcomes);

        var board = level.getScoreboard();
        board.addPlayerToTeam(owner.getScoreboardName(), board.getPlayerTeam(FOREIGN));
        expect(profiles, level, builder, owner, coreKey, corePos, sources, "scoreboard", "scoreboard-transferred", outcomes);
        board.addPlayerToTeam(owner.getScoreboardName(), board.getPlayerTeam(FACTION));
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "scoreboard-restored", outcomes);

        builder.setOwnerUUID(java.util.Optional.of(outsider.getUUID()));
        expect(profiles, level, builder, owner, coreKey, corePos, sources, "ownership", "worker-owner-transferred", outcomes);
        builder.setOwnerUUID(java.util.Optional.of(owner.getUUID()));
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "worker-owner-restored", outcomes);

        sourceClaim.setOwnerFaction(foreign);
        ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, sourceClaim);
        require(SiegeCore.point(level.getServer(), coreKey) != null, "Source transfer invalidated the separate original Core");
        expect(profiles, level, builder, owner, coreKey, corePos, sources, "foreign claim", "foreign-source-claim", outcomes);
        sourceClaim.setOwnerFaction(faction);
        ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, sourceClaim);
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "source-claim-restored", outcomes);

        BlockPos unloaded = new BlockPos(20_000_016, corePos.getY(), 20_000_016);
        require(!level.hasChunkAt(unloaded), "Unloaded inventory source probe already loaded");
        expect(profiles, level, builder, owner, coreKey, corePos, Set.of(unloaded), "loaded safe world", "unloaded-source-denied", outcomes);
        require(!level.hasChunkAt(unloaded), "Inventory authority force-loaded the denied source");

        level.setBlock(corePos, Blocks.AIR.defaultBlockState(), 3);
        expect(profiles, level, builder, owner, coreKey, corePos, sources, "original protected inventory core", "original-core-lost", outcomes);
        level.setBlock(corePos, CoreBlocks.CORE.get().defaultBlockState(), 3);
        saved.siegeCores.put(coreKey, originalCore.copy());
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "original-core-restored", outcomes);
        saved.anchors.remove(coreKey);
        expect(profiles, level, builder, owner, coreKey, corePos, sources, "original protected inventory core", "original-anchor-lost", outcomes);
        saved.anchors.put(coreKey, originalAnchor);
        saved.setDirty();
        expect(profiles, level, builder, owner, coreKey, corePos, sources, null, "valid-context-restored", outcomes);
        require(profileLookups.get() == 0 && level.getServer().getProfileCache() == null,
                "Authority fixture performed profile lookups or changed GameTest services");
        result.put("profileRepositoryLookups", profileLookups.get());
        require(builder.getInventory().countItem(Items.COBBLESTONE) == stock
                        && owner.getUUID().equals(WorkersBridge.readWorkerOwner(builder))
                        && level.getServer().getPlayerList().getPlayer(owner.getUUID()) == null
                        && level.getServer().getPlayerList().getPlayerCount() == 0 && !level.players().contains(owner),
                "Authority checks changed stock, current worker ownership or connection state");
    }

    private static RecruitsClaim claim(ServerLevel level, RecruitsFaction faction, FakePlayer owner, BlockPos pos) {
        var chunk = new ChunkPos(pos);
        require(ClaimEvents.recruitsClaimManager.getClaim(chunk) == null, "Inventory fixture claim already exists");
        var claim = new RecruitsClaim("Server inventory authority QA", faction);
        claim.setCenter(chunk);
        claim.setPlayer(new RecruitsPlayerInfo(owner.getUUID(), owner.getScoreboardName(), faction));
        claim.addChunk(chunk);
        claim.setHealth(claim.getMaxHealth());
        ClaimEvents.recruitsClaimManager.addOrUpdateClaim(level, claim);
        require(ClaimEvents.recruitsClaimManager.getClaim(chunk) == claim, "Native inventory claim update was rejected");
        return claim;
    }

    private static void expect(GameProfileCache profiles, ServerLevel level, BuilderEntity builder, FakePlayer owner, String key,
                               BlockPos core, Set<BlockPos> sources, String problem, String id, Map<String, String> outcomes) {
        String actual = NativeInventoryAuthority.problem(level, builder, owner.getUUID(), key, core, sources, () -> profiles);
        require(problem == null ? actual == null : actual != null && actual.startsWith("Paused:") && actual.contains(problem),
                "Inventory authority " + id + " expected " + (problem == null ? "allow" : problem) + ", got " + actual);
        outcomes.put(id, actual == null ? "allowed" : actual);
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
