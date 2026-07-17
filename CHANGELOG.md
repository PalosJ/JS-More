# Changelog

All notable user-facing changes are recorded here.

## [1.1.0] - 2026-07-16

### Breaking identity change

- Renamed the project from **JS-revise** to **JS More**.
- Changed the Mod ID, artifact, registries, resources, data, configuration,
  attachments, payloads, system properties, and GameTest namespace from
  `jsrevise` to `jsmore`.
- Changed the Java package root from `com.palos.jsrevise` to
  `com.palos.jsmore` and the final artifact to `jsmore-1.1.0.jar`.
- Pre-1.1.0 worlds, items, configurations, attachments, and network data are
  not migrated. There is no dual namespace, MissingMappings bridge, DataFixer,
  or legacy Mod ID alias. Back up worlds and treat 1.1.0 as a new add-on
  installation.
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
- Added recipe-discovery advancements and two visible survival milestones for
  entering the anesthesia loop and preparing a transport kit.
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
  pre-1.1.0 identity, including the historical capture-cage alias, syringe
  projectile registration, loaded-syringe migration, and obsolete 20/100
  durability migrations.

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
full Jurassic Saga modpack. Version 1.1.0 must not be described as having
passed real in-pack acceptance until that testing is completed.
