# Changelog

All notable user-facing changes are recorded here.

## [1.0.1] - 2026-07-22

### Added

- Added the Egg Collector, a one-block nest and storage device with a vanilla
  two-row, 18-slot chest interface.
- Egg Collectors scan a 16-block-radius sphere every 10 ticks and collect only
  tagged base eggs after the dropped item has existed for at least 100 ticks.
- Added a hidden recipe unlock and a survival recipe using one hay bale, one
  chest, and two iron ingots.

### Changed

- Jurassic Saga animals in the `CREATURE` and `WATER_CREATURE` categories no
  longer enter distance- or idle-based cleanup. JS More preserves rather than
  sets or clears each animal's existing `isPersistenceRequired` flag.
- Random automatic mate search is blocked. Non-periodic breeding now requires
  separately feeding both same-species, opposite-sex, adult, fertile,
  zero-cooldown parents; Jurassic Saga remains responsible for mate checks,
  genetics, and birth.
- Ostriches accept seeds, alligators accept fish, and reed frogs and basilisks
  accept mosquitoes to arm one eligible adult, fertile, zero-cooldown female
  for her next real periodic lay.
- The armed lay replaces exactly one item egg with a maternal-gene `EggEntity`.
  Success clears `PENDING` so later unarmed cycles return to item eggs.
  Creation or world-insertion failure still drops the original item egg and
  retains `PENDING` for a later cycle.
- Valid pending state and maternal genes survive entity saves across
  unload/reload and capture-box round trips. Reloading also clears stale
  automatic mate search.
- Finalized the Egg Collector with a `16x8x16` lower bin, a visually
  overhanging `19x19` grass nest, a total height of `13.25`, and an item GUI
  scale of `0.59`. It uses a full-block selection outline and detailed
  single-cell collision that follows the lower bin and open-centered,
  three-layer inset rim.
- Dinosaur age remains proportional to growth before adulthood. At the exact
  adult boundary it now switches to one displayed day per 24000 ticks of
  server runtime, so sleep and `/time` daylight jumps do not inflate age while
  unloaded chunks and capture-box storage continue from the persisted birth
  anchor.
- On the first server start with this age fix, legacy dinosaurs that were
  already adult reset once to their species adult age. Later-loaded entities
  and capture boxes use the same saved world epoch, accrue only post-update
  server runtime, and never repeat the reset after a restart.

### Behavior and safety

- The default collection tag covers the vanilla egg and Jurassic Saga's
  alligator, ostrich, frog, basilisk, fish, and spider base eggs. Collection is
  source-agnostic, so matching natural, death, and player-dropped eggs behave
  consistently while unrelated drops are ignored.
- Players and hoppers may store any item in the collector. A full inventory
  leaves the dropped egg untouched, and partial insertion preserves the exact
  remainder in the world.
- Network protocol identity remains `7`; the collector reuses the vanilla
  two-row chest menu and adds no custom payload or screen.
- The collector still accepts only tagged egg `ItemEntity` drops after roughly
  five seconds, exposes 18 storage slots, and reuses its placeable block model
  in inventories.

### Upgrade note

- Before upgrading from an earlier local `1.0.1` candidate, manually empty
  slots 19–27 in that candidate. This capacity reduction intentionally does
  not migrate the old third row; items stored in those high slots will not
  enter the new 18-slot inventory.

### Verification boundary

Automated bytecode contracts, persistence tests, and GameTests cover the
implemented rules above, but they do not replace real long-duration husbandry
or final in-game visual acceptance.

## [1.0.0] - 2026-07-17

### Initial public baseline and breaking identity change

- Renamed the project from **JS-revise** to **JS More**.
- Changed the Mod ID, artifact, registries, resources, data, configuration,
  attachments, payloads, system properties, and GameTest namespace from
  `jsrevise` to `jsmore`.
- Changed the Java package root from `com.palos.jsrevise` to
  `com.palos.jsmore` and the final artifact to `jsmore-1.0.0.jar`.
- Legacy JS-revise worlds, items, configurations, attachments, and network
  data are not migrated. There is no dual namespace, MissingMappings bridge,
  DataFixer, or legacy Mod ID alias. Back up worlds and treat JS More 1.0.0 as
  a new add-on installation.
- Raised the network protocol identity from `6` to `7`; payload field layouts
  remain unchanged.

### Added

- Completed the survival transport loop: brew anesthetic supplies, load a
  six-shot anesthetic crossbow, anesthetize a dinosaur, capture it with its
  original UUID/data, maintain supplies during transport, and release it at a
  safe destination.
- Added protected recovery carriers for relocation failures. Valid recovery
  items resist ordinary despawn and environmental loss while preserving one
  canonical dinosaur authority.
- Added a JS More advancement root that makes the dedicated tab visible after
  first entering a world without a toast or chat announcement, plus recipe
  discovery and two visible survival milestones for entering the anesthesia
  loop and preparing a transport kit.
- Added a project icon, mixed-license scope, third-party notices, asset
  provenance ledger, architecture/compatibility/development documentation,
  and bilingual Modrinth copy.
- Added a minimum-runtime verification profile and separate Travelers 0.7.1
  and 0.7.2 server/binary checks.

### Changed

- Travelers compatibility now patches only the known unsafe 0.7.1 renderer
  installation, accepts only provably client-guarded or bridge-free safe
  layouts, and fails fast on ambiguous structural drift.
- Jurassic Saga and Travelers dependency ranges are lower-bounded so a version
  number increase alone does not reject startup.
- Age overrides reject null, non-finite, zero, and negative values. Existing
  overrides take priority, then known adult ages, then unknown-species generic
  fallback.
- Repository metadata, POM, wrapper verification, CI action pinning, profile
  coverage, artifact audit, and optional-dependency isolation were tightened
  for public distribution.

### Fixed

- Stabilized Jurassic Saga 0.2.1 food-candidate sorting so dense searches no
  longer violate Java TimSort's comparator contract.

### Removed

- Removed old registry aliases and migrations that existed only for the
  legacy JS-revise identity, including the historical capture-cage alias,
  syringe projectile registration, loaded-syringe migration, and obsolete
  20/100 durability migrations.

### Safety and compatibility

- Valid recovery carriers are checked for item type, marker type, size, and
  controller identity before protection is applied. Forged or malformed
  markers do not receive permanent entity protection.
- Curios, Jade, TerraBlender, Sable, Create, and Aeronautics implementations
  remain optional. Missing optional mods cannot block the minimum runtime.
- Code and ordinary data/documentation use MIT; verified original visual assets
  use the JS More visual-assets license. Upstream-derived work remains outside
  the Palos ARR scope.

### Verification boundary

Automated profiles, binary checks, dedicated-server smoke tests, and artifact
audits are release gates, but they do not prove final behavior in the user's
full Jurassic Saga modpack. Version 1.0.0 must not be described as having
passed real in-pack acceptance until that testing is completed.
