# JS More Compatibility

## Installation boundary

| Component | Relationship | Release boundary |
| --- | --- | --- |
| Minecraft: Java Edition | Required | `1.21.1` |
| NeoForge | Required | `21.1.232` or newer compatible 1.21.1 build |
| Jurassic Saga | Required host mod | `0.2.1` or newer; no artificial upper bound |
| Travelers Lib | Required through the host-mod stack | `0.7.1` or newer; no artificial upper bound |
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

The tested host pairs are Jurassic Saga 0.2.1 / Travelers 0.7.2 and Jurassic
Saga 0.2.3 / Travelers 0.8.2.2. Travelers 0.7.1 is an additional legacy server
regression, not a valid prerequisite for the newer host. Builds pin the
platform-specific Modrinth file IDs because a shared numerical version can
resolve to a different Minecraft release.

Jurassic Saga and Travelers use lower-bounded dependency ranges so a version
number change alone does not reject startup. This does not promise behavioral
compatibility with every future binary. If an API, class, descriptor, or
required behavior disappears, JS More must either use a proven fallback,
disable only the affected optional feature, or emit a precise fail-fast
diagnostic when continuing would be unsafe.

Do not tighten an upper bound merely because a newer version exists. First
test the actual binary and identify a functional incompatibility.

## Travelers Lib

The server compatibility transformer recognizes only provable structures:

- the known 0.7.1 unsafe renderer-install sequence is patched narrowly;
- a renderer install proven to be inside a client-only branch is a safe
  no-op;
- a version in which the renderer bridge is absent but common initialization
  remains intact is a safe no-op; and
- partial, duplicated, incorrectly guarded, stack-invalid, or otherwise
  ambiguous structures fail fast with a diagnostic.

Both the old `onLoad(CoreServices)` and current `onLoad()` signatures are
recognized. Only the old unsafe initialization is eligible for mutation;
the no-argument entry must prove its client guard. Official 0.7.1, 0.7.2 and
0.8.2.2 binaries are separate regression inputs. Internal
packages whose names contain `azure` belong to Travelers' bundled animation
implementation; they do not imply an external AzureLib mod dependency.

## Jurassic Saga animal compatibility

General anesthesia, observation, age, growth, capture, and release behavior is
derived from stable base classes and runtime capabilities. Known species may
have precision overrides, but a fixed species list cannot turn general
features on or off. Unknown future animals receive bounded generic defaults.

Age registration ignores null species, non-finite values, zero, and negative
values. An existing override takes priority, then a known built-in adult age,
then a generic size fallback for an unknown species.

The despawn policy covers vanilla land/water creature categories and the
host's `jurassicsaga:js_land` / `jurassicsaga:js_water` categories. It preserves
upstream persistence and ambient behavior. Upstream 0.2.3 also removes old
concrete `JSAnimals` fields; test fixtures use the stable registry query.

## Jurassic Saga food-task compatibility

Jurassic Saga 0.2.1 and 0.2.3 create a food comparator whose result can
change while the same candidate pair is being sorted because it samples random
jitter during each comparison. This can violate Java TimSort's comparator
contract and abort dense food searches.

JS More gates a common Mixin with a narrow bytecode fingerprint. For the known
unsafe shape (`PATCH`), the redirect pre-samples one bounded jitter value per
candidate and sorts by the resulting stable score for that invocation. If the
old sorting path is proven completely absent (`SAFE_NO_OP`), no patch is
applied. Any other structural drift is diagnosed and left unpatched rather
than allowing an unverified redirect to block startup. The game can still
start, but the affected upstream food-search behavior must be treated as
unverified until that exact binary is tested.

The legacy redirect targets `findTargets(float, Vec3)` / `ArrayList.sort`.
The current redirect targets private `gatherCandidates(Level, float)` /
`List.sort`, with exact checks for its caller, lambda, locals and jitter
helper arithmetic. Only one matching Mixin is applied. The newer competitive
path requests and candidate limit remain upstream-owned.

Remove this workaround only after the upstream implementation no longer uses
the unsafe comparator and an official replacement binary has been verified.

## Optional client integrations

- **Curios:** goggles work in supported Curios head slots; without Curios the
  normal helmet slot remains available.
- **Jade:** animal/capture-box information is added or suppressed only when
  Jade is present; no Jade class enters common startup.
- **Resource reload:** model/animation capability caches are invalidated on
  reload and session changes.

Jurassic Saga 0.2.3 explicitly adds two Jade config keys that Jade 15.10.6
already creates from its entity providers. This makes the host plugin's
registration session throw before installing its ray-trace callback. A
client-only redirect removes just those two duplicate additions. It requires
exact class hashes for the host plugin, both providers, Jade's session and
registration implementation, and the provider defaults. Missing or changed
classes disable this workaround; the ordinary JS More integration remains
independent. The capture-box Jade option has both English and Chinese labels.

## World generation

Jurassic Saga biome generation remains enabled by default. The COMMON
`disable_jurassicsaga_biome_generation` setting is a restart-scoped fallback
for conflicts with other biome generators, not a universal compatibility fix.
Legacy configurations are backed up before NeoForge corrects their contents;
the loading event resets the flag once and writes schema 1. Later opt-in is
preserved. Existing chunks are not regenerated. TerraBlender is
optional: class absence disables the TerraBlender-specific hook, while a
present but structurally incompatible class produces a targeted diagnostic.
Disabling biome generation does not remove Jurassic Saga animals, fossils,
items, or structures outside that specific generation path.

The independent SERVER options for despawn protection and player-fed breeding
default to true and use NeoForge world-restart caching and client sync.
Disabling breeding restores upstream behavior without clearing pending egg
data. Re-enabling validates the retained data before use. Anesthesia has its
own Mixins and is independent of both switches.

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

## JS More 1.0.0 identity boundary

The public Alpha established the `jsmore` identity. The stable 1.0.0 release
keeps its registries, capture format and network protocol 7. It does not repeat
the earlier internal identity migration or reset already-migrated adult ages.
Older internal namespaces remain unsupported; that is separate from updating
the public Alpha to the stable release.

## Support expectations

When reporting a compatibility problem, include the exact Minecraft,
NeoForge, Jurassic Saga, Travelers, and optional-integration versions, plus the
relevant log or crash report. Remove personal paths, account identifiers, and
credentials before sharing logs.
