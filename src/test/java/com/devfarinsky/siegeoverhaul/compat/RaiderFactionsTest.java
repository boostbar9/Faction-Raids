package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.MinecraftTestSupport;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaiderFactionsTest extends MinecraftTestSupport {
    @Test void existingNativeFactionIsRefreshedInPlaceOnlyWhenIdentityDiffers() throws Exception {
        CompoundTag oldBanner=new CompoundTag();
        oldBanner.putString("identity","legacy");
        CompoundTag olympianBanner=new CompoundTag();
        olympianBanner.putString("identity","olympian");
        FakeFaction faction=new FakeFaction("Blackbay Reavers","Blackbay Reavers Warlord",oldBanner,(byte)0,0);

        assertTrue(RaiderFactions.refreshNativeIdentity(
                faction,"Poseidon's Tide","Poseidon's Tide Strategos",olympianBanner,(byte)6,0x00AAAA));
        assertEquals("Poseidon's Tide",faction.getTeamDisplayName());
        assertEquals("Poseidon's Tide Strategos",faction.getTeamLeaderName());
        assertEquals((byte)6,faction.getUnitColor());
        assertEquals(0x00AAAA,faction.getTeamColor());
        assertEquals(olympianBanner,faction.getBanner());
        assertNotSame(olympianBanner,faction.getBanner(),"native faction must own a defensive NBT copy");
        assertEquals(5,faction.mutations);

        assertFalse(RaiderFactions.refreshNativeIdentity(
                faction,"Poseidon's Tide","Poseidon's Tide Strategos",olympianBanner,(byte)6,0x00AAAA));
        assertEquals(5,faction.mutations,"stable identities must not dirty the faction save again");
    }

    public static final class FakeFaction {
        private String name;
        private String leaderName;
        private CompoundTag banner;
        private byte unitColor;
        private int teamColor;
        int mutations;

        FakeFaction(String name,String leaderName,CompoundTag banner,byte unitColor,int teamColor) {
            this.name=name;
            this.leaderName=leaderName;
            this.banner=banner;
            this.unitColor=unitColor;
            this.teamColor=teamColor;
        }
        public String getTeamDisplayName() { return name; }
        public void setTeamDisplayName(String name) { this.name=name; mutations++; }
        public String getTeamLeaderName() { return leaderName; }
        public void setTeamLeaderName(String leaderName) { this.leaderName=leaderName; mutations++; }
        public CompoundTag getBanner() { return banner; }
        public void setBanner(CompoundTag banner) { this.banner=banner; mutations++; }
        public byte getUnitColor() { return unitColor; }
        public void setUnitColor(byte unitColor) { this.unitColor=unitColor; mutations++; }
        public int getTeamColor() { return teamColor; }
        public void setTeamColor(int teamColor) { this.teamColor=teamColor; mutations++; }
    }
}
