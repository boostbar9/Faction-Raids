#!/usr/bin/env python3
"""Exercise the published Village Expansion claim checker with isolated API fixtures.

Usage: python3 tools/reproduce_village_claim_reservation.py path/to/addon.jar
Requires Java 17 (jdk.compiler module). Does not run Minecraft or modify a world.
The addon is supplied separately; no upstream implementation is redistributed.
"""
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

EXPECTED_SHA256 = "9f34d9fed68f1f07bd83040759bece65240b31e37b0d4cea08286b1605137437"
TARGET = "com/example/villagerecruits/claim/PlayerClaimRules.class"
FIXTURES = {
    "net/minecraft/network/chat/Component.java": "package net.minecraft.network.chat; public interface Component {}",
    "net/minecraft/world/level/ItemLike.java": "package net.minecraft.world.level; public interface ItemLike {}",
    "net/minecraft/world/item/Item.java": "package net.minecraft.world.item; public class Item implements net.minecraft.world.level.ItemLike {}",
    "net/minecraft/world/item/ItemStack.java": "package net.minecraft.world.item; public class ItemStack {}",
    "net/minecraft/core/BlockPos.java": """
package net.minecraft.core;
public record BlockPos(int x, int y, int z) {
    public int m_123341_() { return x; }
    public int m_123343_() { return z; }
}
""",
    "net/minecraft/world/level/ChunkPos.java": """
package net.minecraft.world.level;
public class ChunkPos {
    public final int f_45578_, f_45579_;
    public ChunkPos(int x, int z) { f_45578_=x; f_45579_=z; }
    public int m_45604_() { return f_45578_*16; }
    public int m_45605_() { return f_45579_*16; }
    public int m_45608_() { return m_45604_()+15; }
    public int m_45609_() { return m_45605_()+15; }
}
""",
    "net/minecraft/server/level/ServerLevel.java": """
package net.minecraft.server.level;
public class ServerLevel {}
""",
    "net/minecraft/server/level/ServerPlayer.java": """
package net.minecraft.server.level;
public class ServerPlayer {}
""",
    "com/talhanation/recruits/ClaimEvent.java": """
package com.talhanation.recruits;
public class ClaimEvent { public static class Updated {} }
""",
    "com/talhanation/recruits/world/RecruitsClaim.java": """
package com.talhanation.recruits.world;
import java.util.*;
import net.minecraft.world.level.ChunkPos;
public class RecruitsClaim {
    public final List<ChunkPos> chunks = new ArrayList<>();
    public List<ChunkPos> getClaimedChunks() { return chunks; }
}
""",
    "com/example/villagerecruits/claim/VillageClaimIntegration.java": """
package com.example.villagerecruits.claim;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
public class VillageClaimIntegration {
    public static final Map<BlockPos,String> centers = new HashMap<>();
    public static Map<BlockPos,String> getAllCentersWithFactions(ServerLevel level) { return centers; }
}
""",
    "com/example/villagerecruits/data/VillageFactionSaveData.java": """
package com.example.villagerecruits.data;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
public class VillageFactionSaveData {
    public static final VillageFactionSaveData DATA = new VillageFactionSaveData();
    public final Set<String> generatedChunks = new HashSet<>();
    public static VillageFactionSaveData get(ServerLevel level) { return DATA; }
}
""",
    "com/example/villagerecruits/expansion/VillageSiteSpacing.java": """
package com.example.villagerecruits.expansion;
import java.util.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
public class VillageSiteSpacing {
    public static final List<ChunkPos> candidates = new ArrayList<>();
    public static List<ChunkPos> potentialTowerChunksInRadius(ServerLevel level, int x, int z, int radius) {
        return candidates;
    }
}
""",
    "com/example/villagerecruits/world/RemoteTowerActivation.java": """
package com.example.villagerecruits.world;
import java.util.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
public class RemoteTowerActivation {
    // The published queue method returns without queuing already-probed sites.
    // A probe can mean no spawner exists; it does not populate generatedChunks.
    public static final Set<String> probed = new HashSet<>();
    public static int queued;
    public static void queuePriorityTower(ServerLevel level, ChunkPos pos) {
        if (probed.contains(pos.f_45578_+","+pos.f_45579_)) return;
        queued++;
    }
}
""",
    "Reproduce.java": """
import java.lang.reflect.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import com.talhanation.recruits.world.RecruitsClaim;
import com.example.villagerecruits.claim.VillageClaimIntegration;
import com.example.villagerecruits.data.VillageFactionSaveData;
import com.example.villagerecruits.expansion.VillageSiteSpacing;
import com.example.villagerecruits.world.RemoteTowerActivation;
public class Reproduce {
    private static Method checker;
    private static BlockPos check(ServerLevel level, RecruitsClaim claim) throws Exception {
        return (BlockPos) checker.invoke(null, level, claim, 600);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        checker = Class.forName("com.example.villagerecruits.claim.PlayerClaimRules")
            .getDeclaredMethod("nearestVillageOrPendingTower", ServerLevel.class, RecruitsClaim.class, int.class);
        checker.setAccessible(true);
        ServerLevel level = new ServerLevel();
        RecruitsClaim claim = new RecruitsClaim();
        for (int x=-2; x<=2; x++) for (int z=-2; z<=2; z++) claim.chunks.add(new ChunkPos(x,z));
        require(check(level,claim)==null, "Control: empty candidate list should allow the claim");
        VillageSiteSpacing.candidates.add(new ChunkPos(10,0));
        RemoteTowerActivation.probed.add("10,0");
        BlockPos rejected = check(level,claim);
        require(new BlockPos(168,64,8).equals(rejected), "Published defect did not reproduce");
        require(RemoteTowerActivation.queued==0, "Already-probed empty site should not queue again");
        require(VillageFactionSaveData.DATA.generatedChunks.isEmpty(), "No actual tower was generated");
        require(rejected.equals(check(level,claim)), "Repeated attempt should reproduce the persistent rejection");
        System.out.println("REPRODUCED: published checker rejects a confirmed-empty candidate at 168,8 on repeated attempts.");
        VillageSiteSpacing.candidates.clear();
        VillageSiteSpacing.candidates.add(new ChunkPos(100,0));
        require(check(level,claim)==null, "Control: distant candidate should not reject");
        VillageSiteSpacing.candidates.clear();
        BlockPos village = new BlockPos(168,64,8);
        VillageClaimIntegration.centers.put(village,"actual-village");
        require(village.equals(check(level,claim)), "Control: real nearby village must remain protected");
        System.out.println("Controls passed: no candidate/distant candidate allow; real nearby village blocks.");
        System.out.println("This is an isolated bytecode reproduction, not a live Forge playtest or a shipped fix.");
    }
}
""",
}


def main():
    jar = Path(sys.argv[1])
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit("Refusing an unverified artifact: " + digest)
    with tempfile.TemporaryDirectory(prefix="village-claim-repro-") as folder:
        root = Path(folder)
        sources = []
        for name, source in FIXTURES.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
            sources.append(str(path))
        subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main", "-d", str(root), *sources], check=True)
        with zipfile.ZipFile(jar) as archive:
            target = root / TARGET
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(TARGET))
        subprocess.run(["java", "-cp", str(root), "Reproduce"], check=True)


if __name__ == "__main__":
    main()
