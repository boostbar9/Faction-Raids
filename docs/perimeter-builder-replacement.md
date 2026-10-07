# Replacement builders for paid perimeter projects

An unfinished paid perimeter keeps its blueprint, original builder identity, Treasury receipt, reservation, baselines and verified sections. A separate bounded SavedData assignment history names the builder currently authorized to work.

After a confirmed destructive removal, the controller chooses the closest eligible idle builder within 64 blocks of the original Siege Core. The owner must be online in the same dimension; faction, original core and territory must still match. Each hire must belong to that owner and have no other native assignment, perimeter reservation, protected receipt, inventory review or active item use. Unloaded, transferred or merely missing builders never authorize a replacement.

The replacement inherits the unfinished section's protected snapshot, including completed and cleared cells. The immutable paid manifest is not rewritten. No additional commission is taken and no items are copied from the dead builder; recover and supply death drops through normal gameplay. Replacement works during a section and at an authenticated retired-section boundary. Terminal cleanup resolves the current assignment while preserving the original receipt identity.

Unknown or conflicting saves pause for review. Assignment history is bounded to 64 projects and 64 replacements per project; exhaustion fails closed. This change maintains one active native section and one assigned builder per project. Concurrent crews need separate task leases and are not enabled here.

Before admitting a later paid section, the controller checks whether the assigned worker overlaps its targets. If so, it requests ordinary native walking to loaded, dry, collision-free standing space near the original marker and outside the entire reserved footprint, with a body-width margin. It creates no marker, lease or admission journal until the builder is clear. Other assignments, combat, leash/passenger state or unavailable ground pause this approach; collision and placement guards are unchanged. This avoids sealing an incomplete admission while the builder itself occupies the next section.

## Validation

`PerimeterBuilderAssignmentsTest` covers immutable payment/blueprint preservation, saved assignment round trips, repeated deaths, stale-worker rejection, corrupt chains and conflicting identity. `ConstructionProjectLedgerTest` exercises authenticated death, unchanged reservations, edited-site refusal, reload and a second death. These are model/server-boundary tests, not in-game proof.

Label a draft PR `native-builder-replacement-qa` to run the representative native handoff fixture with `-PnativeBuilderReplacementQa=true`. The replacement variant explicitly commissions the retained legacy 572-target hollow fixture through the protected public server admission; it does not replace production’s new gated quote. Gated blueprint preservation also has a model regression, but this short native run does not prove death/replacement inside a gated section. The fixture kills the original builder after partial placement when construction cargo is empty, introduces a fresh owned idle worker with tools and food only, and waits for production selection and actual native placement. Its ordinary subsequent world reload, section handoff, cancellation and canceled reload must all pass. The <=192-target replacement test partition (ordinary handoff fixture remains <=96) is explicit representative coverage, not full-territory completion. The finite material budget and native AI speed remain unchanged.

All release checks and the final versioned source/JAR still need verification before publishing. This draft does not change stable main, the stable update feed, or the separate alpha release pins.
