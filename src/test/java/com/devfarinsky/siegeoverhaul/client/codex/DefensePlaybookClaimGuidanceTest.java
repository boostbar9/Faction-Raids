package com.devfarinsky.siegeoverhaul.client.codex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Content contracts only: these do not execute Recruits or prove native UI rendering. */
class DefensePlaybookClaimGuidanceTest {
    private String firstClaimTip() {
        return DefensePlaybook.TIPS.stream()
                .filter(tip -> tip.title().equals("Claim land, place a Siege Core"))
                .findFirst().orElseThrow().body();
    }

    @Test void firstClaimIsAnAreaAndChunkClaimOnlyExtendsIt() {
        String tip = firstClaimTip();
        assertTrue(tip.contains("faction leader"));
        assertTrue(tip.contains("Overworld"));
        assertTrue(tip.contains("Claim Area for the first 5x5 claim"));
        assertTrue(tip.contains("Claim Chunk only extends an existing claim"));
        assertFalse(tip.contains("claim at least one chunk"));
    }

    @Test void paymentAndWholeAreaBufferAreNotConfusedWithTreasuryOrCenterOnlyChecks() {
        String tip = firstClaimTip();
        assertTrue(tip.contains("own inventory, not the Siege Core Treasury"));
        assertTrue(tip.contains("entire 5x5 area"));
        assertTrue(tip.contains("three-chunk buffer"));
        assertFalse(tip.toLowerCase().contains("village"));
        assertFalse(tip.toLowerCase().contains("tower"));
    }
}
