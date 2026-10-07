# Retained canceled-grading cleanup boundary

This slice adds no cancel/menu packet, retirement/refund or new work authority. It lets the original live protected lifecycle finish after an exact canceled local job has revoked work and inventory leases, before a LivingTick refusal would otherwise strand cleanup.

Admission requires the retained paid job, exact selector and owner, same sealed marker/world reservation/generation, live recovery admission, matching hand/supply evidence, and no native callback or inventory REVIEW ambiguity. All four native inventory families must already be guarded with exact original delegate/session identity. Foreign/unwrapped/absent/orphaned identities refuse. No fresh wrapper, start, work tick or transfer is created by this path.

Pinned stop effects were read from the actual 4.52.9 fixture's companion bytecode (Workers8351157/Recruits8339846):
- BuilderWorkGoal has no stop override; inherited Goal.stop is empty. Only the retained new-work delegate may be stopped, never another original/legacy lifecycle.
- GetNeededItemsFromStorage and DepositItemsToStorage inherit AbstractChestGoal.stop: close the captured chest if its position/container exist.
- RecruitUpkeepPosGoal.stop sets the normal upkeep cooldown, clears forced upkeep and local timing flags, conditionally performs its already-earned payment-timer reset, closes the captured source and marks it changed.
- RecruitUpkeepEntityGoal.stop finalizes cooldown/forced-upkeep and its already-earned conditional payment-timer reset; it does not read a source inventory.
- RecruitStorageUpkeepGoal.stop delegates to entity upkeep outside storage mode. In storage mode it closes its captured chest, clears captured position/container/queue, stops native navigation, sets cooldown/forced flags, conditionally resets the earned payment timer and clears its storage selection.

The existing ProtectedInventoryGoal callback fence and cleanup guards own those effects. Unloaded required sources defer the original stop. A partial/throwing callback stays REVIEW and is never replayed. Repeated cancellation only observes completion; it never reissues native stops, erases source/material requests, changes supply/work receipts or restores blocks.

The terminal lease continues denying new work/transfers. This is not final marker retirement, capacity reuse, terminal physiology/hand-only handoff or saved-game recovery. Navigation remains the original implementation; no forwarding navigator or field swap is included.
