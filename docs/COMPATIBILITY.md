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

Official 0.7.1 and 0.7.2 binaries are separate smoke-test inputs. Internal
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

## Jurassic Saga food-task compatibility

Jurassic Saga 0.2.1's `JSFindFoodTask` creates a comparator whose result can
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

Remove this workaround only after the upstream implementation no longer uses
the unsafe comparator and an official replacement binary has been verified.

## Optional client integrations

- **Curios:** goggles work in supported Curios head slots; without Curios the
  normal helmet slot remains available.
- **Jade:** animal/capture-box information is added or suppressed only when
  Jade is present; no Jade class enters common startup.
- **Resource reload:** model/animation capability caches are invalidated on
  reload and session changes.

## World generation

Jurassic Saga biome generation controls are restart-scoped. TerraBlender is
optional: class absence disables the TerraBlender-specific hook, while a
present but structurally incompatible class produces a targeted diagnostic.
Disabling biome generation does not remove Jurassic Saga animals, fossils,
items, or structures outside that specific generation path.

## Moving-structure compatibility

Capture boxes are normal server-authoritative multiblocks first. Exact
Aeronautics/Sable compatibility adds safe sub-level projection, relocation,
breakage, recovery, and standard item automation without transferring data
authority to the physics mod.

Unknown implementations remain non-movable rather than risking duplication or
data loss. Failure recovery preserves the original payload or one protected
recovery carrier; it must never create a second authority.

The exact binary matrix belongs in CI/build configuration and may evolve more
often than this document. A green compatibility profile is evidence for its
tested binaries, not a blanket claim for every fork or future version.

## JS More 1.0.0 identity boundary

JS More 1.0.0 is the initial public-review baseline and is intentionally
incompatible with legacy project registry, configuration, attachment,
payload, and resource identities. There is no
MissingMappings bridge, DataFixer, dual namespace, or legacy Mod ID alias.
Back up worlds before switching identities and treat JS More 1.0.0 as a new add-on
installation.

## Support expectations

When reporting a compatibility problem, include the exact Minecraft,
NeoForge, Jurassic Saga, Travelers, and optional-integration versions, plus the
relevant log or crash report. Remove personal paths, account identifiers, and
credentials before sharing logs.
