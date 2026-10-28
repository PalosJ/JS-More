# JS More Compatibility

## Installation boundary

| Component | Relationship | Release boundary |
| --- | --- | --- |
| Minecraft: Java Edition | Required | `1.21.1` |
| NeoForge | Required | `21.1.232` or newer compatible 1.21.1 build |
| Jurassic Saga | Required host mod | `0.3` or newer structurally compatible binary |
| Travelers Lib | Required through the host-mod stack | `1.0` or newer structurally compatible binary |
| Sable Companion | Embedded compatibility facade | Approved nested version only |
| Curios | Optional | Missing mod disables Curios-slot detection only |
| Jade | Optional client integration | Missing mod disables Jade integration only |
| TerraBlender | Optional world-generation integration | Missing mod safely disables its Mixin/integration path |
| Sable/Create/Aeronautics implementations | Optional | Exact compatible structures enable moving-box support; unknown structures fail closed |

The minimum installation chain is JS More, Jurassic Saga, and the
prerequisites declared by Jurassic Saga. Curios, Jade, TerraBlender, and
moving-structure implementations are not required to enter a world or start a
dedicated server.

## Version-range policy

The 1.1.0 baseline is Jurassic Saga 0.3 (`nti1NZVa`) / Travelers 1.0
(`I9ua8Njp`), specifically their NeoForge 1.21.1 files. Public 1.0.0 retains
0.2.3 / 0.8.2.2 and 0.2.1 / 0.7.2 support; its extra Travelers 0.7.1 regression
belongs to that old artifact. Do not run 1.1.0 against the old package layout.

Jurassic Saga and Travelers use lower-bounded dependency ranges so a version
number change alone does not reject startup. This does not promise behavioral
compatibility with every future binary. If an API, class, descriptor, or
required behavior disappears, JS More must either use a proven fallback,
disable only the affected optional feature, or emit a precise fail-fast
diagnostic when continuing would be unsafe.

Do not tighten an upper bound merely because a newer version exists. First
test the actual binary and identify a functional incompatibility.

## Travelers Lib

Travelers 1.0 already guards renderer initialization on the client. The old
0.7.1 server-init transformer is retired from the release artifact. Historical
binary analyzers remain test fixtures, not runtime patches. Internal `azure`
packages belong to Travelers' animation implementation and do not require an
external AzureLib mod.

A separate 1.0 navigation defect leaves two non-daemon core workers alive after a
server saves and stops. This was reproduced without JS More. A class-fingerprint
gate allows a small initializer patch that marks newly created path workers as
daemon threads. The queue, limits, rejection policy and pathfinding remain
unchanged; unknown future class bytes are left unpatched with a diagnostic.

The new animator has no `beginFrame()` method. The renderer guards each animal's
bone-state lookup separately, while its actual two-argument update entry remains
verified. Sleeping one animal must not reuse or clear another animal's frame.

## Jurassic Saga animal compatibility

General anesthesia, observation, age, growth, capture, and release behavior is
derived from stable base classes and runtime capabilities. Known species may
have precision overrides, but a fixed species list cannot turn general
features on or off. Unknown future animals receive bounded generic defaults.

Age registration ignores null species, non-finite values, zero, and negative
values. An existing override takes priority, then a known built-in adult age,
then a generic size fallback for an unknown species.

The SERVER policy is OFF / CAPTURED_ONLY (default) / ALL. ALL covers vanilla
land/water creature categories, `jurassicsaga:js_land` / `js_water`, and captured
animals outside those categories. Every mode preserves upstream native persistence.
Only successful captures establish history; old occupied boxes are proof, but
previously released animals cannot be inferred. This is not a population cap.

All upstream breeding behavior is restored. Legacy gestation and module cooldown
fields in `js.metabolism` load into `js.reproduction`, with valid new data taking
priority. The animal-base mate-search cooldown is a separate field. Malformed
records retain recoverable raw data; an unreadable new record falls back to a
valid legacy record instead of discarding its pending gestation/cooldown.
Historical maternal eggs clear only after a successful spawn; no new such
records are created by ordinary feeding.

## Jurassic Saga food-task compatibility

Jurassic Saga 0.3 moves food gathering into `JSFoodSearch.gather`, but still
resamples random jitter inside its sort comparator. JS More fingerprints the
actual audited class before redirecting that single sort. Each candidate gets
one score per invocation. Search radius, forage profile, candidate limit and
pathfinding stay upstream-owned. Unknown class bytes are diagnosed and left
unpatched, rather than applying an unverified redirect.

The old 0.2.x food-task patches are not shipped in 1.1.0. Their binary analyzers
remain separate test fixtures for historical regression evidence.

## Optional client integrations

- **Curios:** goggles work in supported Curios head slots; without Curios the
  normal helmet slot remains available.
- **Jade:** animal/capture-box information is added or suppressed only when
  Jade is present; no Jade class enters common startup.
- **Resource reload:** model/animation capability caches are invalidated on
  reload and session changes.

