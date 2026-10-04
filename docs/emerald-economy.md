# Emerald economy: 4.52.4 consistency pass

Command Center purchases spend the shared faction Treasury. Personal emeralds must
first be deposited; native Recruits faction/claim purchases remain a separate
companion-mod currency path. This release does not rewrite existing server configs.

## Construction commissions

| New commission | Treasury emeralds |
| --- | ---: |
| Wall section | 8 |
| Wall corner | 8 |
| Wall stairs | 12 |
| Archer barricade | 12 |
| Watchtower building | 32 |
| Gatehouse building | 48 |
| Complete territory perimeter | 64 |

These are service fees. A builder, safe site, separately supplied materials and
normal native work time are still required. Plans and previews remain free; a fee
is taken only after the builder accepts. The gatehouse has an open passage, not a
moving gate. Cancellations retain placed blocks and do not refund commissions or
consumed materials.

The reference opening five-wave chapter awards 25 Treasury emeralds in wave cash
and 8 for its commander, before scouts, under untouched defaults. This anchors
small individual commissions below the whole-perimeter service. It is not a
measured combat-duration or universal play-balance claim. Existing worlds may use
different configured payouts and ordinary native trading remains available.

An unpaid manual preview now stores the exact quoted price. Missing, malformed or
outdated price quotes require a fresh review and confirmation delay. Accepted jobs
retain their saved blocks and payment. The perimeter's existing version-1,
64-emerald payment records and terminal receipts are unchanged.

## Unavailable territory upgrades

Fortified Walls and the Watchtower **territory upgrade** currently have no
implemented advertised effect. New purchases are blocked on the server, including
requests from older/custom clients. The HUD shows unavailable status and does not
count them as active benefits. An existing ownership bit remains saved for a future
implemented effect; this release neither removes ownership nor guesses a refund
from a bit that cannot prove its original price or Creative status.

The Watchtower **building** is a separate available construction plan.
Provisioning and Iron Levy retain their existing prices and behavior. Hiring,
heroes, civilians, loot purchases, blessings, siege crews and interest are unchanged.

## Cleared-wave rewards

Physical wave loot settles before a checkpoint vote or enemy-core victory can
remove the raid. Its receipt is separate from the Treasury wave-payment marker.
Each current wave retains a bounded, frozen recipient list, each selected box tier
and whether delivery was attempted. Repeated ticks and ordinary save/reload cannot
repeat an attempted reward. Late arrivals are not added to that wave's recipient
list. The next wave replaces the prior list rather than accumulating an unbounded
history. Practice raids with rewards disabled never create these boxes.

Inventory insertion consumes only the mutable remainder before dropping overflow.
A failed or uncertain delivery is not replayed, and a refused drop is not reported
as successful delivery. Minecraft world SavedData, player inventory and entity
saves are separate writes: this is conservative callback/save-load idempotence,
not a claim of a crash-atomic transaction across all three files.

A missing or invalid receipt in an existing raid cannot prove whether its current
wave already paid. Migration therefore suppresses that ambiguous wave and permits
later waves normally; it does not infer historical rewards or backfill them.
Bonus barrel emeralds likewise require a reward-eligible raid and a defending,
non-spectating player. Barrel destruction still advances normal bookkeeping even
when no bonus is eligible.

## Compatibility and verification

Install 4.52.4 on the server and every client together. Protocol 20 rejects older
peers so static client prices and unavailable-status copy cannot disagree with the
new server. Packet layouts are unchanged; saved worlds and accepted jobs remain
load-compatible.

The regression suite covers price relationships, exact quote binding, unavailable
purchase refusal, retained ownership, unchanged available-upgrade payment,
reward eligibility, replay/load behavior, frozen recipients, partial delivery and
both-side protocol acceptance. Native HUD acceptance checks disabled states at
compact and roomy scales. Native Building acceptance retains real manual placement,
material conservation, claim/reload checks and exact fee accounting; its separate
economy fixtures must be labeled as controlled cleared-wave cases, not fought raids.
Passing syntax or isolated model tests alone does not establish those native gates.
