# JS More Asset Provenance

This ledger records the source and licensing boundary of packaged visual
assets. Git authorship or a non-matching hash is not, by itself, proof of
original authorship. Final release verification must compare every packaged
asset with this ledger.

## Status vocabulary

- **ARR-VERIFIED**: the project has a traceable project-generation or
  user-owned source chain; the listed final files are covered by
  `LICENSES/LicenseRef-JSMore-Visual-Assets.txt`.
- **PROJECT-USE-AUTHORIZED**: the user supplied the material and explicitly
  instructed the project to include it, but the project does not claim that
  the whole work is original Palos ARR artwork.
- **UPSTREAM-EXCLUDED**: the asset references or derives from upstream visual
  material and is excluded from Palos ARR. Upstream terms remain applicable.
- **BLOCKED**: source or redistribution authority is unresolved. The asset
  must be replaced or the record resolved before release.

## Asset-family ledger

| Asset family | Final packaged paths | Author/source chain and method | License boundary | Inclusion | SHA-256 / status |
| --- | --- | --- | --- | --- | --- |
| Project icon | `src/main/resources/jsmore.png` | User-owned `icon_transparent_background.png`; the user confirmed publication rights. Source is resized deterministically to 256×256 with nearest-neighbor sampling, preserving the binary alpha channel and pixel edges. The local source path is intentionally not recorded. | ARR-VERIFIED | Yes | Source file: `219823525EB3F4E1307608C2EFD89D5B4A3CFA0046B008206431DF0005CBACD0`; final file: `77FBADB45E91C871D4165C05D3AB29BD48988E2E12BFC66A76C02D77F2FFDE62` |
| Complete dinosaur capture box | `assets/jsmore/blockstates/dinosaur_capture_box.json`; matching block/item models and `textures/block/dinosaur_capture_box_*.png` | VisualAssetWorker generated the original project candidates; ResourceWorker converted and integrated deterministic Minecraft pixel art. The user repeatedly directed and approved the white-gray transport-container form, door, palette, and badge. The badge silhouette uses the user-owned project-icon source above. Later changes were project-local pixel edits and historical project-resource revisions. | ARR-VERIFIED | Yes | Family manifest (24 files): `7E5D9D14F6008DA238C58DA84E9C322E46D36618170BB9C9580966D4DE8A51A2` |
| Broken dinosaur capture box and debris | Matching broken blockstate, models, `textures/block/broken_dinosaur_capture_box_*.png`, and debris sheet | Project-generated derivative of the verified complete capture-box family. VisualAssetWorker produced damage/debris candidates; ResourceWorker created transparent breaks and source-mapped debris; the user directed irregular tears, collision-independent fragments, and later revisions. No third-party texture source was introduced. | ARR-VERIFIED | Yes | Family manifest (10 files): `9E1874B7E1C8C4CDB0F9723850C14AE79203A15EE3893205441270C6C3AE435C` |
| Anesthetic dart item/entity | Item model plus `textures/item/anesthetic_dart.png` and `textures/entity/projectiles/anesthetic_dart.png` | VisualAssetWorker supplied a concept direction. Production pixels were not downscaled or traced from the generated preview: a deterministic pixel matrix and model UV contract were authored in-project, mechanically applied by ResourceWorker, reviewed, and user-approved. | ARR-VERIFIED | Yes | Family manifest (3 files): `53D4F20F5866315D57235C4D9BE071D4C72628CA0DBADEB1A8C2B77CA8511F0B` |
| Anesthetic crossbow states | Crossbow item-model chain and `textures/item/crossbow_*.png` | The project model chain and state layout were developed in-project. Final sprites were supplied from a user-edited directory and explicitly requested for inclusion; six loaded frames were later edited again by the user. The design retains a Minecraft crossbow visual reference. | PROJECT-USE-AUTHORIZED and UPSTREAM-EXCLUDED; not claimed as Palos ARR. Minecraft terms remain applicable. | Yes | Family manifest (21 files): `16C8E1795CDB9777396AFED91DAA9D0A1140C6B0BC5AAF26ECCC298826C0BF7E` |
| Anesthetic potion | Item model and `textures/item/anesthetic_potion.png` | Project recolor and pixel adaptation designed to resemble the Minecraft potion silhouette while using the anesthetic cyan palette. The visual reference is intentional and documented. | UPSTREAM-EXCLUDED; not Palos ARR. Reviewed under the Minecraft EULA and Usage Guidelines, which remain authoritative. | Yes | Family manifest (2 files): `E620B2E98F50D8B520AC2C76783C8C8E765D79B2E73038957798EF595A9C23E1` |
| Anesthetic syringe visual | `models/item/anesthetic_syringe.json` only | JS More packages only a model reference to `jurassicsaga:item/blue_syringe`; the referenced syringe model/texture is provided at runtime by Jurassic Saga and is not copied into this JAR. | UPSTREAM-EXCLUDED. The one-line reference data is in the MIT ordinary-data scope; Jurassic Saga artwork remains All Rights Reserved to its owner. | Reference only | File: `04DAA8A44CDCD517F1CE731143CB90FD1E1B301DD41AF2E9B41A2FAF2E349F4A`; family manifest (1 file): `EA24FA1E405A720DE72998842A8D1E59BB01487794EA32965E70B778753D1833` |
| Dino Doctor goggles | `models/item/dino_doctor_goggles.json` and `textures/item/dino_doctor_goggles.png` | The approved 1.0.134 model geometry and texture are retained unchanged; the JS More 1.0.0 identity migration only changes the resource namespace. | PROJECT-USE-AUTHORIZED | Yes | Texture file: `20809B1D786CD12A97944948F06ABCA53A41C68D3BB16F4F994B1AD7BA47D84E`; family manifest (2 files): `1C027225EF4500CC578514513026DB90C4A08BAED08FDA225FE8C0E1576A3E74` |

