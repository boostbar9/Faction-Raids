package com.devfarinsky.siegeoverhaul.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedAssembly.*;
import static com.devfarinsky.siegeoverhaul.core.PerimeterSteppedProject.*;

/** Synthetic model values only: these receipts do not represent world work, payment, or durable storage. */
final class PerimeterSteppedModelFixtures {
    static final Position FILL = new Position(0, 63, 0), WALL = new Position(0, 64, 0), SECOND_FILL = new Position(1, 63, 0);
    static final Position CLEAR = new Position(0, 65, 0), SUPPORT = new Position(0, 62, 0);
    static final StateValue COBBLE = new StateValue("minecraft:cobblestone", Map.of());
    static final String GEOMETRY = "a".repeat(64);
    static UUID id(int n) { return new UUID(0, n); }
    static Header header() {
        return new Header(id(1), 1, id(2), id(3), "test", "team:test", new Position(1, 64, 1),
                "minecraft:overworld", id(4), 1, Set.of(new Chunk(0, 0)), -64, 320, "b".repeat(64), 1, 64);
    }
    static Map<Position, Target> targets() {
        return new HashMap<>(Map.of(FILL, fill(), WALL, new Target(Kind.STRUCTURE, 0, StateValue.AIR, COBBLE, Edit.PLACE, 7)));
    }
    static Target fill() { return new Target(Kind.FILL, 0, StateValue.AIR, StateValue.DIRT, Edit.PLACE, 6); }
    static Map<Position, Observation> observations() {
        return new HashMap<>(Map.of(CLEAR, new Observation(ObservationRole.CLEARANCE, StateValue.AIR, 8),
                SUPPORT, new Observation(ObservationRole.SUPPORT, new StateValue("minecraft:grass_block", Map.of("snowy", "false")), 5)));
    }
    static Map<String, Integer> bill() { return new HashMap<>(Map.of("minecraft:dirt", 1, "minecraft:cobblestone", 1)); }
    static PerimeterSteppedAssembly assembly() { return assemble(header(), 1, GEOMETRY, targets(), observations(), bill()); }
    static PaymentReceipt payment(PerimeterSteppedProject p) { return new PaymentReceipt(p.contract().binding(), id(100), 1, 64, 64, PaymentMode.TREASURY_DEBIT); }
    static ActivationReceipt activation(PerimeterSteppedProject p, int phase) {
        Phase f = p.contract().phases().get(phase);
        return new ActivationReceipt(p.contract().binding(), phase, f.areaId(), f.digest(), id(110 + phase));
    }
    static PhaseReceipt verification(PerimeterSteppedProject p) {
        return new PhaseReceipt(p.snapshot().activeLease(), id(120 + p.activePhase()), p.contract().phases().get(p.activePhase()).targets().size());
    }
    static RetirementReceipt retirement(PerimeterSteppedProject p) { return new RetirementReceipt(p.snapshot().verified().get(p.activePhase()), id(130 + p.activePhase())); }
    static CompletionReceipt completion(PerimeterSteppedProject p) {
        return new CompletionReceipt(p.contract().binding(), id(140), p.snapshot().retired().stream().map(RetirementReceipt::receiptId).toList(), "c".repeat(64));
    }
    static PerimeterSteppedProject paid() { var p = prepare(assembly()); return p.recordPayment(p.check(), payment(p)); }
    static PerimeterSteppedProject running() { var p = paid(); return p.recordActivation(p.check(), activation(p, 0)); }
    static PerimeterSteppedProject verified() { var p = running(); return p.recordVerification(p.check(), verification(p)); }
    static PerimeterSteppedProject waiting() { var p = verified(); return p.recordRetirement(p.check(), retirement(p)); }
    static PerimeterSteppedProject verifyingComplete() {
        var p = waiting(); p = p.recordActivation(p.check(), activation(p, 1)); p = p.recordVerification(p.check(), verification(p));
        return p.recordRetirement(p.check(), retirement(p));
    }
    static PerimeterSteppedProject complete() { var p = verifyingComplete(); return p.recordCompletion(p.check(), completion(p)); }
    static Snapshot snapshot(PerimeterSteppedProject p, State state, State prior, int phase, long revision,
                             PaymentReceipt pay, ActivationReceipt lease, List<PhaseReceipt> verified,
                             List<RetirementReceipt> retired, CompletionReceipt completed, String blocker) {
        return new Snapshot(p.contract().digest(), state, prior, phase, revision, pay, lease, verified, retired, completed, blocker);
    }
}
