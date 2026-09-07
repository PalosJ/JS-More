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
| `legacy` | 0.2.1 | 0.7.2 |
| `current` (default) | 0.2.3 | 0.8.2.2 |

Only the legacy profile accepts `-PtravelersRuntimeVersion=0.7.1` for its additional server regression. Platform-specific Modrinth file IDs are pinned in the build because numerical version coordinates can resolve to another Minecraft version.

`physicsProfile` selects Aeronautics/Simulated 1.3.0 + Sable 2.0.3 (`legacy`), or 1.3.2 + 2.0.5 (`current`), with Create 6.0.10. It defaults to the upstream profile. Keep archive and runtime structure checks for both exact sets; mixed and unknown sets must remain disabled.

Each upstream runs these runtime profiles:

| `runtimeProfile` | Loaded environment |
| --- | --- |
| `minimal` | Required Jurassic Saga/Travelers stack and approved embedded facade |
| `default` | Adds Curios, Jade and TerraBlender |
| `aeronautics` | Adds the selected exact physics stack |
| `pack-interop` | Also adds the approved Create coexistence mods |

Run both physics choices for each upstream's two physics profiles. Also check both upstreams on NeoForge 21.1.250, retaining 21.1.232 as the build baseline. The CI matrix and build declarations are the exact version source of truth.

JUnit uses a separate game directory for every dependency combination. Do not copy the current host's generated configuration into a legacy test: upstream configuration formats can differ even when JS More supports both binaries.

## Verification

macOS/Linux example:

```sh
sh ./gradlew test runGameTestServer assemble -PupstreamProfile=legacy -PruntimeProfile=minimal --warning-mode all --console=plain
sh ./gradlew test runGameTestServer assemble -PupstreamProfile=current -PruntimeProfile=pack-interop -PphysicsProfile=current --warning-mode all --console=plain
sh ./gradlew runGameTestServer -PupstreamProfile=legacy -PruntimeProfile=minimal -PtravelersRuntimeVersion=0.7.1 --console=plain
```

Windows example:

```powershell
.\gradlew.bat test runGameTestServer assemble -PupstreamProfile=current -PruntimeProfile=default --warning-mode all --console=plain
```

Use `--rerun-tasks` when a fresh execution is required; changing a profile or dependency is already a tracked build input. Pure helpers use JUnit, actual world/entity transactions use GameTest, and optional-library integration uses real-binary structure tests. Do not globally weaken tests for one changed upstream asset or fixture.

For dedicated-server acceptance, run `runServer` for every approved environment, wait for `Done`, send `stop`, and require exit 0. `-PserverSmoke=true` selects an ephemeral port. `-PrunDirectory=<directory>` isolates development/test worlds and configs; use only test directories with the owner's existing EULA acceptance. Minimum-profile logs must show that optional implementations are absent.

For configuration changes, cover fresh installation, both old biome values, repeated restart, manual opt-in after migration, and each SERVER switch independently. Test disabled breeding with existing pending eggs, then re-enable it. Keep anesthesia, data preservation and age migration independent of these switches.

Measure hot paths before changing them. Record workload, accepted requests or transferred items, timing and data invariants. Helper microbenchmarks and short GameTests do not establish whole-server performance or long-duration pack stability.

## Release checks

```sh
sh ./gradlew generateModMetadata generatePomFileForMavenJavaPublication assemble auditReleaseArtifact --console=plain
sh ./gradlew publish --dry-run --console=plain
```

The dry run must not publish anything. Audit the actual final JAR, including metadata, protocol, optional dependency isolation, resources, licenses and provenance. Final packaging uses the minimum NeoForge baseline, not whichever newer matrix row ran last.

Code, ordinary data and docs use MIT; only verified original visuals use the visual-assets license. Read LICENSE, THIRD_PARTY_NOTICES.md and ASSET_PROVENANCE.md before changing packaged assets.

Public `1.0.0 Alpha` and `1.0.0 Release` are distinct records. The stable release retains `jsmore` identity and protocol `7`. Preserve existing age-migration markers; never restart a migration just because the release version changes. Later substantive releases increment from `1.0.1` unless explicitly approved otherwise.

## Review and external actions

Follow the outer project workflow. Subagents are used when the current request or applicable rules call for them; preserve the on-demand and full-workflow commands. Read-only reviewers report concrete findings, and a Fixer only receives a specific finding.

Before any authorized GitHub write, recheck remote HEAD, the approved file list, existing dirty changes, versions, licenses, provenance and secrets. Stage approved inner-repository changes individually. Never force-push or include outer governance, planning or build outputs.

A GitHub push does not authorize Modrinth edits, artifact uploads, GitHub releases or issue replies. Release materials are in MODRINTH_DESCRIPTION.md and MODRINTH_RELEASE.md. Actual visual, movement and long-term husbandry acceptance must be recorded separately from automated verification.