## Deterministic derivation evidence

The project icon was generated from the source file identified by the source
hash above. Pillow converted the source to RGBA, resized 1254×1254 to 256×256
with nearest-neighbor sampling, and wrote a lossless PNG with optimization
disabled and compression level 9. Both source and final alpha channels contain
only 0 and 255. The source file itself and its local path are not packaged.

## Family-manifest algorithm and scopes

Each family manifest is reproducible as follows:

1. enumerate the stated files relative to `src/main/resources/`;
2. normalize separators to `/` and sort paths using ordinal order;
3. for JSON files, decode as UTF-8 without BOM and normalize CRLF or lone CR
   line endings to LF before computing the lowercase file SHA-256; hash PNG
   and other binary files as their exact packaged bytes;
4. write one UTF-8 line without BOM per file as
   `relative/path<TAB>lowercase-file-sha256<LF>`; and
5. SHA-256 the complete manifest bytes.

The complete-box scope is its blockstate, the 16
`models/block/dinosaur_capture_box_*.json` proxies, its item model, and six
`textures/block/dinosaur_capture_box_*.png` faces. The broken-box scope is its
blockstate, block and item models, six damaged faces, and debris sheet. The dart
scope is its item model, item texture, and entity texture. The crossbow scope is
every `models/item/anesthetic_crossbow*.json` and
`textures/item/crossbow_*.png`. Potion, syringe, and goggles scopes are the
exact model/texture paths stated in the ledger; the syringe scope contains only
its single model-reference JSON.

## Non-packaged references and candidates

- Image-generation previews, enlarged review sheets, Blockbench working files,
  and rejected candidates in the outer workspace are not release assets and
  are not packaged.
- The anesthetic-dart preview established only high-level readability. The
  deterministic pixel matrix, not the generated bitmap, is the production
  source.
- The source project icon is not copied into the repository; only the approved
  256×256 derivative is packaged.

## Upstream usage boundary

Minecraft-derived or Minecraft-referential work is excluded from the project
ARR license. Minecraft's official EULA and Usage Guidelines remain the
authoritative terms:

- <https://www.minecraft.net/eula>
- <https://www.minecraft.net/usage-guidelines>

Jurassic Saga's referenced syringe visual is not packaged. Jurassic Saga is
listed as All Rights Reserved by its official Modrinth project:
<https://modrinth.com/mod/jurassic-saga>.

## Final-release gates

Before a public release, ResourceWorker/Verifier must:

1. recompute every final file and family-manifest SHA-256 using the algorithm
   above;
2. prove the packaged goggles texture is byte-identical to the approved 1.0.134
   texture and the model differs only by the required namespace migration;
3. confirm the icon is exactly the approved nearest-neighbor derivative;
4. confirm no asset with **BLOCKED** status is packaged;
5. compare source and JAR copies byte-for-byte; and
6. review perceptual similarity against Minecraft, Jurassic Saga, and local
   reference assets without treating a different hash as proof of originality.

No packaged asset family in this ledger has an unresolved hash or untraceable
source-chain status. Any future change to a scoped file invalidates its recorded
family manifest and blocks release until the ledger and tests are updated.
