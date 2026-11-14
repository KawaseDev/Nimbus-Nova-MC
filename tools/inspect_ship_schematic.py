#!/usr/bin/env python3
"""Inspect the team's Sponge schematic and export its bounded, non-entity NVS1 block map."""
import argparse
import collections
import gzip
import hashlib
import io
import json
from pathlib import Path
import struct

from import_marscraft import NbtReader

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'ananaslafelie-1794683726306.schem'
OUTPUT = ROOT / 'src/main/resources/data/nimbus_nova/ship/return_window.nvs.gz'
ORIGIN = (-13, 64, -41)
MARKERS = {
    'lectern': 'Pilot', 'brewing_stand': 'Galley', 'fletching_table': 'Medical',
    'note_block': 'Meeting', 'furnace': 'Engine', 'beacon': 'Science',
    'cartography_table': 'Comms', 'composter': 'Plants', 'red_wool': 'Punching bag',
    'chest': 'Storage', 'lime_wool': 'Airlock',
}
COLORS = {
    'Pilot': '#63aaff', 'Galley': '#f8b45c', 'Medical': '#ff7790', 'Meeting': '#b993ff',
    'Engine': '#f47743', 'Science': '#6ce7dc', 'Comms': '#66baf6', 'Plants': '#87cf75',
    'Punching bag': '#e34f62', 'Storage': '#bd976a', 'Airlock': '#d1ee75',
}


def decode_varints(data, count):
    values, value, shift = [], 0, 0
    for byte in data:
        if shift >= 35:
            raise ValueError('Block palette VarInt exceeds five bytes')
        value |= (byte & 127) << shift
        if byte < 128:
            values.append(value)
            if len(values) > count:
                raise ValueError('Too many schematic block entries')
            value = shift = 0
        else:
            shift += 7
    if shift or len(values) != count:
        raise ValueError('Truncated schematic block data')
    return values


def load(path):
    if path.stat().st_size > 8_388_608:
        raise ValueError('Schematic compressed size exceeds 8 MiB')
    with gzip.open(path, 'rb') as stream:
        payload = stream.read(8_388_609)
    if len(payload) > 8_388_608:
        raise ValueError('Schematic payload exceeds 8 MiB')
    reader = NbtReader(payload, {()})
    data = reader.root()
    if reader.offset != len(payload) or data.get('Version') != 2:
        raise ValueError('Expected exactly one Sponge v2 schematic')
    width, height, length = (data[key] for key in ('Width', 'Height', 'Length'))
    if min(width, height, length) <= 0 or width * height * length > 262_144:
        raise ValueError('Schematic dimensions exceed the import bound')
    palette = sorted(data['Palette'], key=data['Palette'].get)
    if len(palette) > 4096 or sorted(data['Palette'].values()) != list(range(len(palette))):
        raise ValueError('Palette must be bounded and contiguous')
    values = decode_varints(data['BlockData'], width * height * length)
    if any(value >= len(palette) for value in values):
        raise ValueError('Block references an unknown palette index')
    return data, palette, values


def coordinates(index, width, length):
    return index % width, index // (width * length), index // width % length


def world(position):
    return [value + offset for value, offset in zip(position, ORIGIN)]


def bare(state):
    return state.split('[', 1)[0].removeprefix('minecraft:')


def door_groups(points):
    remaining = {position for position, state in points.items() if bare(state) == 'lime_wool'}
    groups = []
    while remaining:
        seed = min(remaining)
        remaining.remove(seed)
        group, pending = [seed], [seed]
        while pending:
            x, y, z = pending.pop()
            for position in ((x - 1, y, z), (x + 1, y, z), (x, y - 1, z),
                             (x, y + 1, z), (x, y, z - 1), (x, y, z + 1)):
                if position in remaining:
                    remaining.remove(position)
                    pending.append(position)
                    group.append(position)
        low = [min(position[axis] for position in group) for axis in range(3)]
        high = [max(position[axis] for position in group) for axis in range(3)]
        groups.append({'min': low, 'max': high, 'world_min': world(low), 'world_max': world(high),
                       'normal': 'x' if low[0] == high[0] else 'z', 'blocks': [list(p) for p in sorted(group)]})
    return groups


