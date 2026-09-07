# JS More Architecture

## Purpose

JS More is a NeoForge 1.21.1 add-on for Jurassic Saga. Its primary design
goal is a server-authoritative survival loop in which players anesthetize a
living dinosaur, capture it without cloning or losing its identity, transport
it, maintain its supplies, and release it safely.

The public identity is:

- display name: `JS More`;
- Mod ID and resource namespace: `jsmore`;
- Java package root: `com.palos.jsmore`;
- Maven artifact: `jsmore`.

## Layer boundaries

### Bootstrap and registries

`JSMore` is the common entry point. Registry classes own items, blocks, block
entities, entities, creative tabs, data attachments, serializers, brewing,
and network registration. Registration code must not execute client-only
classes on a dedicated server.

### Server domains

- **Anesthesia and motion** own dose timing, pending/active projections,
  sleeping, AI suppression, fluid contact, collision-aware floating, and
  server synchronization.
- **Capture** owns the capture transaction, entity snapshot, original UUID,
  supplies, durability, item/block-entity transfer, release, broken-box
  conversion, recovery carriers, and multiblock integrity.
- **Profiles and age** derive safe species-independent defaults and apply
  narrow known-species precision overrides. A species list is never a general
  feature switch.
- **Observation** builds bounded snapshots for the client HUD. It cannot grant
  game-state authority to the client.
- **World generation and configuration** isolate Jurassic Saga biome controls
  and optional integration behavior from unrelated gameplay systems.
- **Breeding and despawn rules** have separate Mixins and independent SERVER
  settings. COMMON handles biome generation and one-time configuration format
  migration. NeoForge synchronizes SERVER values and caches them until the
  world restarts; pending breeding state remains intact while disabled.

Release first searches suitable nearby positions and retains the approved
forced-nearby fallback. It is not a guarantee against hazardous terrain. The
fallback may expose the animal to damage; it must still preserve one authority.

### Client presentation

Client packages render the Dino Doctor HUD, item/entity models, particles,
and safe animation fallbacks. Client caches are bounded and are cleared on
session, world, or resource lifecycle changes. Client prediction may improve
presentation but never commits persistent gameplay state.

### Optional compatibility

Optional integrations are guarded by class presence, metadata, Mixin plugins,
and exact structural checks where needed. Curios, Jade, TerraBlender, Sable,
Create, and Aeronautics implementations must be absent-safe. Their classes
must not leak into the minimum runtime path.

Sable Companion is an embedded no-op-safe facade. When an exact supported
moving-structure implementation is present, it enables sub-level projection
without making that implementation a mandatory dependency.

## Authority and persistence

### Server authority

The server is authoritative for entity state, AI, position, collision,
capture/release decisions, NBT, attachments, supplies, durability, UUID
uniqueness, and network validation. C2S requests are treated as requests, not
commands: the server revalidates player, hand, item, target, distance, rate,
and current state.

### Capture data states

Capture carriers distinguish three states:

- **EMPTY**: the capture key is absent;
- **VALID**: the payload parses and can participate in normal gameplay;
- **UNREADABLE**: raw data exists but cannot be safely interpreted.

UNREADABLE data is preserved byte-for-byte across item/block-entity/drop
transfers. It is not silently converted to empty data, settled, released, or
overwritten. Unknown fields inside valid payloads are also retained where the
format permits.

The original dinosaur UUID is part of the authority invariant. At any
transaction boundary, the source entity, target box, and protected recovery
carrier must resolve to exactly one canonical authority.

### Recovery carrier

If a moving-structure transaction cannot restore a canonical box safely, a
validated recovery item may temporarily hold the single payload. Protected
world entities are visibly marked and hardened against ordinary despawn and
environmental loss. The marker remains on the ItemStack until successful
canonical restoration, so picking up and dropping the item does not destroy
the recovery path. Forged or malformed markers never gain permanent
protection.

### Network identity

JS More 1.0.0 Alpha and Release both use network protocol `7`. Payload field layouts
remain deliberately small and validated, but payload IDs, attachments,
registries, configuration, resources, and GameTest data all use the `jsmore`
namespace. Protocol changes require coordinated registration, codec, test,
and compatibility documentation updates.

## Resource architecture

Resources are split between `assets/jsmore` and `data/jsmore`, with standard
Minecraft/Curios/optional-mod tag namespaces used only where their data-pack
contracts require it. Dynamic renderer references must be represented by an
explicit integrity-test allowlist, not by globally weakening reference tests.

Visual assets follow the mixed-license scope in `LICENSE` and
`ASSET_PROVENANCE.md`. Source code, ordinary data, language, recipes, tags,
tests, build files, CI, and documentation use the MIT scope. Only explicitly
verified original visual assets use the project visual-assets license.

## Core invariants

Changes must preserve these invariants:

1. no client class is required to start a dedicated server;
2. optional integrations may disable themselves, but cannot block the minimum
   dependency runtime;
3. real movement and collision changes occur on the server, not only in
   rendering;
4. unknown or unreadable persistent data is preserved rather than erased;
5. capture/release/relocation leaves exactly one dinosaur authority;
6. future Jurassic Saga animals receive safe generic behavior unless a
   capability is genuinely unavailable;
7. hot-path caches are bounded and lifecycle-aware; and
8. packaged resources, licenses, notices, namespaces, and metadata agree with
   the final artifact.
