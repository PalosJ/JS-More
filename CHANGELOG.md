# Changelog

This log compares public releases. Internal candidate versions are not separate public upgrades.

## 1.0.0 — Release

Changes since the public **1.0.0 Alpha**:

### Added

- Egg Collector: gathers base egg items within 16 blocks after roughly five seconds, with 18 storage slots and hopper support. Items that do not fit remain on the ground.
- Player-fed breeding: eligible ordinary parents must both be fed. An eligible female ostrich, alligator, reed frog or basilisk can be fed to produce one fertile entity egg on her next laying cycle. Valid pending eggs survive saving, reloading and capture-box transport.
- Default protection against distance- and inactivity-based despawning for Jurassic Saga land and water creatures, including the host's new custom spawn categories.
- Independent server options for despawn protection and player-fed breeding. Both default to enabled; disabling breeding preserves pending egg data.

### Fixed and changed

- Jurassic Saga biome generation is now **enabled by default**. The optional biome-disable setting is intended as a simple workaround for conflicts with other biome mods.
- Legacy common configurations are backed up and reset once to keep biome generation enabled. Later manual opt-in survives restarts. Restart the game or server after editing the setting; existing chunks are unaffected.
- Adult age follows elapsed server runtime instead of daylight jumps. Old adult dinosaurs are corrected once; previously migrated animals are not reset again.
- Supports Jurassic Saga 0.2.3 / Travelers 0.8.2.2 alongside the legacy 0.2.1 / 0.7.2 pair. Travelers 0.7.1 retains its dedicated-server fix.
- Fixes unstable food-candidate sorting in both supported host layouts while preserving the newer pathfinding flow.
- Adds the exact Aeronautics 1.3.2 / Sable 2.0.5 combination alongside 1.3.0 / 2.0.3, with Create 6.0.10. Unknown or mixed combinations keep physics integration disabled.
- Adds Jade 15.10.6 coverage. Build dependencies use platform-specific file IDs to avoid downloading another Minecraft version with the same version number.
- Fixes the host's duplicate Jade option registration in the verified 0.2.3 / 15.10.6 combination and supplies the missing capture-box option label.
- Full Egg Collectors skip unnecessary entity scans. Capture-box settlement is spread across ticks, and request-limit cleanup runs at most once per tick.
- Rewrites installation and configuration guidance, explains transport failure, and clarifies the separate code and visual-asset license scopes.

### Upgrade notes

The anesthesia, capture and transport tools already existed in Alpha. This release keeps their behavior, the `jsmore` identity, capture data format and network protocol `7`.

Forced nearby release is retained. Dangerous terrain can injure or kill the dinosaur, and broken boxes cannot be repaired or reused.

Install matching Minecraft 1.21.1 NeoForge files on both client and server. Keep the Alpha release record and artifact separate; the stable release does not replace that download.

## 1.0.0 — Alpha — 2026-07-18

Initial public JS More release, including anesthetic supplies and darts, the six-shot crossbow, dinosaur capture and transport, supply management, observation goggles, recovery handling and survival advancements.

The older internal JS-revise identity was replaced by `jsmore` for this public baseline. The stable release above keeps that identity. See the [published Alpha](https://modrinth.com/mod/jurassic-saga-more/version/jrAX7Rcv) for the original download.