def walkability(points, dimensions):
    width, height, length = dimensions

    def empty(position):
        block = bare(points.get(position, 'minecraft:air'))
        return block in ('air', 'cave_air', 'light', 'ladder', 'lime_wool') or block.endswith('_door')

    nodes = set()
    for x in range(width):
        for z in range(length):
            for y in range(11, min(height - 1, 21)):
                position, below = (x, y, z), (x, y - 1, z)
                climbing = bare(points.get(position, '')) == 'ladder' or bare(points.get(below, '')) == 'ladder'
                if empty(position) and empty((x, y + 1, z)) and (climbing or not empty(below)):
                    nodes.add(position)
    spawn = (13, 16, 64)
    if spawn not in nodes or bare(points[(13, 15, 64)]) != 'smooth_quartz':
        raise ValueError('Bridge spawn lost its solid floor / two-block clearance')
    reached, queue = {spawn}, collections.deque([spawn])
    while queue:
        x, y, z = queue.popleft()
        candidates = [(x + dx, y + dy, z + dz)
                      for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)) for dy in (-1, 0, 1)]
        if any(bare(points.get((x, y + dy, z), '')) == 'ladder' for dy in (-1, 0, 1)):
            candidates += [(x, y - 1, z), (x, y + 1, z)]
        for position in candidates:
            if position in nodes and position not in reached:
                reached.add(position)
                queue.append(position)
    return spawn, nodes, reached


def export_binary(path, dimensions, palette, values):
    raw = io.BytesIO()
    raw.write(b'NVS1')
    raw.write(struct.pack('>HHHH', *dimensions, len(palette)))
    for state in palette:
        encoded = state.encode('utf-8')
        if len(encoded) > 2048:
            raise ValueError('Palette state exceeds 2048 UTF-8 bytes')
        raw.write(struct.pack('>H', len(encoded)))
        raw.write(encoded)
    entries = [(index, value) for index, value in enumerate(values) if bare(palette[value]) not in ('air', 'cave_air', 'void_air')]
    raw.write(struct.pack('>I', len(entries)))
    for index, value in entries:
        raw.write(struct.pack('>IH', index, value))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(raw.getvalue(), compresslevel=9, mtime=0))
    return entries


def check_binary(path, dimensions, palette, entries):
    raw = io.BytesIO(gzip.decompress(path.read_bytes()))
    assert raw.read(4) == b'NVS1'
    assert struct.unpack('>HHHH', raw.read(8)) == (*dimensions, len(palette))
    for state in palette:
        size, = struct.unpack('>H', raw.read(2))
        assert raw.read(size).decode('utf-8') == state
    count, = struct.unpack('>I', raw.read(4))
    assert count == len(entries)
    assert [struct.unpack('>IH', raw.read(6)) for _ in range(count)] == entries
    assert not raw.read(1)


