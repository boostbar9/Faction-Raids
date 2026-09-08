package com.devfarinsky.siegeoverhaul.compat;

import com.devfarinsky.siegeoverhaul.FactionLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** Transfers only the exact contested native claim; canceled updates leave the original untouched. */
public final class CoreClaimTransfer {
    private CoreClaimTransfer() {}
    public static boolean transfer(ServerLevel level, UUID id, String expectedOwner, String newOwner) {
        return transfer(level,id,expectedOwner,newOwner,null);
    }
    public static boolean transfer(ServerLevel level, UUID id, String expectedOwner, String newOwner, String name) {
        try {
            Object manager = Class.forName("com.talhanation.recruits.ClaimEvents").getField("recruitsClaimManager").get(null);
            if (manager == null) return false;
            Object original = manager.getClass().getMethod("getClaim", UUID.class).invoke(manager, id);
            if (original == null) return false;
            Class<?> type = original.getClass();
            if (type.getField("isAdmin").getBoolean(original)
                    || !expectedOwner.equals(type.getMethod("getOwnerFactionStringID").invoke(original))) return false;
            Object factions = Class.forName("com.talhanation.recruits.FactionEvents").getField("recruitsFactionManager").get(null);
            Object faction = factions.getClass().getMethod("getFactionByStringID", String.class).invoke(factions,newOwner);
            if (faction == null) return false;
            // Work on a copy so ClaimEvent.Updated cancellation really prevents the ownership change.
            Object copy = type.getMethod("fromNBT", CompoundTag.class).invoke(null,
                    ((CompoundTag) type.getMethod("toNBT").invoke(original)).copy());
            Class<?> factionType = Class.forName("com.talhanation.recruits.world.RecruitsFaction");
            Class<?> infoType = Class.forName("com.talhanation.recruits.world.RecruitsPlayerInfo");
            Object info = infoType.getConstructor(UUID.class,String.class,factionType).newInstance(
                    factionType.getMethod("getTeamLeaderUUID").invoke(faction),
                    factionType.getMethod("getTeamLeaderName").invoke(faction),faction);
            type.getMethod("setOwnerFaction",factionType).invoke(copy,faction);
            type.getMethod("setPlayer",infoType).invoke(copy,info);
            if(name!=null && !name.isBlank()) type.getMethod("setName",String.class).invoke(copy,name);
            type.getField("isUnderSiege").setBoolean(copy,false);
            ((java.util.Collection<?>)type.getField("attackingParties").get(copy)).clear();
            ((java.util.Collection<?>)type.getField("defendingParties").get(copy)).clear();
            type.getMethod("resetHealth").invoke(copy);
            manager.getClass().getMethod("addOrUpdateClaim",ServerLevel.class,type).invoke(manager,level,copy);
            Object accepted = manager.getClass().getMethod("getClaim",UUID.class).invoke(manager,id);
            if (accepted != copy || !newOwner.equals(type.getMethod("getOwnerFactionStringID").invoke(accepted))) return false;
            manager.getClass().getMethod("removeActiveSiege",type).invoke(manager,original);
            manager.getClass().getMethod("save",ServerLevel.class).invoke(manager,level);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            FactionLogger.LOG.warn("Core claim transfer deferred for {}",id,ex);
            return false;
        }
    }
}
