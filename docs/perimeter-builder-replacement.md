# Replacement builders for paid perimeter projects

An unfinished paid perimeter keeps its blueprint, original builder identity, Treasury receipt, reservation, baselines and verified sections. A separate bounded SavedData assignment history names the builder currently authorized to work.

After a confirmed destructive removal, the controller chooses the closest eligible idle builder within 64 blocks of the original Siege Core. The owner must be online in the same dimension; faction, original core and territory must still match. Each hire must belong to that owner and have no other native assignment, perimeter reservation, protected receipt, inventory review or active item use. Unloaded, transferred or merely missing builders never authorize a replacement.

The replacement inherits the unfinished section's protected snapshot, including completed and cleared cells. The immutable paid manifest is not rewritten. No additional commission is taken and no items are copied from the dead builder; recover and supply death drops through normal gameplay. Replacement works during a section and at an authenticated retired-section boundary. Terminal cleanup resolves the current assignment while preserving the original receipt identity.

Unknown or conflicting saves pause for review. Assignment history is bounded to 64 projects and 64 replacements per project; exhaustion fails closed. This change maintains one active native section and one assigned builder per project. Concurrent crews need separate task leases and are not enabled here.

Before admitting a later paid section, the controller checks whether the assigned worker overlaps its targets. If so, it requests ordinary native walking to loaded, dry, collision-free standing space near the original marker and outside the entire reserved footprint, with a body-width margin. It creates no marker, lease or admission journal until the builder is clear. Other assignments, combat, leash/passenger state or unavailable ground pause this approach; collision and placement guards are unchanged. This avoids sealing an incomplete admission while the builder itself occupies the next section.

## Validation

`PerimeterBuilderAssignmentsTest` covers immutable payment/blueprint preservation, saved assignment round trips, repeated deaths, stale-worker rejection, corrupt chains and conflicting identity. `ConstructionProjectLedgerTest` exercises authenticated death, unchanged reservations, edited-site refusal, reload and a second death. These are model/server-boundary tests, not in-game proof.

Ported to main from the 4.53.0-alpha.3 line (feat/perimeter-builder-replacement) without its gate, earthworks or stepped-wall changes. The alpha native replacement QA workflow is not part of main; the unit and ledger regressions above run in the normal Build.

Known limit: if a replacement is bound but its section handoff cannot be verified and that replacement then dies before the handoff completes, no new death receipt can be minted and the project waits for that builder. Cancel the project from the Siege Core to recover.
