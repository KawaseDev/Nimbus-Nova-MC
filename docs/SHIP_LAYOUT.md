# Spaceship schematic integration

Source: `ananaslafelie-1794683726306.schem` (team-supplied Git asset). SHA-256 `acf7570450813a6e2fa0b02fd6b5c9a648c7e216b3290b4c422f7115229362c5`.

The newer schematic is Sponge v2, data version 5023, dimensions **26 × 31 × 82**, containing **16,376 non-air blocks** and 202 palette states. The older `1794683017856` version has the same dimensions and task anchors but fewer doorway markers: zero lime-wool blocks versus 118 in this source. Use the newer file.

The ship points toward +Z: engines are aft near local Z=3, and the pilot console is at Z=65. Lower-deck feet are normally Y=12; the upper deck is normally Y=17, with a lowered bridge at Y=16 and raised galley equipment at Y=18.

## Placement and entry

Use world origin **(−13, 64, −41)**: world = local + origin. Ignore the source world-offset metadata `(248, −54, −219)`; it records the original clipboard position, not an installation requirement.

**Bridge spawn:** local `(13,16,64)`, world `(0,80,23)`, with a solid smooth-quartz floor and two air blocks. Upper corridor alternative: local `(13,17,52)`, world `(0,81,11)`, with a deepslate floor and noncolliding light/air above.

Ladders join decks at local X/Z `(2,17)`, `(24,17)`, `(4,60)`, `(22,60)` through Y=12…16. Preserve these routes and their upper landings. The schematic contains original iron doors as well as lime panels; the integer-grid route check assumes both become passable doors.

A planning flood fill reaches 1,670 integer-grid standing positions from the bridge. Every task marker has a reached standing position within five blocks. This approximates stairs/slabs and treats replacement doors as open; it does not replace the in-game walking check.

## Station markers

| # | Purpose | Source marker | Local XYZ | World XYZ | Nearby reachable local XYZ |
| --- | --- | --- | --- | --- | --- |
| 1 | Comms | `cartography_table` | `(4, 12, 22)` | `(-9, 76, -19)` | `(4, 13, 22)` |
| 2 | Science | `beacon` | `(20, 12, 29)` | `(7, 76, -12)` | `(21, 12, 29)` |
| 3 | Plants | `composter` | `(5, 12, 42)` | `(-8, 76, 1)` | `(5, 13, 42)` |
| 4 | Storage | `chest` | `(22, 12, 54)` | `(9, 76, 13)` | `(22, 12, 55)` |
| 5 | Punching bag | `red_wool` | `(8, 13, 53)` | `(-5, 77, 12)` | `(9, 12, 53)` |
| 6 | Punching bag | `red_wool` | `(8, 14, 53)` | `(-5, 78, 12)` | `(9, 12, 53)` |
| 7 | Pilot | `lectern` | `(13, 16, 65)` | `(0, 80, 24)` | `(13, 16, 64)` |
| 8 | Meeting | `note_block` | `(21, 17, 33)` | `(8, 81, -8)` | `(21, 18, 33)` |
| 9 | Medical | `fletching_table` | `(21, 17, 45)` | `(8, 81, 4)` | `(21, 17, 46)` |
| 10 | Engine | `furnace` | `(5, 18, 3)` | `(-8, 82, -38)` | `(4, 19, 3)` |
| 11 | Engine | `furnace` | `(21, 18, 3)` | `(8, 82, -38)` | `(22, 19, 3)` |
| 12 | Galley | `brewing_stand` | `(5, 18, 56)` | `(-8, 82, 15)` | `(4, 18, 56)` |

The two red-wool cells form one two-block-tall punching-bag placeholder. Keep the lower anchor and replace/remove its upper placeholder as required by the custom model.

## Door panels

Lime wool forms 16 separate axis-aligned panels. Clear the entire panel before installing a matching portal: most are three blocks high and two or three wide, so copying the old two-high doors without clearing the third row would leave a blocked passage. All exact cells and both local/world bounds are in [ship-layout.json](ship-layout.json).

