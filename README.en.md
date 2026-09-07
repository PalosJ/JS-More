# JS More

[简体中文](README.md) | **English**

JS More is a Jurassic Saga addon for **Minecraft 1.21.1 NeoForge**. It adds a way to sedate and transport dinosaurs, check their condition, and raise animals through feeding and breeding. An Egg Collector helps gather food for your egg-eating dinosaurs.

Source version: **1.0.0 Release**. Download from [Modrinth](https://modrinth.com/mod/jurassic-saga-more). The public `1.0.0 Alpha` and this stable release are separate entries; check Modrinth for what is currently available.

## Installation

Both the client and server need Java 21, NeoForge, and the **NeoForge 1.21.1 files** for all three mods below:

| Combination | JS More | Jurassic Saga | Travelers Lib |
| --- | --- | --- | --- |
| Current supported pair | 1.0.0 | [0.2.3](https://modrinth.com/mod/jurassic-saga/version/EzDlTUHi) | [0.8.2.2](https://modrinth.com/mod/travelers-lib/version/Aadzy3jP) |
| Legacy support | 1.0.0 | [0.2.1](https://modrinth.com/mod/jurassic-saga/version/YdbXbcFo) | [0.7.2](https://modrinth.com/mod/travelers-lib/version/vKc8KD7H) |

The minimum tested NeoForge version is `21.1.232`, with additional coverage for `21.1.250`. Travelers `0.7.1` is retained for legacy dedicated-server regression checks; do not pair it with Jurassic Saga `0.2.3`. Different platforms may share a mod version number, so check the Minecraft version and loader when downloading.

Curios, Jade, TerraBlender, and the Aeronautics components are optional. Core features work without them.

## From anesthesia to transport

1. Brew an Anesthetic Potion from a poppy and an Awkward Potion, then use it with Jurassic Saga's empty syringes and feathers to make Anesthetic Darts.
2. Hold right-click to load the Anesthetic Crossbow, up to six darts, and left-click to fire. Doses take time to work and can build up over several shots.
3. Once the dinosaur is sufficiently sedated, put it in a capture box and take it to a new location.
4. Keep the box stocked with anesthetic, water, and suitable food, and watch its durability. Dino Doctor Goggles show the condition of both animals and boxes.
5. Choose a spacious, suitable place to release the dinosaur. If transport fails, it will be forcibly released nearby. Dangerous terrain can still injure or kill it.

A capture box occupies a 2×4-block footprint, stands 2 blocks tall, and has 500 maximum durability. Broken boxes cannot be repaired or used to capture another dinosaur. If an item's tooltip says its data cannot be read, keep it and do not treat it as an empty box.

## Keeping and breeding animals

By default, Jurassic Saga land and water creatures no longer despawn because players move away or leave them alone for a long time. They can still take damage, and Jurassic Saga's existing persistence rules remain in place.

For animals that breed by mating, feed both parents a food they accept. They must be the same species, opposite sexes, adult, fertile, and off their breeding cooldown. Jurassic Saga still handles courtship, genetics, and birth.

Ostriches, alligators, reed frogs, and basilisks lay eggs on their own cycles. Feed an eligible adult female to make her next egg a hatchable one carrying her genes. Ostriches eat seeds, alligators eat fish, and reed frogs and basilisks eat Jurassic Saga mosquitoes. After a successful fertile lay, feed her again for the next cycle. Pending eggs are preserved through saving, chunk reloads, and capture-box transport.

Servers can turn despawn protection and the breeding changes off independently. See the configuration options below.

## Egg collection and observation

The Egg Collector gathers dropped egg items within 16 blocks after roughly 5–5.5 seconds. It collects chicken eggs and Jurassic Saga alligator, ostrich, frog, basilisk, fish, and spider eggs. It leaves hatching eggs in place and does not keep chunks loaded. Right-click to open its 18 storage slots, or connect a hopper. Eggs that do not fit stay on the ground.

Dino Doctor Goggles show health, sex, hunger, thirst, mood, growth, age, anesthesia, and egg-laying progress. Look at a capture box to check its supplies and durability. The goggles work in the normal head slot, with support for the appropriate Curios slot when Curios is installed.

Before adulthood, displayed age follows growth progress. After adulthood, it increases by one day for each in-game day's worth of server runtime. Sleeping or using `/time` to skip daylight does not suddenly add years. Older adult dinosaurs that have not received the age correction are adjusted once; previously corrected animals are not reset again.

## Configuration

| File and option | Default | Effect |
| --- | --- | --- |
| `config/jsmore-common.toml` → `disable_jurassicsaga_biome_generation` | `false` | Optionally disables Jurassic Saga biome generation |
| `jsmore-server.toml` → `prevent_jurassicsaga_animal_despawn` | `true` | Prevents land and water creatures from despawning due to distance or inactivity |
| `jsmore-server.toml` → `enable_player_fed_breeding` | `true` | Enables feeding-based breeding and fertile eggs on periodic laying cycles |

**Jurassic Saga biomes generate normally by default.** If they conflict with other biome mods in your pack, set the biome-disable option to `true`. This is a simple fallback for those conflicts. Restart the game or server to apply it; existing chunks will not be regenerated.

When upgrading to this stable release, the biome-disable setting in an old-format config resets to `false` once. A backup is kept beside the original file with a `.pre-schema-1.bak` suffix, and `config_schema_version` is then set to `1`. If you choose `true` after that, your choice is preserved. Do not manually remove the format marker.

SERVER configuration normally lives in `config/`; a world's `serverconfig/jsmore-server.toml` can override it. The server controls both gameplay options and synchronizes them to clients. Restart the world or server after changing them. Disabling breeding restores Jurassic Saga's rules while keeping pending egg data, which is checked before use when breeding is enabled again.

## Optional integrations and feedback

- Curios `9.5.1`: goggles equipment slot. Jade `15.10.6`: information display. TerraBlender `4.1.0.8`: biome integration.
- Moving capture boxes with physics supports Create `6.0.10` with either Aeronautics `1.3.0` / Sable `2.0.3`, or Aeronautics `1.3.2` / Sable `2.0.5`. Install the matching versions together. Unknown or incomplete combinations disable the transport integration.
- Unlisted newer versions have not been verified as compatible. Report problems through [GitHub Issues](https://github.com/PalosJ/JS-More/issues), including your mod versions, steps to reproduce, and relevant logs.

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
