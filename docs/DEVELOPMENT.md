# Developing JS More

## Repository boundary

The inner Git repository contains the mod source. The outer workspace contains
Codex governance, agents, skills, planning records, reference binaries, and
review material. Never add outer governance, `.planning`, reference mods,
unpacked dependencies, local run directories, or generated review assets to
the inner Git index.

The physical workspace directory name is not part of the public identity. The
release identity is `JS More` / `jsmore` / `com.palos.jsmore`.

## Toolchain

- Java 21;
- the checked-in Gradle Wrapper;
- Minecraft 1.21.1 and the NeoForge version declared in
  `gradle.properties`;
- network access only when Gradle must resolve a dependency not already in the
  verified cache.

Use the wrapper rather than a system Gradle installation. Do not replace the
official distribution URL or checksum with a machine-local mirror in a
release candidate.

## Profiles

Every release candidate is checked in four dependency environments:

| Profile | Purpose | Invocation |
| --- | --- | --- |
| minimal-runtime | Proves only the required Jurassic Saga stack and approved embedded library are needed | `.\gradlew.bat test runGameTestServer assemble -PruntimeProfile=minimal --rerun-tasks --warning-mode all --console=plain` |
| default | Normal development environment and general regression suite | `.\gradlew.bat test runGameTestServer assemble -PruntimeProfile=default --rerun-tasks --warning-mode all --console=plain` |
| Aeronautics exact | Loads the approved exact Sable/Create/Aeronautics binary set | `.\gradlew.bat test runGameTestServer assemble -PruntimeProfile=aeronautics --rerun-tasks --warning-mode all --console=plain` |
| pack-interop | Adds the approved coexistence mods that can alter compatibility bytecode | `.\gradlew.bat test runGameTestServer assemble -PruntimeProfile=pack-interop --rerun-tasks --warning-mode all --console=plain` |

The table must be synchronized with `build.gradle` and CI before release. Do
not copy temporary local flags into this document unless they are the stable
project interface.

Travelers 0.7.1 and 0.7.2 also receive separate minimal-runtime server smoke
tests:

```powershell
.\gradlew.bat runGameTestServer -PruntimeProfile=minimal -PtravelersRuntimeVersion=0.7.1 --rerun-tasks --warning-mode all --console=plain
.\gradlew.bat runGameTestServer -PruntimeProfile=minimal -PtravelersRuntimeVersion=0.7.2 --rerun-tasks --warning-mode all --console=plain
```

The minimum server log must prove Curios, Jade, TerraBlender, and Aeronautics
implementations were not loaded. Each release also runs bounded dedicated
servers for all four profiles, waits for `Done`, sends `stop`, and requires a
zero exit code.

## Change workflow

1. Read the outer `AGENTS.md`, matching project skills, current source, tests,
   resources, documentation, dependency binaries, and dirty diff.
2. Reproduce or prove the issue before changing code.
3. Keep write ownership disjoint when Subagents run in parallel.
4. Make the smallest change that satisfies explicit behavioral invariants.
5. Add focused tests at the same layer as the behavior: pure JUnit for helpers,
   GameTest for world/entity transactions, binary tests for optional-library
   structure, and resource tests for packaged references.
6. Run every affected profile, dedicated-server smoke, artifact audit, and
   `git diff --check` before readiness.
7. Use a separate Reviewer; call a Fixer only for a concrete finding, then
   repeat review and verification.

Before release, generate and audit metadata, the Maven POM, the final JAR, and
the publishing task graph without publishing anything:

```powershell
.\gradlew.bat generateModMetadata generatePomFileForMavenJavaPublication assemble auditReleaseArtifact --warning-mode all --console=plain
.\gradlew.bat publish --dry-run --warning-mode all --console=plain
```

Do not weaken a test globally to accommodate one dynamic reference. Record an
explicit allowlist and verify the runtime owner instead.

## Compatibility rules

- Common/server paths must not link client-only or optional implementation
  classes.
- Optional compatibility must be class-absence safe and fail closed on
  ambiguous structural drift.
- Runtime data is validated for type, length, range, null, enum, and finite
  numeric values.
- Preserve UNREADABLE/raw NBT and unknown fields; never map parse failure to an
  empty capture carrier.
- Service-side entity motion and collision changes must be real, not visual
  substitutions.
- General animal behavior uses stable base classes and runtime capabilities,
  not a species whitelist.

## Version policy

`1.0.0` is the JS More breaking-identity release and initial public-review
baseline. The next substantive release starts at `1.0.1`, followed by normal
patch increments on the 1.0 line unless the user explicitly approves another
semantic-version boundary. A task applies
one coordinated version change after behavior and resource gates pass.

Keep `gradle.properties`, README, CHANGELOG, generated metadata, POM, and final
artifact version consistent. A build-only or outer-governance task does not
change the mod version.

## Licenses and assets

Read `LICENSE`, `THIRD_PARTY_NOTICES.md`, and `ASSET_PROVENANCE.md` before
adding or modifying packaged resources.

- Code, tests, build/CI, ordinary data, language, recipes, tags, and docs use
  the MIT scope.
- Only assets marked ARR-VERIFIED use the project visual-assets license.
- Upstream-derived, referenced, or user-supplied work without an ownership
  assignment must not be relabeled as original Palos artwork.
- Every visual family records author/source, derivation method, license scope,
  inclusion status, and final hash.
- Unknown or unlicensed packaged assets block release.

## Release and GitHub safety

Green automation does not authorize commit, push, repository rename, publish,
release, or artifact upload. Before an authorized GitHub action, verify the
approved file list, current remote HEAD, repository identity, version,
licenses, provenance, secrets, local paths, and worktree cleanliness.

Never force-push. A repository rename must succeed before new-brand content is
pushed; on failure, stop without pushing to the old-brand remote.

Public release readiness still requires actual in-pack testing of visual,
movement, capture, relocation, save/reload, and optional-integration behavior.
Automated profiles do not substitute for that acceptance.
