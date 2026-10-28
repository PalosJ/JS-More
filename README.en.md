# JS More

[简体中文](README.md) | **English**

JS More is a Jurassic Saga addon for **Minecraft 1.21.1 NeoForge**. It adds a way to sedate and transport dinosaurs, check their condition, and care for captured animals. An Egg Collector helps gather food for your egg-eating dinosaurs.

Source version: **1.1.0**. Download from [Modrinth](https://modrinth.com/mod/jurassic-saga-more). The source version does not imply an online release; check Modrinth for available files.

## Installation

Both the client and server need Java 21, NeoForge, and the **NeoForge 1.21.1 files** for all three mods below:

| Combination | JS More | Jurassic Saga | Travelers Lib |
| --- | --- | --- | --- |
| New baseline | 1.1.0 | [0.3](https://modrinth.com/mod/jurassic-saga/version/nti1NZVa) | [1.0](https://modrinth.com/mod/travelers-lib/version/I9ua8Njp) |
| Previous release | 1.0.0 | [0.2.3](https://modrinth.com/mod/jurassic-saga/version/EzDlTUHi) | [0.8.2.2](https://modrinth.com/mod/travelers-lib/version/Aadzy3jP) |
| Legacy support | 1.0.0 | [0.2.1](https://modrinth.com/mod/jurassic-saga/version/YdbXbcFo) | [0.7.2](https://modrinth.com/mod/travelers-lib/version/vKc8KD7H) |

The NeoForge build baseline is `21.1.232`, with an acceptance matrix that also includes `21.1.250`. Travelers `0.7.1` is retained for legacy dedicated-server regression checks; do not pair it with Jurassic Saga `0.2.3`. Different platforms may share a mod version number, so check the Minecraft version and loader when downloading.

Curios, Jade, TerraBlender, and the Aeronautics components are optional. Core features work without them.

## From anesthesia to transport

1. Brew an Anesthetic Potion from a poppy and an Awkward Potion, then use it with Jurassic Saga's empty syringes and feathers to make Anesthetic Darts.
2. Hold right-click to load the Anesthetic Crossbow, up to six darts, and left-click to fire. Doses take time to work and can build up over several shots.
3. Once the dinosaur is sufficiently sedated, put it in a capture box and take it to a new location.
4. Keep the box stocked with anesthetic, water, and suitable food, and watch its durability. Dino Doctor Goggles show the condition of both animals and boxes.
5. Choose a spacious, suitable place to release the dinosaur. If transport fails, it will be forcibly released nearby. Dangerous terrain can still injure or kill it.

A capture box occupies a 2×4-block footprint, stands 2 blocks tall, and has 500 maximum durability. Broken boxes cannot be repaired or used to capture another dinosaur. If an item's tooltip says its data cannot be read, keep it and do not treat it as an empty box.

## Keeping and breeding animals

By default, natural-despawn protection applies only to animals successfully captured in a JS More box. Their history survives release, recapture and reload; failed captures do not mark the animal, and offspring do not inherit it. All modes preserve upstream persistence and damage rules.

Breeding follows Jurassic Saga in full, including natural mate search, requirements, genetics and birth. JS More no longer adds a feeding gate or creates new maternal-only fertile eggs. Existing valid pending eggs from older saves are fulfilled once, clearing only after successful spawning. Invalid or temporarily unspawnable data is retained.

Despawn protection is not a population limit. Protected animals, naturally persistent upstream animals and ordinary breeding can still increase enclosure populations. Manage space, supplies and animal numbers accordingly.

## Egg collection and observation

The Egg Collector gathers dropped egg items within 16 blocks after roughly 5–5.5 seconds. It collects chicken eggs and Jurassic Saga alligator, ostrich, frog, basilisk, fish, and spider eggs. It leaves hatching eggs in place and does not keep chunks loaded. Right-click to open its 18 storage slots, or connect a hopper. Eggs that do not fit stay on the ground.

Dino Doctor Goggles use rate-limited server snapshots to show health, sex, hunger, thirst, mood, growth, estimated age, anesthesia and egg-laying progress. By default they omit only matching information actually rendered by Jade in that frame; full independent display is configurable. Look at a capture box to check its supplies and durability. The goggles work in the normal head slot, with support for the appropriate Curios slot when Curios is installed.

Capture settlement and release preserve unfinished adult growth; repeatedly observing or loading a box does not instantly complete it. Jade deduplication retains the age, hunger, thirst and mood panel.

Before adulthood, displayed age follows growth progress. After adulthood, it increases by one day for each in-game day's worth of server runtime. Sleeping or using `/time` to skip daylight does not suddenly add years. Older adult dinosaurs that have not received the age correction are adjusted once; previously corrected animals are not reset again.

## Configuration and upgrading

| File scope and option | Default | Effect |
| --- | --- | --- |
| COMMON `disable_jurassicsaga_biome_generation` | `false` | Optional biome-generation fallback; restart required, existing chunks unchanged |
| COMMON `debug_logging` | `false` | Additional diagnostics |
| SERVER `animal_despawn_protection` | `CAPTURED_ONLY` | `OFF`: upstream rules; `CAPTURED_ONLY`: captured animals; `ALL`: also land/water categories |
| CLIENT `show_full_goggles_information` | `false` | Always show full goggles information when enabled |

Files are `jsmore-common.toml`, `jsmore-server.toml` and `jsmore-client.toml`. The server owns and synchronizes despawn policy. A world's `serverconfig/jsmore-server.toml` may override the global file; restart the world/server after changing this mode.

Old despawn booleans are not converted. The feeding-based breeding switch and biome-reset migration are removed. Valid biome/debug choices remain; a missing new despawn setting uses `CAPTURED_ONLY`.

Old occupied boxes prove prior capture. Animals already released by older versions cannot be reliably identified and are not guessed. Protect old enclosures with the new `ALL` mode, or retain old occupied boxes until releasing them after upgrading. Animals outside the old land/water categories need a successful recapture to gain the new history.

Despawn protection does not cap breeding or the total animal population. Enclosures still need population management; `CAPTURED_ONLY` does not make every wild animal permanent.

Unloaded boxes settle anesthesia, supplies, hunger/thirst and durability against elapsed server game time, without loading chunks or counting shutdown time. Automatic care retains up to four future pending injections. Reloading an animal does not restart expired doses; manual item-box release also deducts supplies consumed during elapsed-time settlement. Occupied normal box drops cannot expire from item age, but still consume supplies and durability and retain environmental damage rules. Dart-hit damage is at most 1 HP and nonlethal for that hit; other damage can still kill.

1.1.0 uses network protocol `8`: update both client and server. Use public 1.0.0 for the older dependency pairs. 1.1.0 cannot run on Jurassic Saga 0.2.x; future binaries still require actual interface and behavior checks.

## Optional integrations and feedback

- Curios `9.5.1`: goggles equipment slot. Jade `15.10.6`: information display. TerraBlender `4.1.0.8`: biome integration.
- Moving capture boxes with physics supports Create `6.0.10` with either Aeronautics `1.3.0` / Sable `2.0.3`, or Aeronautics `1.3.2` / Sable `2.0.5`. Install the matching versions together. Unknown or incomplete combinations disable the transport integration.
- Unlisted newer versions have not been verified as compatible. Report problems through [GitHub Issues](https://github.com/PalosJ/JS-More/issues), including your mod versions, steps to reproduce, and relevant logs.

Some ostrich sounds in Jurassic Saga 0.3 contain damaged audio data and can exhaust the sound-channel pool without JS More installed. See the [compatibility notes](docs/COMPATIBILITY.md#support-expectations) for the cause and verification scope.

The host mod's Jade fence detection can also repeatedly log oversized-query errors on moving structures, reproducible without JS More. Temporarily hiding Jade while transporting boxes avoids that trigger; goggles remain available. These upstream issues are not patched by 1.1.0.

## Development

Use Java 21 and the repository's Gradle Wrapper. On macOS/Linux:

```sh
sh ./gradlew test runGameTestServer assemble -PupstreamProfile=current -PruntimeProfile=minimal
```

On Windows PowerShell:

```powershell
.\gradlew.bat test runGameTestServer assemble -PupstreamProfile=current -PruntimeProfile=minimal
```

See the [development guide](docs/DEVELOPMENT.md) for the full test matrix, dedicated-server checks, and artifact audits. The [architecture](docs/ARCHITECTURE.md) and [compatibility notes](docs/COMPATIBILITY.md) explain the design boundaries. Release changes are in the [changelog](CHANGELOG.md); publishing materials are in the [Modrinth description](MODRINTH_DESCRIPTION.md) and [release checklist](MODRINTH_RELEASE.md).

## License

Code, ordinary data, translations, and documentation use the [MIT License](LICENSES/MIT.txt). Original visual assets explicitly identified in the provenance ledger use the [JS More Visual Assets License](LICENSES/LicenseRef-JSMore-Visual-Assets.txt). Third-party and upstream material retain their own terms. See [LICENSE](LICENSE), [third-party notices](THIRD_PARTY_NOTICES.md), and [asset provenance](ASSET_PROVENANCE.md) for the full scope.
