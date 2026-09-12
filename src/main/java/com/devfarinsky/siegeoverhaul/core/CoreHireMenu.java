package com.devfarinsky.siegeoverhaul.core;

import com.devfarinsky.siegeoverhaul.RaidSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Read-only offer display; server owns stock, prices, permissions and purchases. */
public final class CoreHireMenu extends AbstractContainerMenu {
    private static final String CORE_HUD_INTRO_SEEN = "SiegeCoreHudIntroSeen";
    private final ServerPlayer owner;
    private final BlockPos pos;
    private final SimpleContainer display = new SimpleContainer(6);
    private final ContainerData data = new SimpleContainerData(30);
    private long shownAt = Long.MIN_VALUE;
    private long lastActionAt = -1;
    private String sentRoster = "";
    private String factionName = "Faction";
    private java.util.List<String> members = java.util.List.of();
    private int[] ledger = new int[0];
    public String factionName() { return factionName; }
    public java.util.List<String> members() { return members; }
    /** v4.18.0 recent bank transactions (signed deltas, oldest first). */
    public int[] bankLedger() { return ledger; }
    public void details(String faction, java.util.List<String> roster, int[] recentLedger) {
        factionName = faction;
        members = java.util.List.copyOf(roster);
        ledger = recentLedger == null ? new int[0] : recentLedger.clone();
    }
    private int wide(int low) { return (data.get(low) & 0xffff) | (data.get(low+1) & 0xffff) << 16; }
    private void wide(int low,int value) { data.set(low,value & 0xffff); data.set(low+1,(value >>> 16) & 0xffff); }
    public int bank() { return wide(18); }
    public int interestRate() { return data.get(20); }
    public int nextWave() { return wide(21); }
    public int nextReward() { return wide(23); }
    public boolean canWithdraw() { return data.get(25) != 0; }
    public int currentWave() { return wide(26); }
    public int voteSeconds() { return data.get(28); }
    /** v4.18.0 Territory buff ownership bitmask replicated to the client. */
    public int territoryBuffMask() { return data.get(29); }
    public boolean hasTerritoryBuff(int index) { return (data.get(29) & (1 << index)) != 0; }
    public CoreHireMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public CoreHireMenu(int id, Inventory inventory, BlockPos pos) {
        super(CoreMenus.HIRING.get(), id);
        this.owner = inventory.player instanceof ServerPlayer sp ? sp : null;
        this.pos = pos == null ? null : pos.immutable();
        for (int i = 0; i < 6; i++) addSlot(new Slot(display, i, -1000, -1000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for (int i = 0; i < 4; i++) { data.set(i, -1); data.set(i + 4, -1); }
        addDataSlots(data);
        if (owner != null) {
            refresh();
            sentRoster = "";
            EndlessSiege.remind(owner, RaidSavedData.get(owner.server).raids.get(SiegeCore.key(owner)));
            maybeSendCoreHudIntro(owner);
        }
    }
    public int lootSequence() { return data.get(16); }
    public int lootBox() { return data.get(15)-1; }
    public int lootTier() { return data.get(17); }
    public ItemStack lootReward() { return display.getItem(5); }
    public int role(int slot) { return data.get(slot); }
    public int cost(int slot) { return data.get(slot + 4); }
    public boolean sold(int slot) { return (data.get(8) & (1 << slot)) != 0; }
    public int emeralds() { return data.get(14); }
    public int seconds() { return data.get(9); }
    public long rotation() {
        long value = 0;
        for (int i = 0; i < 4; i++) value |= (long) (data.get(10 + i) & 0xffff) << (i * 16);
        return value;
    }
    private void refresh() {
        if (owner == null || !stillValid(owner)) return;
        RaidSavedData saved = RaidSavedData.get(owner.server);
        CompoundTag core = saved.siegeCores.get(SiegeCore.key(owner));
        long now = owner.server.overworld().getGameTime();
        if (CoreOffers.refresh(core, now, owner.serverLevel().random)) saved.setDirty();
        long rotation = core.getLong("RefreshAt");
        int[] offers = core.getIntArray("Offers");
        for (int i = 0; i < 4; i++) {
            int role=i==3?core.getInt("HeroRole"):offers[i];
            data.set(i, role);
            display.setItem(i, new ItemStack(CoreHiring.icon(role)));
            try { data.set(i + 4, Math.min(32767, Math.max(0, CoreHiring.cost(role)))); }
            catch (ReflectiveOperationException | RuntimeException ex) { data.set(i + 4, -1); }
        }
        try { display.setItem(4, new ItemStack(CoreHiring.currency())); }
        catch (ReflectiveOperationException | RuntimeException ex) { display.setItem(4, ItemStack.EMPTY); }
        data.set(8, core.getInt("Sold"));
        data.set(9, (int) Math.min(900, Math.max(0, (rotation - now + 19) / 20)));
        for (int i = 0; i < 4; i++) data.set(10 + i, (int) ((rotation >>> (i * 16)) & 0xffff));
        data.set(14,owner.getInventory().items.stream().filter(stack->stack.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum());
        FactionBank.settle(saved,core);
        wide(18,(int)FactionBank.balance(core)); data.set(20,com.devfarinsky.siegeoverhaul.RaidConfig.BANK_INTEREST_BASIS_POINTS.get());
        var raid = saved.raids.get(SiegeCore.key(owner));
        int next = raid == null ? 1 : (int)Math.min(Integer.MAX_VALUE,raid.wave+1L);
        wide(21,next); wide(23,raid!=null && !raid.rewardEligible ? 0 : EndlessSiege.reward(next)); data.set(25,FactionBank.canWithdraw(owner)?1:0);
        wide(26,raid==null?0:raid.wave); data.set(28,raid==null?0:(raid.campaign.getInt("VoteTicks")+19)/20);
        data.set(29, com.devfarinsky.siegeoverhaul.core.TerritoryBuffs.mask(core));
        String faction=owner.getTeam() instanceof net.minecraft.world.scores.PlayerTeam team?team.getDisplayName().getString():"Faction";
        java.util.List<String> roster = owner.getTeam()==null?java.util.List.of():owner.getTeam().getPlayers().stream()
                .filter(name -> !name.startsWith("#"))
                .filter(name -> { try { java.util.UUID.fromString(name); return false; } catch(IllegalArgumentException ignored) { return true; } })
                .sorted().limit(100).map(name -> (owner.server.getPlayerList().getPlayerByName(name)!=null?"Online  ":"Offline  ")+name).toList();
        String signature=faction+roster;
        int[] ledgerCurrent = FactionBank.ledgerDeltas(core);
        String ledgerSig = java.util.Arrays.toString(ledgerCurrent);
        String signatureFull = signature + ledgerSig;
        if (!signatureFull.equals(sentRoster)) { sentRoster=signatureFull; com.devfarinsky.siegeoverhaul.RaidNetwork.coreDetails(owner,containerId,faction,roster,ledgerCurrent); }
        shownAt = now;
    }
    @Override public boolean stillValid(Player player) {
        return owner == null ? pos == null : player == owner && owner.isAlive() && !owner.isSpectator() && SiegeCore.canUse(owner, pos);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, ClickType click, Player player) { }
    public void purchase(ServerPlayer player, int index, long expectedRotation) {
        if (owner == null || !stillValid(player)) return;
        refresh();
        RaidSavedData saved = RaidSavedData.get(owner.server);
        CompoundTag core = saved.siegeCores.get(SiegeCore.key(owner));
        if (CoreOffers.canPurchase(core, index, expectedRotation)
                && CoreHiring.hire(owner, pos, index==3?core.getInt("HeroRole"):core.getIntArray("Offers")[index])) {
            core.putInt("Sold", core.getInt("Sold") | (1 << index));
            saved.setDirty();
        }
        refresh();
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player,int button) {
        if(owner==null || player!=owner || !stillValid(player))return false;
        long now=owner.server.overworld().getGameTime();
        if(lastActionAt>=0 && now-lastActionAt<5)return false;
        lastActionAt=now;
        boolean changed=false;
        if(button>=20 && button<=22) {
            var receipt=CoreLoot.purchaseWithReceipt(owner,button-20);
            if(receipt==null)return false;
            display.setItem(5,receipt.prize().copy()); data.set(15,button-19);data.set(17,receipt.tier());
            data.set(16,data.get(16)%30000+1); changed=true;
        } else if(button>=30 && button<=32) changed=CoreBuffs.purchase(owner,button-30);
        else if(button>=40 && button<=43) changed=FactionBank.transact(owner,new int[]{8,64,-8,-64}[button-40]);
        else if(button>=50 && button<=51) changed=SiegeYard.hire(owner, pos, button-50);
        else if(button>=60 && button<=63) changed=TerritoryBuffs.purchase(owner, pos, button-60);
        if(!changed)return false;
        owner.inventoryMenu.broadcastChanges();refresh();super.broadcastChanges();return true;
    }
    @Override public void broadcastChanges() {
        if (owner != null && owner.server.overworld().getGameTime() - shownAt >= 20) refresh();
        super.broadcastChanges();
    }

    private static void maybeSendCoreHudIntro(ServerPlayer player) {
        CompoundTag flags = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (flags.getBoolean(CORE_HUD_INTRO_SEEN)) return;
        flags.putBoolean(CORE_HUD_INTRO_SEEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, flags);

        player.sendSystemMessage(Component.literal("Command Center quick start:")
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Army hires defenders, Bank shares emeralds, Territory unlocks faction upgrades.")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Need help later? Run ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/siegeoverhaul help").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" or open the Intel tab.").withStyle(ChatFormatting.GRAY)));
    }
}