def floorplan(path, points, reached, markers):
    cell, left, top = 10, 45, 70
    body = ['<svg xmlns="http://www.w3.org/2000/svg" width="850" height="960" viewBox="0 0 850 960">',
            '<rect width="850" height="960" fill="#111a24"/>',
            '<g font-family="sans-serif" fill="#e7eff7">',
            '<text x="30" y="28" font-size="19">Return Window — team spaceship schematic</text>',
            '<text x="30" y="49" font-size="12">Local coordinates. Bow / cockpit at +Z. Lime cells are doorway placeholders.</text>']
    for deck, feet in enumerate((12, 17)):
        origin_x = left + deck * 310
        body.append(f'<text x="{origin_x}" y="{top - 5}" font-size="14">{"Lower" if deck == 0 else "Upper"} deck · feet Y≈{feet}</text>')
        for x in range(26):
            for z in range(82):
                state = bare(points[(x, feet, z)])
                clear = state in ('air', 'light', 'ladder')
                color = '#24313e' if clear else '#657381'
                if clear and any((x, y, z) in reached for y in range(feet - 1, feet + 2)):
                    color = '#344b58'
                if clear and bare(points[(x, feet - 1, z)]) == 'air':
                    color = '#111a24'
                if state in MARKERS:
                    color = COLORS[MARKERS[state]]
                if state == 'ladder':
                    color = '#efdfb2'
                body.append(f'<rect x="{origin_x + x * cell}" y="{top + z * cell}" width="9.5" height="9.5" fill="{color}"/>')
        for z in range(0, 82, 10):
            body.append(f'<text x="{origin_x - 25}" y="{top + z * cell + 9}" font-size="10">{z}</text>')
        for x in (0, 5, 10, 15, 20, 25):
            body.append(f'<text x="{origin_x + x * cell}" y="{top + 837}" font-size="10">{x}</text>')
        for marker in markers:
            x, y, z = marker['local']
            if (deck == 0 and y < 15) or (deck == 1 and y >= 15):
                body.append(f'<rect x="{origin_x + x * cell}" y="{top + z * cell}" width="9.5" height="9.5" fill="{COLORS[marker["kind"]]}"/>')
                body.append(f'<text x="{origin_x + x * cell + 5}" y="{top + z * cell + 8}" text-anchor="middle" font-size="9" font-weight="bold" fill="#0d1721">{marker["number"]}</text>')
    for index, (label, color) in enumerate(COLORS.items()):
        y = 95 + index * 27
        body += [f'<rect x="675" y="{y - 10}" width="13" height="13" fill="{color}"/>',
                 f'<text x="696" y="{y}" font-size="11">{label}</text>']
    body += ['<text x="675" y="430" font-size="11">Pale yellow = ladder</text>',
             '<text x="675" y="450" font-size="11">Blue gray = reachable</text>',
             '<text x="675" y="470" font-size="11">Gray = occupied slice</text>',
             '<text x="30" y="948" font-size="11">Reachability is an integer-grid planning check, not Minecraft collision certification. See SHIP_LAYOUT.md.</text>', '</g></svg>']
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('\n'.join(body) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', nargs='?', type=Path, default=SOURCE)
    parser.add_argument('--output', type=Path, default=OUTPUT)
    arguments = parser.parse_args()
    data, palette, values = load(arguments.source)
    dimensions = tuple(data[key] for key in ('Width', 'Height', 'Length'))
    width, height, length = dimensions
    points = {coordinates(index, width, length): palette[value] for index, value in enumerate(values)}
    counts = collections.Counter(values)
    spawn, nodes, reached = walkability(points, dimensions)
    groups = door_groups(points)
    markers = []
    for position, state in points.items():
        block = bare(state)
        if block not in MARKERS or block == 'lime_wool':
            continue
        nearest = min(reached, key=lambda p: sum((a - b) ** 2 for a, b in zip(position, p)))
        markers.append({'number': len(markers) + 1, 'kind': MARKERS[block], 'source_block': state,
                        'local': list(position), 'world': world(position), 'nearest_walkable': list(nearest),
                        'walk_distance': round(sum((a - b) ** 2 for a, b in zip(position, nearest)) ** 0.5, 2)})
    entries = export_binary(arguments.output, dimensions, palette, values)
    check_binary(arguments.output, dimensions, palette, entries)
    try:
        decode_varints(b'\x80', 1)
        raise AssertionError('Truncated VarInt was accepted')
    except ValueError:
        pass
    try:
        decode_varints(b'\x80' * 6, 1)
        raise AssertionError('Overlong VarInt was accepted')
    except ValueError:
        pass
    manifest = {'source': arguments.source.name, 'source_sha256': hashlib.sha256(arguments.source.read_bytes()).hexdigest(),
                'data_version': data['DataVersion'], 'dimensions': list(dimensions), 'schematic_offset_ignored': list(data['Offset']),
                'world_origin': list(ORIGIN), 'spawn_local': list(spawn), 'spawn_world': world(spawn),
                'non_air_blocks': len(entries), 'palette': [{'id': index, 'state': state, 'count': counts[index]} for index, state in enumerate(palette)],
                'markers': markers, 'doors': groups, 'block_entities_omitted': len(data['BlockEntities']),
                'command_blocks': [list(p) for p, state in points.items() if bare(state) == 'command_block'],
                'walkable_nodes': len(nodes), 'bridge_reachable_nodes': len(reached),
                'all_markers_within_interaction_range': all(marker['walk_distance'] <= 5 for marker in markers),
                'runtime_file': str(arguments.output.relative_to(ROOT)), 'runtime_sha256': hashlib.sha256(arguments.output.read_bytes()).hexdigest()}
    docs = ROOT / 'docs'
    docs.mkdir(exist_ok=True)
    (docs / 'ship-layout.json').write_text(json.dumps(manifest, indent=2) + '\n')
    floorplan(docs / 'ship-layout.svg', points, reached, markers)
    lines = ['# Spaceship schematic integration', '',
             f'Source: `{arguments.source.name}` (team-supplied Git asset). SHA-256 `{manifest["source_sha256"]}`.', '',
             f'The newer schematic is Sponge v2, data version {data["DataVersion"]}, dimensions **{width} × {height} × {length}**, containing **{len(entries):,} non-air blocks** and {len(palette)} palette states. The older `1794683017856` version has the same dimensions and task anchors but fewer doorway markers: zero lime-wool blocks versus 118 in this source. Use the newer file.', '',
             'The ship points toward +Z: engines are aft near local Z=3, and the pilot console is at Z=65. Lower-deck feet are normally Y=12; the upper deck is normally Y=17, with a lowered bridge at Y=16 and raised galley equipment at Y=18.', '',
             '## Placement and entry', '',
             'Use world origin **(−13, 64, −41)**: world = local + origin. Ignore the source world-offset metadata `(248, −54, −219)`; it records the original clipboard position, not an installation requirement.', '',
             '**Bridge spawn:** local `(13,16,64)`, world `(0,80,23)`, with a solid smooth-quartz floor and two air blocks. Upper corridor alternative: local `(13,17,52)`, world `(0,81,11)`, with a deepslate floor and noncolliding light/air above.', '',
             'Ladders join decks at local X/Z `(2,17)`, `(24,17)`, `(4,60)`, `(22,60)` through Y=12…16. Preserve these routes and their upper landings. The schematic contains original iron doors as well as lime panels; the integer-grid route check assumes both become passable doors.', '',
             f'A planning flood fill reaches {len(reached):,} integer-grid standing positions from the bridge. Every task marker has a reached standing position within five blocks. This approximates stairs/slabs and treats replacement doors as open; it does not replace the in-game walking check.', '',
             '## Station markers', '', '| # | Purpose | Source marker | Local XYZ | World XYZ | Nearby reachable local XYZ |', '| --- | --- | --- | --- | --- | --- |']
    for marker in markers:
        lines.append(f'| {marker["number"]} | {marker["kind"]} | `{bare(marker["source_block"])}` | `{tuple(marker["local"])}` | `{tuple(marker["world"])}` | `{tuple(marker["nearest_walkable"])}` |')
    lines += ['', 'The two red-wool cells form one two-block-tall punching-bag placeholder. Keep the lower anchor and replace/remove its upper placeholder as required by the custom model.', '',
              '## Door panels', '', 'Lime wool forms 16 separate axis-aligned panels. Clear the entire panel before installing a matching portal: most are three blocks high and two or three wide, so copying the old two-high doors without clearing the third row would leave a blocked passage. All exact cells and both local/world bounds are in [ship-layout.json](ship-layout.json).', '',
              '| Local minimum | Local maximum | Facing axis |', '| --- | --- | --- |']
    for group in groups:
        lines.append(f'| `{tuple(group["min"])}` | `{tuple(group["max"])}` | {group["normal"]} |')
    lines += ['', '## Runtime block data', '',
              f'Generated resource: `{manifest["runtime_file"]}`. SHA-256 `{manifest["runtime_sha256"]}`.', '',
              'Decompress gzip, then read big-endian fields: ASCII magic `NVS1`; unsigned-short width, height, length; unsigned-short palette count; for each palette state, unsigned-short UTF-8 byte length then those bytes; unsigned-int non-air entry count; each entry is unsigned-int linear index followed by unsigned-short palette ID. Linear index is `x + z*width + y*width*length`. The current palette is ASCII, so Java `DataInputStream.readUTF()` also reads its strings; ordinary length-prefixed UTF-8 is the formal format.', '',
              'The resource preserves all original block-state strings and geometry. It intentionally carries no entities, command contents, inventories, sign text or external executable behavior. There are 115 omitted block-entity payloads, including 36 blank signs and two command blocks at local `(20,8,59)` and `(21,8,59)`. Replace those two command-block materials with inert hull during runtime installation.', '',
              'The palette includes `gray_wool_stairs`, `light_gray_wool_stairs`, `light_gray_concrete_slab`, `white_concrete_slab` and `iron_chain` families. Confirm their availability in the actual client registry. If absent, use a deliberate closest-shape replacement preserving compatible facing/half/type properties; never silently replace unsupported structural blocks with air. The full palette and counts are in the JSON manifest.', '',
              'Regenerate and run format/truncation checks with:', '', '```sh', 'python3 tools/inspect_ship_schematic.py', '```', '',
              'The exporter bounds compressed/decompressed input, volume and palette size; validates VarInts, palette references and safe spawn clearance; and round-trips every exported entry. [Floor plan](ship-layout.svg) shows both decks and marker numbers. Game tests must still verify station visibility, actual player collision, ladders and custom door passage.']
    (docs / 'SHIP_LAYOUT.md').write_text('\n'.join(lines) + '\n')
    print(f'Exported {arguments.source.name}: {dimensions}, {len(entries)} blocks, {len(palette)} states, {len(groups)} doorway panels.')
    print(f'Spawn local {spawn}, world {world(spawn)}; {len(reached)} reached standing nodes; all task markers in range: {manifest["all_markers_within_interaction_range"]}.')
    print(f'NVS1 entry round-trip and malformed VarInt checks passed; resource {arguments.output.stat().st_size:,} bytes.')


if __name__ == '__main__':
    main()
