package com.devfarinsky.siegeoverhaul.nativecompat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import com.mojang.authlib.GameProfile;
import com.talhanation.recruits.world.RecruitsFaction;
import com.talhanation.recruits.world.RecruitsPlayerInfo;
import net.minecraft.world.scores.Scoreboard;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeInventoryAuthorityTest extends MinecraftTestSupport {
    private static final UUID OWNER = UUID.fromString("eeafed51-67b1-45e7-a72b-cb5d1d8dfe62");

    @Test void offlineOwnerUsesPersistedUuidRosterAndCurrentScoreboardTogether() {
        var faction = faction(); var board = board();
        assertFalse(faction.getMembers().get(0).isOnline());
        assertNull(NativeInventoryAuthority.membershipProblem(faction, board, OWNER, "builders", profile()));
        faction.getMembers().get(0).setOnline(true);
        assertNull(NativeInventoryAuthority.membershipProblem(faction, board, OWNER, "builders", profile()));
    }

    @Test void staleRosterCannotAuthorizeAfterScoreboardTransfer() {
        var board = board(); board.addPlayerToTeam("BuilderOwner", board.addPlayerTeam("other"));
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction(), board, OWNER, "builders", profile()));
    }

    @Test void staleScoreboardCannotAuthorizeRemovedUuidOrSameNameDifferentPlayer() {
        var faction = faction(); faction.getMembers().clear();
        faction.addMember(UUID.randomUUID(), "BuilderOwner");
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction, board(), OWNER, "builders", profile()));
    }

    @Test void nativeRosterLossAfterReloadDoesNotInventAnOfflineRequirement() {
        var faction = faction(); faction.getMembers().clear();
        assertNull(NativeInventoryAuthority.membershipProblem(faction, board(), OWNER, "builders", profile()));
        var board = board(); board.removePlayerFromTeam("BuilderOwner", board.getPlayerTeam("builders"));
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction, board, OWNER, "builders", profile()));
    }

    @Test void missingWrongUuidOrRenamedCachedProfileFailsClosed() {
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction(), board(), OWNER, "builders", null));
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction(), board(), OWNER, "builders",
                new GameProfile(UUID.randomUUID(), "BuilderOwner")));
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction(), board(), OWNER, "builders",
                new GameProfile(OWNER, "RenamedOwner")));
        var faction = faction(); faction.getMembers().clear();
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction, board(), OWNER, "builders",
                new GameProfile(OWNER, "RenamedOwner")));
    }

    @Test void duplicateUuidNullEntryAndOversizedRosterFailClosed() {
        var faction = faction(); faction.getMembers().add(new RecruitsPlayerInfo(OWNER, "BuilderOwner"));
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction, board(), OWNER, "builders", profile()));
        faction = faction(); faction.getMembers().add(null);
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction, board(), OWNER, "builders", profile()));
        faction = faction();
        while (faction.getMembers().size() <= NativeInventoryAuthority.MAX_MEMBERS)
            faction.getMembers().add(new RecruitsPlayerInfo(UUID.randomUUID(), "Other"));
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction, board(), OWNER, "builders", profile()));
    }

    @Test void unknownAndWrongFactionCannotUseTheMembershipSeam() {
        assertNotNull(NativeInventoryAuthority.membershipProblem(null, board(), OWNER, "builders", profile()));
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction(), board(), OWNER, "other", profile()));
        assertNotNull(NativeInventoryAuthority.membershipProblem(faction(), null, OWNER, "builders", profile()));
    }

    @Test void invalidContextsStopBeforeAnyWorldOrNativeLookup() {
        assertNotNull(NativeInventoryAuthority.problem(null, null, OWNER, "team:builders", null, null));
    }

    private static GameProfile profile() { return new GameProfile(OWNER, "BuilderOwner"); }

    private static RecruitsFaction faction() {
        var faction = new RecruitsFaction(); faction.setStringID("builders");
        faction.addMember(OWNER, "BuilderOwner"); return faction;
    }

    private static Scoreboard board() {
        var board = new Scoreboard(); board.addPlayerToTeam("BuilderOwner", board.addPlayerTeam("builders"));
        return board;
    }
}
