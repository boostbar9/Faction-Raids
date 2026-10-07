package com.devfarinsky.siegeoverhaul.nativecompat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** Immutable observed swap/deposit receipts, separate from positive delivery receipts. */
final class EarthworksSupplyOperations {
    static final int MAX_OPERATIONS = 512, MAX_RECORD_BYTES = 16384, MAX_HISTORY_BYTES = 1_048_576;
    private static final Set<String> KEYS = Set.of("Id", "Scope", "Request", "Kind", "Tick", "Sources", "InventoryBefore", "InventoryAfter", "StorageBefore", "StorageAfter", "Returned");
    private EarthworksSupplyOperations() {}
    /** Prebuilt complete worst-case record and appended list, with actual operation identity and scope. */
    static final class Reservation {
        private final ListTag previous;
        private final CompoundTag maximum;
        private final EarthworksSupplyDemand.Scope scope;
        private final Map<String, CompoundTag> returnedValues;
        private final int maximumRecordLength, maximumHistoryLength;
        private Reservation(ListTag previous, CompoundTag maximum, EarthworksSupplyDemand.Scope scope) {
            this.previous=previous.copy();this.maximum=maximum.copy();this.scope=scope;
            Map<String,CompoundTag> values=new TreeMap<>();
            for(Tag value:maximum.getList("Returned",Tag.TAG_COMPOUND)) {
                var row=(CompoundTag)value;values.put(EarthworksInventoryEvidence.key(row.getCompound("Item")),row.copy());
            }
            returnedValues=Collections.unmodifiableMap(values);
            maximumRecordLength=WorkersEarthworksPort.canonical(maximum).length();
            maximumHistoryLength=WorkersEarthworksPort.canonical(append(previous,maximum,scope)).length();
        }
        ListTag observe(String afterInventory,String afterStorage,Map<String,Integer> returned) {
            // The callback may return any subset/count of the captured cargo, but no unknown full value.
            var receipt=maximum.copy();receipt.putString("InventoryAfter",afterInventory);receipt.putString("StorageAfter",afterStorage);
            var changes=new ListTag();
            new TreeMap<>(returned).forEach((key,count)->{
                CompoundTag bound=returnedValues.get(key);
                if(bound==null||count==null||count<1||count>bound.getInt("Count"))throw bad("Native return exceeds captured cargo identity/count");
                var row=bound.copy();row.putInt("Count",count);changes.add(row);
            });
            receipt.put("Returned",changes);validate(receipt,scope);
            var next=append(previous,receipt,scope);
            if(WorkersEarthworksPort.canonical(receipt).length()>maximumRecordLength
                    ||WorkersEarthworksPort.canonical(next).length()>maximumHistoryLength)
                throw bad("Native result exceeds its exact prebuilt evidence envelope");
            return next;
        }
        int recordLength(){return maximumRecordLength;}
        int historyLength(){return maximumHistoryLength;}
    }
    static Reservation reserve(ListTag previous,EarthworksSupplyDemand.Scope scope,UUID request,String kind,long tick,
                               Set<BlockPos> sources,EarthworksInventoryEvidence.Frame inventory,String storageBefore) {
        if(!Set.of("DEPOSIT","SWAP","OBSERVED_HAND").contains(kind))throw bad("Unknown native operation");
        boolean deposit=kind.equals("DEPOSIT");
        var receipt=new CompoundTag();receipt.putUUID("Id",UUID.randomUUID());receipt.put("Scope",scope.save());receipt.putUUID("Request",request);
        receipt.putString("Kind",kind);receipt.putLong("Tick",tick);receipt.putLongArray("Sources",sources.stream().mapToLong(BlockPos::asLong).sorted().toArray());
        receipt.putString("InventoryBefore",inventory.hash());receipt.putString("InventoryAfter","0".repeat(64));
        receipt.putString("StorageBefore",storageBefore);receipt.putString("StorageAfter",deposit?"0".repeat(64):"");
        var changes=new ListTag();Map<String,CompoundTag> values=new TreeMap<>();Map<String,Integer> counts=new TreeMap<>();
        if(deposit)for(int i=6;i<inventory.slots().size();i++) {
            var slot=inventory.slots().get(i);if(slot.count()<1)continue;
            var item=slot.data().copy();item.putByte("Count",(byte)1);String key=slot.key();
            values.putIfAbsent(key,item);counts.merge(key,slot.count(),Math::addExact);
        }
        counts.forEach((key,count)->{var row=new CompoundTag();row.put("Item",values.get(key));row.putInt("Count",count);changes.add(row);});
        receipt.put("Returned",changes);
        // Same validators/encoding as the actual receipt, including UUID numeric widths, all digest fields,
        // list/compound framing and history-count growth. No guessed fixed overhead or callback probing.
        validate(receipt,scope);append(previous,receipt,scope);return new Reservation(previous,receipt,scope);
    }
    static ListTag append(ListTag previous, CompoundTag receipt, EarthworksSupplyDemand.Scope scope) {
        if (previous.size() >= MAX_OPERATIONS) throw bad("Observed inventory history is full");
        ListTag next = previous.copy(); next.add(receipt); validate(next, scope); return next;
    }
    static void validate(ListTag operations, EarthworksSupplyDemand.Scope scope) {
        if (operations.size() > MAX_OPERATIONS || WorkersEarthworksPort.canonical(operations).length() > MAX_HISTORY_BYTES)
            throw bad("Unbounded native inventory history");
        var seen = new HashSet<UUID>();
        for (Tag value : operations) {
            if (!(value instanceof CompoundTag receipt) || !seen.add(receipt.getUUID("Id"))) throw bad("Duplicate or malformed inventory receipt");
            validate(receipt, scope);
        }
    }
    private static void validate(CompoundTag receipt, EarthworksSupplyDemand.Scope current) {
        if (!receipt.getAllKeys().equals(KEYS) || !receipt.hasUUID("Id") || !receipt.hasUUID("Request")
                || !receipt.contains("Scope", Tag.TAG_COMPOUND) || !receipt.contains("Kind", Tag.TAG_STRING)
                || !receipt.contains("Tick", Tag.TAG_LONG) || !receipt.contains("Sources", Tag.TAG_LONG_ARRAY)
                || !receipt.contains("Returned", Tag.TAG_LIST) || WorkersEarthworksPort.canonical(receipt).length() > MAX_RECORD_BYTES)
            throw bad("Malformed observed inventory receipt");
        var scope = EarthworksSupplyDemand.Scope.read(receipt.getCompound("Scope"));
        String kind = receipt.getString("Kind"); boolean deposit = kind.equals("DEPOSIT");
        if (!Set.of("DEPOSIT", "SWAP", "OBSERVED_HAND").contains(kind) || !scope.sameJob(current) || scope.step() > current.step()
                || receipt.getLongArray("Sources").length > 2 || deposit && receipt.getLongArray("Sources").length < 1
                || !deposit && receipt.getLongArray("Sources").length != 0) throw bad("Foreign inventory operation");
        for (String key : List.of("InventoryBefore", "InventoryAfter", "StorageBefore", "StorageAfter")) {
            if (!receipt.contains(key, Tag.TAG_STRING)) throw bad("Missing inventory operation digest");
            String digest = receipt.getString(key);
            if ((!key.startsWith("Storage") || deposit) ? !digest.matches("[0-9a-f]{64}") : !digest.isEmpty()) throw bad("Invalid inventory operation digest");
        }
        var rows = receipt.getList("Returned", Tag.TAG_COMPOUND);
        if (((ListTag)receipt.get("Returned")).size() != rows.size() || rows.size() > 128 || !deposit && !rows.isEmpty())
            throw bad("Malformed returned item evidence");
        var keys = new HashSet<String>();
        for (Tag value : rows) {
            var row = (CompoundTag)value;
            if (!row.getAllKeys().equals(Set.of("Item", "Count")) || !row.contains("Item", Tag.TAG_COMPOUND)
                    || !row.contains("Count", Tag.TAG_INT) || row.getInt("Count") < 1 || row.getInt("Count") > 8192
                    || row.getCompound("Item").getByte("Count") != 1 || !keys.add(EarthworksInventoryEvidence.key(row.getCompound("Item"))))
                throw bad("Malformed full-value return accounting");
        }
    }
    private static IllegalStateException bad(String message) { return new IllegalStateException(message); }
}
