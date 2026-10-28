# Developing JS More

## Workspace and toolchain

The inner Git repository is the only source repository. Outer AGENTS.md, project skills, agents, planning, reference binaries and unpacked upstream mods stay outside its index. Preserve existing user changes, including unrelated line-ending differences.

Use **Java 21** and the checked-in **Gradle Wrapper**. A system Gradle installation is unnecessary. Do not replace the official distribution URL or checksum.

On macOS, select the installed JDK for the current shell:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
sh ./gradlew --version
```

On Linux, set JAVA_HOME to the installed JDK 21 directory before running `sh ./gradlew`. On Windows, set JAVA_HOME to JDK 21 and use `.\gradlew.bat` in PowerShell. All properties below work on both platforms.

On a new machine, resolve dependencies online first. Keep dependency verification enabled: distinguish missing platform artifacts from changed hashes, and check official metadata and actual JARs before trusting either. Never reuse migrated `build/moddev` launch arguments. Regenerate them with `prepareServerRun` / `prepareClientRun`; an isolated `--project-cache-dir` avoids stale machine-specific project caches.

## Approved dependency matrix

`upstreamProfile` selects a tested pair, not independent version guesses:

| Profile | Jurassic Saga | Travelers |
| --- | --- | --- |
| `current` (default, 1.1.0) | 0.3 | 1.0 |

Old-host regression uses the preserved public 1.0.0 artifact separately, never the
1.1.0 source set. Platform-specific file IDs are pinned in Gradle and the generated
Maven POM because numerical version coordinates can resolve to another Minecraft
version. The release audit checks both required file IDs.

`physicsProfile` selects Aeronautics/Simulated 1.3.0 + Sable 2.0.3 (`legacy`), or 1.3.2 + 2.0.5 (`current`), with Create 6.0.10. It defaults to `current`. Keep archive and runtime structure checks for both exact sets; mixed and unknown sets must remain disabled.

The current baseline runs these runtime profiles:

| `runtimeProfile` | Loaded environment |
| --- | --- |
| `minimal` | Required Jurassic Saga/Travelers stack and approved embedded facade |
| `default` | Adds Curios, Jade and TerraBlender |
| `aeronautics` | Adds the selected exact physics stack |
| `pack-interop` | Also adds the approved Create coexistence mods |

Run both physics choices for the two physics runtime profiles. Also check the current baseline on NeoForge 21.1.250, retaining 21.1.232 as the build baseline. The CI matrix and build declarations are the exact version source of truth.

JUnit uses a separate game directory for every dependency combination. Do not copy the current host's generated configuration into a legacy test: upstream configuration formats can differ when testing the old public artifact.

## Verification

macOS/Linux example:

```sh
sh ./gradlew test runGameTestServer assemble -PupstreamProfile=current -PruntimeProfile=minimal --warning-mode all --console=plain
sh ./gradlew test runGameTestServer assemble -PupstreamProfile=current -PruntimeProfile=pack-interop -PphysicsProfile=current --warning-mode all --console=plain
```

Windows example:

```powershell
.\gradlew.bat test runGameTestServer assemble -PupstreamProfile=current -PruntimeProfile=default --warning-mode all --console=plain
```

Use `--rerun-tasks` when a fresh execution is required; changing a profile or dependency is already a tracked build input. Pure helpers use JUnit, actual world/entity transactions use GameTest, and optional-library integration uses real-binary structure tests. Do not globally weaken tests for one changed upstream asset or fixture.

For dedicated-server acceptance, run `runServer` for every approved environment, wait for `Done`, send `stop`, and require exit 0. `-PserverSmoke=true` selects an ephemeral port. `-PrunDirectory=<directory>` isolates development/test worlds and configs; use only test directories with the owner's existing EULA acceptance. Minimum-profile logs must show that optional implementations are absent.

For configuration changes, cover defaults, retained biome/debug choices, removed
old boolean keys, repeated restart and every new despawn mode. No config migration
is expected. Verify capture history across success/failure/reload/recapture and that
children do not inherit it. Test upstream breeding and one-time legacy pending eggs.
Compare saved raw growth before another upstream entity load can normalize it,
including the adult-stage threshold when it precedes maximum growth.
Compare short-step and long-gap transport settlement, resource exhaustion, clock
rollback, starvation boundaries, low health and growth-stage transitions. Test new
supply deposits after elapsed history, occupied item expiry and normal empty boxes.

Actual client acceptance covers hidden/disabled Jade, full goggles display, target
and world changes, flying/aquatic anesthesia and waking animation. Include an
isolated husbandry/transport workflow and at least one hour of sustained operation;
record tick time, memory, network and supplies. List any unperformed acceptance
separately; server GameTests do not establish client visual correctness.

JFR heap-object counting can request full heap inspections at chunk boundaries.
For pause attribution, record the JFR settings and GC causes, separate actual
stop-the-world pause time from collection duration, and avoid concurrent builds
or debugger suspensions during the measurement interval. Audio-pool failures
need the retained sound IDs and failed decode futures, not only a vanilla-sound
smoke test.

Measure hot paths before changing them. Record workload, accepted requests or transferred items, timing and data invariants. Helper microbenchmarks and short GameTests do not establish whole-server performance or long-duration pack stability.

## Release checks

```sh
sh ./gradlew generateModMetadata generatePomFileForMavenJavaPublication assemble auditReleaseArtifact --console=plain
sh ./gradlew publish --dry-run --console=plain
```

The dry run must not publish anything. Audit the actual final JAR, including metadata, protocol, optional dependency isolation, resources, licenses and provenance. Final packaging uses the minimum NeoForge baseline, not whichever newer matrix row ran last.

Code, ordinary data and docs use MIT; only verified original visuals use the visual-assets license. Read LICENSE, THIRD_PARTY_NOTICES.md and ASSET_PROVENANCE.md before changing packaged assets.

Public `1.0.0 Alpha` and `1.0.0 Release` are distinct records. 1.1.0 retains `jsmore` identity and uses protocol `8`, while public 1.0.0 uses `7`. Preserve existing age-migration markers; never restart a migration just because the release version changes. The user-approved compatibility release is 1.1.0; do not increment it again for fixes inside the same release.

## Review and external actions

Follow the outer project workflow. Subagents are used when the current request or applicable rules call for them; preserve the on-demand and full-workflow commands. Read-only reviewers report concrete findings, and a Fixer only receives a specific finding.

Before any authorized GitHub write, recheck remote HEAD, the approved file list, existing dirty changes, versions, licenses, provenance and secrets. Stage approved inner-repository changes individually. Never force-push or include outer governance, planning or build outputs.

A GitHub push does not authorize Modrinth edits, artifact uploads, GitHub releases or issue replies. Release materials are in MODRINTH_DESCRIPTION.md and MODRINTH_RELEASE.md. Actual visual, movement and long-term husbandry acceptance must be recorded separately from automated verification.
