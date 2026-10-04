package com.devfarinsky.siegeoverhaul.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Geometry-free terminal authority. It never grants permission to pay, assign, reconstruct or mutate blocks. */
public final class PerimeterTerminalReceipt {
    /**
     * Supplied by runtime only after independent durable ledger/entity cleanup verification.
     * The ledger generation and ordered stage IDs must match the independently checked ledger.
     * The detachment receipt must bind this project, generation, ledger generation and exact builder.
     * Hash strings alone do not verify world state: they must come from those cleanup authorities, never manifest fields alone.
     * Unknown, unloaded, interrupted or ambiguous cleanup must retain the full manifest instead.
     */
    public record CleanupProof(UUID projectId, long generation, String manifestHash, long terminalRevision,
                               PerimeterProject.State terminalState, UUID owner, UUID builder, UUID ledgerGeneration, List<UUID> cleanedStageIds,
                               String reservationRetirementReceipt, String builderDetachmentReceipt,
                               String nativeStageCleanupReceipt) {
        public CleanupProof {
            identity(projectId); identity(owner); identity(builder); identity(ledgerGeneration);
            cleanedStageIds = List.copyOf(cleanedStageIds);
            Set<UUID> unique = new HashSet<>();
            if (cleanedStageIds.isEmpty() || cleanedStageIds.size() > PerimeterStageLayout.MAX_STAGES) throw invalid("Missing exact stage cleanup coverage");
            for (UUID stage : cleanedStageIds) {
                identity(stage);
                if (stage.equals(projectId) || !unique.add(stage)) throw invalid("Reused native stage cleanup identity");
            }
            if (generation < 1 || terminalRevision < 1 || !terminal(terminalState)
                    || !PerimeterProject.digest(manifestHash) || !PerimeterProject.digest(reservationRetirementReceipt)
                    || !PerimeterProject.digest(builderDetachmentReceipt) || !PerimeterProject.digest(nativeStageCleanupReceipt))
                throw invalid("Invalid independently verified terminal cleanup evidence");
        }
    }
    public record Stage(int index, UUID areaId, String digest, int targetCount) {
        public Stage {
            identity(areaId);
            if (index < 0 || index >= PerimeterStageLayout.MAX_STAGES || !PerimeterProject.digest(digest)
                    || targetCount < 1 || targetCount > PerimeterStageLayout.MAX_TARGETS) throw invalid("Invalid retired native stage identity");
        }
    }

    private final CleanupProof cleanup;
    private final String coreKey, faction;
    private final int feeVersion, quotedPrice, activeStage, verifiedStages, claimChunkCount;
    private final List<Stage> stages;
    private final PerimeterProject.PaymentReceipt payment;
    private final String receiptHash;

    private PerimeterTerminalReceipt(CleanupProof cleanup, String coreKey, String faction, int feeVersion, int quotedPrice,
                                     int activeStage, int verifiedStages, int claimChunkCount, List<Stage> stages, PerimeterProject.PaymentReceipt payment) {
        this.cleanup = Objects.requireNonNull(cleanup); this.coreKey = coreKey; this.faction = faction;
        this.feeVersion = feeVersion; this.quotedPrice = quotedPrice; this.activeStage = activeStage;
        this.verifiedStages = verifiedStages; this.claimChunkCount = claimChunkCount; this.stages = List.copyOf(stages); this.payment = payment;
        if (!PerimeterProject.bounded(coreKey, 256) || !PerimeterProject.bounded(faction, 256) || !coreKey.equals("team:" + faction)
                || claimChunkCount < 1 || claimChunkCount > PerimeterTerritory.MAX_CHUNKS || feeVersion != 1 || quotedPrice != 64 || stages.isEmpty() || stages.size() > PerimeterStageLayout.MAX_STAGES
                || activeStage < 0 || activeStage > stages.size() || verifiedStages < activeStage
                || verifiedStages > stages.size() || verifiedStages > activeStage + 1) throw invalid("Invalid compact terminal record");
        Set<UUID> areas = new HashSet<>(); int count = 0;
        for (int i = 0; i < stages.size(); i++) {
            Stage stage = stages.get(i); count += stage.targetCount();
            if (stage.index() != i || stage.areaId().equals(cleanup.projectId()) || !areas.add(stage.areaId()) || count > PerimeterStageLayout.MAX_TARGETS)
                throw invalid("Changed retired stage ordering or totals");
        }
        if (!cleanup.cleanedStageIds().equals(stages.stream().map(Stage::areaId).toList())) throw invalid("Incomplete or reordered stage cleanup coverage");
        if (cleanup.terminalState() == PerimeterProject.State.COMPLETE && (activeStage != stages.size() || verifiedStages != stages.size() || payment == null))
            throw invalid("Completion receipt is missing complete native-stage evidence");
        if (payment == null && (activeStage != 0 || verifiedStages != 0)) throw invalid("Unpaid cancellation cannot have completed native work");
        if (payment != null && !new PerimeterProject.PaymentReceipt(cleanup.projectId(), cleanup.generation(), cleanup.manifestHash(),
                feeVersion, quotedPrice, payment.creative() ? 0 : quotedPrice, payment.creative()).equals(payment))
            throw invalid("Changed terminal payment receipt");
        this.receiptHash = hash();
    }