Jurassic Saga 0.3 already removes its duplicate Jade option registration, so the
old corrective Mixin is retired. JS More never removes upstream tooltip rows.
Goggles suppress matching health, sex or genes only when the current Jade render
confirms those fields for the same animal. Unknown formatting keeps goggles data;
CLIENT `show_full_goggles_information=true` keeps everything. Hidden/disabled Jade
must not leave both displays empty. Capture-box requests also carry unique
request IDs and dimensions; timed-out requests retry, late replies cannot consume
a newer request, and old snapshots expire instead of remaining visibly frozen.
Animal observations keep one request pending for up to 80 ticks, rather than
replacing it at the 20-tick refresh interval; a delayed valid response remains
usable while lost requests still retry.

## World generation

Jurassic Saga biome generation remains enabled by default. The COMMON
`disable_jurassicsaga_biome_generation` setting is a restart-scoped fallback
for conflicts with other biome generators, not a universal compatibility fix.
Valid existing biome/debug values are retained; the old one-time reset and schema
key are no longer used. Existing chunks are not regenerated. TerraBlender is
optional: absence disables its hook and structural drift produces a diagnostic.
Disabling biome generation does not remove unrelated animals, fossils, items or
structures.

The SERVER despawn enum uses NeoForge restart caching and synchronization. Old
booleans are not converted; a missing enum defaults to CAPTURED_ONLY. The former
feeding-breeding setting is removed. Anesthesia and data preservation do not depend
on despawn mode.

## Moving-structure compatibility

Capture boxes are normal server-authoritative multiblocks first. Exact
Aeronautics/Sable compatibility adds safe sub-level projection, relocation,
breakage, recovery, and standard item automation without transferring data
authority to the physics mod.

The approved sets are Aeronautics/Simulated 1.3.0 with Sable 2.0.3, and
Aeronautics/Simulated 1.3.2 with Sable 2.0.5, both with Create 6.0.10. Their
audited movement and assembly classes have identical bytes, but the archive
fingerprints differ. Archive checks accept only the corresponding whole
sets; runtime checks require both approved versions and exact class bytes.

Unknown implementations remain non-movable rather than risking duplication or
data loss. Failure recovery preserves the original payload or one protected
recovery carrier; it must never create a second authority.

The exact binary matrix belongs in CI/build configuration and may evolve more
often than this document. A green compatibility profile is evidence for its
tested binaries, not a blanket claim for every fork or future version.

## Identity and upgrade boundary

The public Alpha established the `jsmore` identity. 1.1.0 retains the registries
and capture carrier identity, and advances network protocol to 8. Both endpoints
must upgrade together. Public 1.0.0 remains protocol 7. It does not repeat
the earlier internal identity migration or reset already-migrated adult ages.
Older internal namespaces remain unsupported; that is separate from updating
the public Alpha to the stable release.

## Support expectations

The NeoForge 1.21.1 Jurassic Saga 0.3 archive (`nti1NZVa`, verified against the
[official file metadata](https://api.modrinth.com/v2/version/nti1NZVa)) contains
corrupt ostrich audio. Its `ostrich_js_idle02.ogg`, `ostrich_js_idle03.ogg` and
`ostrich_js_idle04.ogg` fail Vorbis decoding with `Corrupt or missing data in
bitstream`; Ogg page checks also detect invalid checksums/truncated data.
`ostrich_js_aggressive03.ogg` has a truncated page as well.

In the original failing session, all 247 retained static channels belonged to
`jurassicsaga:ostrich_idle`. A separate client without JS More reproduced pool
exhaustion using that same sound. Minecraft 1.21.1 allocates channels before the
asynchronous buffer load; a failed buffer future leaves the never-started channel
unreleased by the normal stopped-channel sweep. This is an upstream asset and
vanilla error-handling interaction, not an addon sound emitter. JS More does not
replace upstream audio or claim to fix this missing-sound failure.

Jurassic Saga 0.3's Jade fence-cable ray callback also combines a player's world
eye position with a hit position inside a Sable sub-level. Looking at a moving
structure can therefore request an enormous AABB; Sable rejects it and repeatedly
logs a stack trace. This occurred with both approved physics sets. A separate
current-set client/server without JS More reproduced it on ordinary stone and
gold blocks, before any rotation. At a fixed view, hiding Jade stopped new errors
and showing it resumed them. Temporarily disabling Jade's display while working
on moving structures is a verified workaround; JS More goggles continue to work.
This upstream interaction remains unresolved in 1.1.0. Repeated error logging can
add client work and disk I/O, but is not evidence that it explains every stutter.

Short stutters need separate evidence. The original profiling session included
heap-inspection pauses from JFR object counts, chunk work, joins and saves. A
cleaner paired recording with heap-object counting disabled did not reproduce the
seconds-long stalls, but does not attribute every earlier warning or establish
large-pack capacity. Do not interpret total GC collection duration as pause time.

When reporting a compatibility problem, include the exact Minecraft,
NeoForge, Jurassic Saga, Travelers, and optional-integration versions, plus the
relevant log or crash report. Remove personal paths, account identifiers, and
credentials before sharing logs.
