# Persistent building plans

Unpaid manual and perimeter selections are saved in the existing item NBT. They
no longer become unusable after 2,400 ticks. Inventory serialization, logout and
world reload keep the selection. Leaving a manual preview's display range or
visiting another dimension hides it without deleting its tag; returning restores
the same anchor and facing. Explicit cancellation and successful commissioning
still clear the selection, and Survival commissioning consumes the plan.

Persistence applies to player intent, not server approval or a review cache. No
server cache lifetime is extended. Every confirmation still performs the current
production preparation and native acceptance path. Owner, dimension, range,
claim/build permission, Treasury, builder, supplies and terrain checks remain in
force. Existing commissioned jobs and their saved payment records are untouched.

Manual reviews retain the geometry version and quoted-price checks. Changed or
missing values require a fresh visible review and its separate confirmation
delay. Perimeter reviews retain their fingerprint over current price/version,
claim identity, builder, geometry, staging and original world states. A changed
fingerprint refreshes the saved preview without commissioning or payment; a
later deliberate use is required. A blocked refresh retains the free item and
shows the current reason. Corrupt, future-dated or foreign-owner/dimension tags
cannot authorize a commission. The item can be refreshed from Building at its
core if its saved review is unusable.

## Verification

Focused regression classes:

- `DefensePreviewTest`: old NBT round trips, long ages, invalid clocks,
  owner/dimension, geometry and price invalidation, confirmation delay.
- `PerimeterPreviewTest`: long ages, preserved foreign-dimension/owner reads,
  exact bounded preview geometry, corruption and cancellation.
- `DefensePlanItemTest`: held-plan range/dimension/owner interruptions, returning
  to a saved site, fresh commission and one-time consumption.
- `PerimeterConfirmationTest`: fresh preparation for old saved selections,
  blocked ownership, changed quote refresh/delay, failure retention and repeated
  confirmation rejection.

Run the full Java 17 `./gradlew --no-daemon --console=plain clean build`, the
Native Building QA real confirmation/payment/protection flow, and applicable
native HUD captures against the final integrated source. The regression seam
does not itself prove native gameplay; a passed build is not a substitute for
the native receipts. No runtime result is claimed by this document.