    static PerimeterTerminalReceipt compact(PerimeterProject project, CleanupProof proof) {
        var h = project.header();
        if (!terminal(project.state()) || proof == null || !proof.projectId().equals(h.projectId()) || proof.generation() != h.generation()
                || !proof.manifestHash().equals(project.manifestHash()) || proof.terminalRevision() != project.revision()
                || proof.terminalState() != project.state() || !proof.owner().equals(h.owner()) || !proof.builder().equals(h.builder()))
            throw invalid("Cleanup proof does not match this exact terminal project");
        List<Stage> stages = project.stages().stream().map(s -> new Stage(s.index(), s.areaId(), s.digest(), s.layout().targets().size())).toList();
        return new PerimeterTerminalReceipt(proof, h.coreKey(), h.faction(), h.feeVersion(), h.quotedPrice(),
                project.activeStage(), project.receipts().size(), h.territory().size(), stages, project.payment());
    }
    public CleanupProof cleanup() { return cleanup; }
    public UUID projectId() { return cleanup.projectId(); }
    public long generation() { return cleanup.generation(); }
    public UUID owner() { return cleanup.owner(); }
    public UUID builder() { return cleanup.builder(); }
    public String manifestHash() { return cleanup.manifestHash(); }
    public long revision() { return cleanup.terminalRevision(); }
    public PerimeterProject.State state() { return cleanup.terminalState(); }
    public String coreKey() { return coreKey; }
    public String faction() { return faction; }
    public int feeVersion() { return feeVersion; }
    public int quotedPrice() { return quotedPrice; }
    public int activeStage() { return activeStage; }
    public int verifiedStages() { return verifiedStages; }
    public int claimChunkCount() { return claimChunkCount; }
    public int totalStageCount() { return stages.size(); }
    public int totalTargetCount() { return stages.stream().mapToInt(Stage::targetCount).sum(); }
    public List<Stage> stages() { return stages; }
    public PerimeterProject.PaymentReceipt payment() { return payment; }
    public String receiptHash() { return receiptHash; }
    public int completedTargetCount() { return stages.subList(0, verifiedStages).stream().mapToInt(Stage::targetCount).sum(); }
    public PerimeterProject.Check check() { return new PerimeterProject.Check(projectId(), generation(), manifestHash(), revision(), state(), activeStage); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Version", 1);
        tag.putUUID("Project", projectId()); tag.putLong("Generation", generation()); tag.putString("Hash", manifestHash());
        tag.putUUID("Owner", owner()); tag.putUUID("Builder", builder()); tag.putString("CoreKey", coreKey); tag.putString("Faction", faction);
        tag.putLong("Revision", revision()); tag.putString("State", state().name()); tag.putInt("Active", activeStage); tag.putInt("Verified", verifiedStages); tag.putInt("ClaimChunks", claimChunkCount);
        tag.putUUID("LedgerGeneration", cleanup.ledgerGeneration());
        tag.putInt("FeeVersion", feeVersion); tag.putInt("Price", quotedPrice); tag.putString("ReceiptHash", receiptHash);
        tag.putString("ReservationRetired", cleanup.reservationRetirementReceipt());
        tag.putString("BuilderDetached", cleanup.builderDetachmentReceipt()); tag.putString("StagesCleaned", cleanup.nativeStageCleanupReceipt());
        ListTag saved = new ListTag();
        for (Stage stage : stages) {
            CompoundTag entry = new CompoundTag(); entry.putInt("Index", stage.index()); entry.putUUID("Area", stage.areaId());
            entry.putString("Digest", stage.digest()); entry.putInt("Targets", stage.targetCount()); saved.add(entry);
        }
        tag.put("Stages", saved);
        if (payment != null) {
            CompoundTag paid = new CompoundTag(); paid.putUUID("Project", payment.projectId()); paid.putLong("Generation", payment.generation());
            paid.putString("Hash", payment.manifestHash()); paid.putInt("FeeVersion", payment.feeVersion()); paid.putInt("Price", payment.quotedPrice());
            paid.putInt("Debited", payment.debited()); paid.putBoolean("Creative", payment.creative()); tag.put("Payment", paid);
        }
        return tag;
    }
    public static PerimeterTerminalReceipt load(CompoundTag tag) {
        keys(tag, Set.of("Version", "Project", "Generation", "Hash", "Owner", "Builder", "CoreKey", "Faction", "Revision", "State",
                "Active", "Verified", "ClaimChunks", "LedgerGeneration", "FeeVersion", "Price", "ReceiptHash", "ReservationRetired", "BuilderDetached", "StagesCleaned", "Stages"), Set.of("Payment"));
        if (integer(tag, "Version") != 1) throw invalid("Unknown terminal receipt version");
        PerimeterProject.State state;
        try { state = PerimeterProject.State.valueOf(string(tag, "State", 40)); }
        catch (RuntimeException wrong) { throw invalid("Unknown terminal state"); }
        require(tag, "Stages", Tag.TAG_LIST); ListTag saved = (ListTag) tag.get("Stages");
        if (saved.isEmpty() || saved.size() > PerimeterStageLayout.MAX_STAGES || saved.getElementType() != Tag.TAG_COMPOUND)
            throw invalid("Invalid compact stage list");
        List<Stage> stages = new ArrayList<>();
        for (Tag raw : saved) {
            CompoundTag entry = (CompoundTag) raw; keys(entry, Set.of("Index", "Area", "Digest", "Targets"), Set.of());
            stages.add(new Stage(integer(entry, "Index"), uuid(entry, "Area"), string(entry, "Digest", 64), integer(entry, "Targets")));
        }
        CleanupProof proof = new CleanupProof(uuid(tag, "Project"), number(tag, "Generation"), string(tag, "Hash", 64), number(tag, "Revision"),
                state, uuid(tag, "Owner"), uuid(tag, "Builder"), uuid(tag, "LedgerGeneration"), stages.stream().map(Stage::areaId).toList(), string(tag, "ReservationRetired", 64), string(tag, "BuilderDetached", 64), string(tag, "StagesCleaned", 64));
        PerimeterProject.PaymentReceipt payment = null;
        if (tag.contains("Payment")) {
            require(tag, "Payment", Tag.TAG_COMPOUND); CompoundTag p = tag.getCompound("Payment");
            keys(p, Set.of("Project", "Generation", "Hash", "FeeVersion", "Price", "Debited", "Creative"), Set.of()); require(p, "Creative", Tag.TAG_BYTE);
            if (p.getByte("Creative") != 0 && p.getByte("Creative") != 1) throw invalid("Invalid terminal creative payment mode");
            payment = new PerimeterProject.PaymentReceipt(uuid(p, "Project"), number(p, "Generation"), string(p, "Hash", 64),
                    integer(p, "FeeVersion"), integer(p, "Price"), integer(p, "Debited"), p.getBoolean("Creative"));
        }
        var receipt = new PerimeterTerminalReceipt(proof, string(tag, "CoreKey", 256), string(tag, "Faction", 256), integer(tag, "FeeVersion"),
                integer(tag, "Price"), integer(tag, "Active"), integer(tag, "Verified"), integer(tag, "ClaimChunks"), stages, payment);
        if (!receipt.receiptHash.equals(string(tag, "ReceiptHash", 64))) throw invalid("Changed terminal receipt hash");
        return receipt;
    }
    private String hash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(("perimeter-terminal-v1\n" + projectId() + "\n" + generation() + "\n" + manifestHash() + "\n"
                    + revision() + "\n" + state().name() + "\n" + owner() + "\n" + builder() + "\n" + cleanup.ledgerGeneration() + "\n"
                    + cleanup.reservationRetirementReceipt() + "\n" + cleanup.builderDetachmentReceipt() + "\n" + cleanup.nativeStageCleanupReceipt() + "\n"
                    + coreKey + "\n" + faction + "\n" + feeVersion + ":" + quotedPrice + ":" + activeStage + ":" + verifiedStages
                    + ":" + claimChunkCount + "\n").getBytes(StandardCharsets.UTF_8));
            if (payment == null) digest.update("unpaid\n".getBytes(StandardCharsets.UTF_8));
            else digest.update(("paid:" + payment.projectId() + ":" + payment.generation() + ":" + payment.manifestHash() + ":"
                    + payment.feeVersion() + ":" + payment.quotedPrice() + ":" + payment.debited() + ":" + payment.creative() + "\n").getBytes(StandardCharsets.UTF_8));
            for (Stage stage : stages) digest.update((stage.index() + ":" + stage.areaId() + ":" + stage.digest() + ":" + stage.targetCount() + "\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static boolean terminal(PerimeterProject.State state) { return state == PerimeterProject.State.CANCELED || state == PerimeterProject.State.COMPLETE; }
    private static void identity(UUID id) { if (id == null || id.equals(new UUID(0, 0))) throw invalid("Missing terminal identity"); }
    private static UUID uuid(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT_ARRAY); if (!tag.hasUUID(key)) throw invalid("Invalid terminal UUID"); return tag.getUUID(key); }
    private static int integer(CompoundTag tag, String key) { require(tag, key, Tag.TAG_INT); return tag.getInt(key); }
    private static long number(CompoundTag tag, String key) { require(tag, key, Tag.TAG_LONG); return tag.getLong(key); }
    private static String string(CompoundTag tag, String key, int limit) {
        require(tag, key, Tag.TAG_STRING); String value = tag.getString(key); if (value.length() > limit) throw invalid("Oversize terminal field"); return value;
    }
    private static void require(CompoundTag tag, String key, int type) { if (!tag.contains(key, type)) throw invalid("Missing or wrongly typed terminal field: " + key); }
    private static void keys(CompoundTag tag, Set<String> required, Set<String> optional) {
        if (tag == null || !tag.getAllKeys().containsAll(required)) throw invalid("Incomplete terminal receipt");
        for (String key : tag.getAllKeys()) if (!required.contains(key) && !optional.contains(key)) throw invalid("Unexpected terminal field");
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
