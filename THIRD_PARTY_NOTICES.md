# Third-Party Notices

This notice records third-party material used, embedded, referenced, or loaded
only during development of JS More. It is an attribution and scope record, not
legal advice and not a substitute for the upstream license texts.

## Included in the repository or distributed artifact

### NeoForge MDK template portions

- Relationship: the repository was initialized from the NeoForge 1.21
  ModDevGradle MDK and still contains template-derived build files.
- Packaging: template-derived source files are present in the repository; they
  are not a separate runtime library in the mod JAR.
- License: MIT, Copyright (c) 2023 NeoForged project.
- Preserved notices: `TEMPLATE_LICENSE.txt` and
  `LICENSES/NeoForge-MDK-MIT.txt`.
- Official source: <https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle>
- Official template notice:
  <https://github.com/NeoForgeMDKs/MDK-1.21-ModDevGradle/blob/main/TEMPLATE_LICENSE.txt>

### Sable Companion

- Relationship: optional compatibility facade for projecting positions and
  interacting safely with Sable sub-levels.
- Packaging: the approved Sable Companion common library is embedded as a
  nested JAR. Sable itself and the Aeronautics implementation stack are not
  embedded.
- License: MIT. The nested JAR retains its own license metadata and notice.
- Official source: <https://github.com/ryanhcode/sable-companion>
- Official license: <https://github.com/ryanhcode/sable-companion/blob/main/LICENSE>

### Gradle Wrapper

- Relationship: repository build bootstrap only.
- Packaging: wrapper scripts and wrapper JAR are present in the source
  repository; the downloaded Gradle distribution is not included in the mod
  JAR.
- License: Apache License 2.0 for Gradle; see the official license.
- Official source: <https://github.com/gradle/gradle>
- Official license: <https://github.com/gradle/gradle/blob/master/LICENSE>

## Required at runtime but not redistributed

### Minecraft: Java Edition

- Relationship: game platform targeted by the mod.
- Runtime references: item models resolve the Minecraft potion bottle and
  crossbow animation bases through `minecraft:item/...` resource identifiers.
- Packaging: Minecraft code and texture pixels are not distributed in the JS
  More JAR; only independently authored sparse JS More overlay pixels are
  packaged for those items.
- Terms: Mojang/Microsoft EULA and Usage Guidelines apply independently.
- Official EULA: <https://www.minecraft.net/eula>
- Official Usage Guidelines: <https://www.minecraft.net/usage-guidelines>

### NeoForge

- Relationship: required mod loader and runtime API.
- Packaging: not embedded in the JS More JAR.
- License: GNU Lesser General Public License 2.1.
- Official source: <https://github.com/neoforged/NeoForge>
- Official license: <https://github.com/neoforged/NeoForge/blob/1.21.x/LICENSE.txt>

### Jurassic Saga

- Relationship: required host mod; JS More extends its animals, items, and
  survival systems.
- Packaging: not embedded in the JS More JAR.
- License: All Rights Reserved as declared by the official Modrinth project.
- Official project: <https://modrinth.com/mod/jurassic-saga>
- Official source: <https://github.com/Jurassic-Saga-Team/JurassicSaga>

### Travelers Lib

- Relationship: prerequisite declared by Jurassic Saga and a compatibility
  boundary used by sleeping/animation integration.
- Packaging: not embedded in the JS More JAR.
- License: All Rights Reserved as declared by the official Modrinth project.
- Official project: <https://modrinth.com/mod/travelers-lib>

## Optional integrations and test-only compatibility targets

The following projects are not embedded in the JS More JAR. Their names,
licenses, and trademarks remain with their respective owners.

| Project | Relationship | Upstream license | Official source/license |
| --- | --- | --- | --- |
| Curios API | Optional equipment-slot integration | LGPL-3.0-or-later | <https://github.com/TheIllusiveC4/Curios/blob/26.x/LICENSE> |
| Jade | Optional client information integration | CC-BY-NC-SA-4.0 | <https://modrinth.com/mod/jade> |
| TerraBlender | Optional world-generation integration | LGPL-3.0-only | <https://modrinth.com/mod/terrablender> |
| Create: Aeronautics / Simulated | Optional moving-structure compatibility target | Simulated Project License | <https://github.com/Creators-of-Aeronautics/Simulated-Project/blob/main/LICENSE.md> |
| Sable | Optional sub-level implementation | PolyForm Shield License 1.0.0 | <https://github.com/ryanhcode/sable/blob/main/LICENSE.md> |
| Create | Optional Aeronautics prerequisite in compatibility tests | Create Mod License | <https://github.com/Creators-of-Create/Create/blob/HEAD/LICENSE.md> |
| Create: Diesel Generators | Pack-interop test target | MIT | <https://github.com/george8188625/Create-Diesel-Generators> |
| Create: Enchantment Industry | Pack-interop test target | LGPL-3.0-or-later | <https://github.com/DragonsPlusMinecraft/CreateEnchantmentIndustry> |
| Create: Dragons Plus | Pack-interop test target | LGPL-3.0-or-later | <https://github.com/DragonsPlusMinecraft/CreateDragonsPlus> |

## Build tooling

- ModDevGradle is used to build and test the project and is not packaged in
  the mod JAR. It is licensed under LGPL-2.1:
  <https://github.com/neoforged/ModDevGradle>.
- JUnit and other resolved test/build dependencies are not packaged in the mod
  JAR. Their own metadata and licenses remain authoritative.

## Names and trademarks

Minecraft, NeoForge, Jurassic Saga, Travelers Lib, Curios, Jade, Sable,
Create, and other third-party names are used only to identify compatibility.
This project is not endorsed by those projects unless their owners state
otherwise.