| Local minimum | Local maximum | Facing axis |
| --- | --- | --- |
| `(10, 12, 23)` | `(10, 14, 24)` | x |
| `(10, 12, 47)` | `(10, 14, 48)` | x |
| `(10, 17, 22)` | `(10, 18, 22)` | x |
| `(10, 17, 47)` | `(10, 19, 49)` | x |
| `(12, 12, 19)` | `(14, 14, 19)` | z |
| `(12, 12, 28)` | `(14, 14, 28)` | z |
| `(12, 12, 40)` | `(14, 14, 40)` | z |
| `(12, 12, 58)` | `(14, 14, 58)` | z |
| `(12, 17, 19)` | `(14, 19, 19)` | z |
| `(12, 17, 25)` | `(14, 19, 25)` | z |
| `(12, 17, 43)` | `(14, 19, 43)` | z |
| `(12, 17, 57)` | `(14, 19, 57)` | z |
| `(16, 12, 23)` | `(16, 14, 24)` | x |
| `(16, 12, 47)` | `(16, 14, 48)` | x |
| `(16, 17, 22)` | `(16, 18, 22)` | x |
| `(16, 17, 47)` | `(16, 19, 49)` | x |

## Runtime block data

Generated resource: `src/main/resources/data/nimbus_nova/ship/return_window.nvs.gz`. SHA-256 `583ea891b675019916e98caaa8e1f9273b5012565278c4d79c866c27bf5ed640`.

Decompress gzip, then read big-endian fields: ASCII magic `NVS1`; unsigned-short width, height, length; unsigned-short palette count; for each palette state, unsigned-short UTF-8 byte length then those bytes; unsigned-int non-air entry count; each entry is unsigned-int linear index followed by unsigned-short palette ID. Linear index is `x + z*width + y*width*length`. The current palette is ASCII, so Java `DataInputStream.readUTF()` also reads its strings; ordinary length-prefixed UTF-8 is the formal format.

The resource preserves all original block-state strings and geometry. It intentionally carries no entities, command contents, inventories, sign text or external executable behavior. There are 115 omitted block-entity payloads, including 36 blank signs and two command blocks at local `(20,8,59)` and `(21,8,59)`. Replace those two command-block materials with inert hull during runtime installation.

The palette includes `gray_wool_stairs`, `light_gray_wool_stairs`, `light_gray_concrete_slab`, `white_concrete_slab` and `iron_chain` families. Confirm their availability in the actual client registry. If absent, use a deliberate closest-shape replacement preserving compatible facing/half/type properties; never silently replace unsupported structural blocks with air. The full palette and counts are in the JSON manifest.

Regenerate and run format/truncation checks with:

```sh
python3 tools/inspect_ship_schematic.py
```

The exporter bounds compressed/decompressed input, volume and palette size; validates VarInts, palette references and safe spawn clearance; and round-trips every exported entry. [Floor plan](ship-layout.svg) shows both decks and marker numbers. Game tests must still verify station visibility, actual player collision, ladders and custom door passage.

## In-game fitting and verification

The runtime replaces the marked equipment with custom stations, full gray/white hull blocks with the project material kit, and the source spruce/mangrove desk material with metal equivalents while retaining footprint/half/facing geometry. The two engine manifolds face their original service alleys (west on the left engine, east on the right); their copper service covers are folded clear so the interfaces can actually be reached. This correction also applies when selecting an already imported ship. Native station checks require a visible, collision-free standing approach rather than repairing through a wall.

The importer leaves the original file and generated source data intact. It deliberately omits command/inventory/sign NBT and clears waterlogging. Existing ship attempts reset independently of the persisted structure. The final native client test reached every custom station, exercised the bag through physical attack callbacks, completed the unique rescue tasks and produced a successful return report. Real multi-client classroom navigation remains untested.
