# Shared builder projects: alpha continuation

This work continues experimental 4.53.0-alpha.3 (`2d817d7397a6bdb792dd67913b08d47fce28d1dc`). It does not replace that source with stable 4.52.11 or promote experimental features to stable.

## Current implementation boundary

The first reviewable increment carries the stable wither-rose recovery exclusion into the alpha source and introduces testable crew-membership and transient work-claim primitives. **The current draft integrates helper admission and native scheduling, but is not release-ready or natively verified.** The primitives themselves do not authorize native placement, consume materials, charge a commission, or change the paid blueprint. Passing their tests is not native crew gameplay evidence. Runtime integration is under review, including stage-scoped cleanup provenance and explicit coordinator transfer; do not ship this intermediate checkpoint.

`PerimeterBuilderCrew` retains at most three active helper identities alongside the existing coordinator. Membership is bound to ledger generation, project ID/generation/hash and original coordinator, with admission-stage checks. Retired selectors remain cleanup-only history; they cannot silently regain authority. The immutable manifest and `PerimeterBuilderAssignments` replacement chain remain unchanged.

Transient work claims are separate from durable completion. Claims fence a single worker/target, expire or release after interruption, and never survive marker reload. The actual durable construction receipts, loaded world and native inventory remain authoritative.

## Integration gates still required

- Keep one current native marker and sequential project stage. Preserve its sealed coordinator, recipe, original terrain, payment and reservation.
- Enlist only loaded, idle, exactly owned native workers with no competing job. Authenticate each worker's own hand lifecycle and protected storage before creating work authority.
- Add worker-only job links. Existing `PlayerFortificationJobs.link` also writes the marker's coordinator field and must not be called for helpers.
- Add participant predicates only at worker work/storage boundaries. Marker seals, coordinator replacements and historical stage identities must continue to use coordinator predicates.
- Validate raw native queues before pruning. Filter only private placement queues, never the shared/aliased mining stack. Initially keep native vegetation clearing with the coordinator.
- Reserve the actual next target before native dispatch, then revalidate its world protection, player-edit history, body collision and arrival. Pruning cannot hide a removed previously completed block.
- Helpers wait at native DONE. A shared completion barrier must verify world/shared queues, each worker's remaining native work, and safe inventory cleanup before retiring the marker or compacting the project.
- Retain member-specific cleanup/destruction proof through reload and terminal compaction. Helper death must not mark the entire project destroyed. Unload is never proof of death or inventory cleanup.
- Implement and verify continuation when a worker is dismissed/unloaded, including stale worker return, before claiming that requested behavior. Do not silently substitute a death-only feature.

## Required evidence

Full Java 17 regression Build on the exact final source; tests for persistence, ownership, stale selectors, duplicate claims/charges, sleep/work/supply, interruptions and cleanup. Actual native fixtures must demonstrate distinct concurrent placements, finite inventory conservation, one original fee, death/replacement, reload ordering, cancellation and shared completion. Audit exact loaded Workers bytecode; the published runtime's prefetch differs from pinned public source.

Native source reference: Workers `29d26e1df6475fc8d043dc5d455f67b2fd1e9982`. Source review is not gameplay proof. No release may be described as enabling shared crews until integration and native acceptance pass. Publish only an independently reviewed exact tested artifact, preserving experimental release classification.
