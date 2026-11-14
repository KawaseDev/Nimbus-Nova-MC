#!/usr/bin/env python3
"""Export one 512x512 MarsCraft region's existing surface heights, without extracting the world."""
import argparse
import gzip
import json
import math
from pathlib import Path
import statistics
import struct
from zipfile import ZipFile
import zlib


class NbtReader:
    def __init__(self, data, wanted):
        self.data = memoryview(data)
        self.offset = 0
        self.wanted = wanted

    def take(self, count):
        if count < 0 or self.offset + count > len(self.data):
            raise ValueError("Truncated or invalid NBT payload")
        result = self.data[self.offset:self.offset + count]
        self.offset += count
        return result

    def number(self, code):
        return struct.unpack('>' + code, self.take(struct.calcsize(code)))[0]

    def text(self):
        return bytes(self.take(self.number('H'))).decode('utf-8')

    def length(self):
        count = self.number('i')
        if not 0 <= count <= 16_777_216:
            raise ValueError("NBT collection exceeds the import bound")
        return count

    def payload(self, kind, path=(), keep=True, depth=0):
        if depth > 64:
            raise ValueError("NBT nesting exceeds the import bound")
        if kind in range(1, 7):
            value = self.number({1: 'b', 2: 'h', 3: 'i', 4: 'q', 5: 'f', 6: 'd'}[kind])
            return value if keep else None
        if kind == 7:
            value = self.take(self.length())
            return bytes(value) if keep else None
        if kind == 8:
            value = self.take(self.number('H'))
            return bytes(value).decode('utf-8') if keep else None
        if kind == 9:
            child_kind, count = self.number('B'), self.length()
            values = [] if keep else None
            for _ in range(count):
                value = self.payload(child_kind, path, keep, depth + 1)
                if keep:
                    values.append(value)
            return values
        if kind == 10:
            values = {} if keep else None
            while True:
                child_kind = self.number('B')
                if child_kind == 0:
                    return values
                name = self.text()
                child_path = path + (name,)
                selected = keep and any(p[:len(child_path)] == child_path or child_path[:len(p)] == p
                                        for p in self.wanted)
                value = self.payload(child_kind, child_path, selected, depth + 1)
                if selected:
                    values[name] = value
        if kind in (11, 12):
            count = self.length()
            value = self.take(count * (4 if kind == 11 else 8))
            return struct.unpack('>' + ('i' if kind == 11 else 'Q') * count, value) if keep else None
        raise ValueError(f"Unsupported NBT tag {kind}")

    def root(self):
        if self.number('B') != 10:
            raise ValueError("Expected an NBT root compound")
        self.text()
        return self.payload(10)


def surface_heights(words, bits=9):
    """Heightmap entries store the first air block; convert to the surface block Y."""
    mask, per_word = (1 << bits) - 1, 64 // bits
    compact = len(words) == math.ceil(256 * bits / 64)
    if not compact and len(words) != math.ceil(256 / per_word):
        raise ValueError(f"Unexpected heightmap length {len(words)}")
    values = []
    for index in range(256):
        if compact:
            word, shift = divmod(index * bits, 64)
            value = words[word] >> shift
            if shift + bits > 64:
                value |= words[word + 1] << (64 - shift)
        else:
            word, slot = divmod(index, per_word)
            value = words[word] >> (slot * bits)
        values.append((value & mask) - 1)
    return values


def section_surface(sections, version):
    heights = [-1] * 256
    remaining = set(range(256))
    for section in sorted(sections, key=lambda s: s['Y'], reverse=True):
        palette = section.get('Palette', [])
        solid = [entry['Name'] not in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air') for entry in palette]
        if not any(solid):
            continue
        words = section.get('BlockStates', ())
        bits = max(4, (len(palette) - 1).bit_length())
        padded = version >= 2529
        expected = math.ceil(4096 / (64 // bits)) if padded else math.ceil(4096 * bits / 64)
        if len(palette) != 1 and len(words) != expected:
            raise ValueError(f"Unexpected block-state packing: {len(words)} words, expected {expected}")
        for column in tuple(remaining):
            for y in range(15, -1, -1):
                index = y * 256 + column
                if len(palette) == 1:
                    state = 0
                elif padded:
                    word, slot = divmod(index, 64 // bits)
                    state = (words[word] >> (slot * bits)) & ((1 << bits) - 1)
                else:
                    word, shift = divmod(index * bits, 64)
                    state = words[word] >> shift
                    if shift + bits > 64:
                        state |= words[word + 1] << (64 - shift)
                    state &= (1 << bits) - 1
                if state >= len(solid):
                    raise ValueError("Block state references a missing palette entry")
                if solid[state]:
                    heights[column] = section['Y'] * 16 + y
                    remaining.remove(column)
                    break
        if not remaining:
            return heights
    if remaining:
        raise ValueError("Source contains empty terrain columns; refusing to invent their heights")
    return heights


def inflate(data, window=15):
    decoder = zlib.decompressobj(window)
    result = decoder.decompress(data, 16 * 1024 * 1024)
    if not decoder.eof:
        raise ValueError("Compressed NBT exceeds its bound or is incomplete")
    return result


def chunk_surface(region, index):
    location = struct.unpack_from('>I', region, index * 4)[0]
    sector, sector_count = location >> 8, location & 255
    if sector < 2 or sector_count == 0:
        raise ValueError(f"Missing terrain chunk at region index {index}")
    start = sector * 4096
    if start + sector_count * 4096 > len(region):
        raise ValueError("Region chunk points outside its file")
    length = struct.unpack_from('>I', region, start)[0]
    if length < 2 or length + 4 > sector_count * 4096:
        raise ValueError("Invalid region chunk length")
    compression = region[start + 4]
    packed = region[start + 5:start + 4 + length]
    if compression in (1, 2):
        raw = inflate(packed, 31 if compression == 1 else 15)
    elif compression == 3:
        raw = packed
    else:
        raise ValueError(f"Unsupported region compression {compression}; no external chunks imported")
    if len(raw) > 16 * 1024 * 1024:
        raise ValueError("Chunk exceeds decompression bound")
    wanted = [('DataVersion',), ('Level', 'Heightmaps'), ('Level', 'HeightMap'),
              ('Level', 'xPos'), ('Level', 'zPos'), ('Heightmaps',), ('xPos',), ('zPos',),
              ('Level', 'Sections', 'Y'), ('Level', 'Sections', 'Palette'), ('Level', 'Sections', 'BlockStates')]
    root = NbtReader(raw, wanted).root()
    level = root.get('Level', root)
    version = root.get('DataVersion', 0)
    # shortcut: pre-1.18 source Y coordinates; extend minimum-Y handling before accepting newer maps.
    if not 0 < version < 2825:
        raise ValueError(f"Expected this source's pre-1.18 height coordinates, found DataVersion {version}")
    heightmaps = level.get('Heightmaps', {})
    if 'WORLD_SURFACE' in heightmaps:
        heights = surface_heights(heightmaps['WORLD_SURFACE'])
    elif 'WORLD_SURFACE_WG' in heightmaps:
        heights = surface_heights(heightmaps['WORLD_SURFACE_WG'])
    elif 'HeightMap' in level and len(level['HeightMap']) == 256:
        heights = [height - 1 for height in level['HeightMap']]
    elif 'Sections' in level:
        heights = section_surface(level['Sections'], version)
    else:
        raise ValueError("Source chunk has neither stored heightmaps nor paletted terrain sections")
    if not all(-1 <= height <= 255 for height in heights):
        raise ValueError("Surface heights exceed the source world's expected range")
    return heights, version, (level['xPos'], level['zPos'])


def self_test():
    expected = [((i * 37) % 255) + 1 for i in range(256)]
    compact = [0] * 36
    padded = [0] * 37
    for i, value in enumerate(expected):
        word, shift = divmod(i * 9, 64)
        compact[word] |= value << shift
        if shift + 9 > 64:
            compact[word + 1] |= value >> (64 - shift)
        padded[i // 7] |= value << ((i % 7) * 9)
    compact = [value & ((1 << 64) - 1) for value in compact]
    assert surface_heights(compact) == [value - 1 for value in expected]
    assert surface_heights(padded) == [value - 1 for value in expected]
    assert section_surface([{'Y': 3, 'Palette': [{'Name': 'minecraft:red_sand'}]}], 1976) == [63] * 256
    assert section_surface([{'Y': 4, 'Palette': [{'Name': 'minecraft:air'}]}, {'Y': 3, 'Palette': [{'Name': 'minecraft:red_sand'}]}], 1976) == [63] * 256
    palette = [{'Name': 'minecraft:air'}] + [{'Name': 'minecraft:red_sand'}] * 16
    words = [0] * 320
    index = 15 * 256 + 12
    word, shift = divmod(index * 5, 64)
    words[word] |= 16 << shift
    words[word + 1] |= 16 >> (64 - shift)
    words = [value & ((1 << 64) - 1) for value in words]
    sections = [{'Y': 1, 'Palette': palette, 'BlockStates': words},
                {'Y': 0, 'Palette': [{'Name': 'minecraft:red_sand'}]}]
    top = section_surface(sections, 1976)
    assert top[12] == 31 and top[11] == top[13] == 15
    padded = [0] * math.ceil(4096 / 12)
    padded[index // 12] = 16 << ((index % 12) * 5)
    sections[0]['BlockStates'] = padded
    assert section_surface(sections, 2537) == top
    assert inflate(zlib.compress(b'NBT self-check')) == b'NBT self-check'
    data = b'\x0a\x00\x00\x03\x00\x01x' + struct.pack('>i', 42) + b'\x00'
    assert NbtReader(data, [('x',)]).root() == {'x': 42}
    try:
        NbtReader(data[:-2], [('x',)]).root()
    except ValueError:
        pass
    else:
        raise AssertionError("Truncated NBT was accepted")


def export(archive, region_x, region_z, output):
    with ZipFile(archive) as source:
        roots = [name[:-len('level.dat')] for name in source.namelist() if name.endswith('/level.dat')]
        if len(roots) != 1:
            raise ValueError("Expected one source world root")
        root = roots[0]
        metadata = NbtReader(inflate(source.read(root + 'level.dat'), 31),
                             [('Data', 'DataVersion'), ('Data', 'Version'), ('Data', 'LevelName')]).root()['Data']
        entry = source.getinfo(f'{root}region/r.{region_x}.{region_z}.mca')
        if entry.file_size > 128 * 1024 * 1024:
            raise ValueError("Selected region exceeds the import bound")
        region = source.read(entry)
        heights = [0] * (512 * 512)
        versions = set()
        for index in range(1024):
            chunk, version, position = chunk_surface(region, index)
            chunk_x, chunk_z = index % 32, index // 32
            if position != (region_x * 32 + chunk_x, region_z * 32 + chunk_z):
                raise ValueError(f"Unexpected source chunk coordinates: {position}")
            versions.add(version)
            for z in range(16):
                start = (chunk_z * 16 + z) * 512 + chunk_x * 16
                heights[start:start + 16] = chunk[z * 16:z * 16 + 16]
        output.mkdir(parents=True, exist_ok=True)
        height_file = output / 'marscraft-heights.nms.gz'
        raw = b'NMS1' + struct.pack('>HH', 512, 512) + struct.pack('>' + 'h' * len(heights), *heights)
        height_file.write_bytes(gzip.compress(raw, mtime=0))
        center = heights[256 * 512 + 256]
        provenance = {
            'source': 'MarsCraft Updated map by proteinguy',
            'creator_url': 'https://www.planetminecraft.com/project/mars-4521195/',
            'source_archive': archive.name, 'source_world': metadata,
            'source_entry': entry.filename, 'source_entry_crc32': f'{entry.CRC:08x}',
            'source_entry_uncompressed_bytes': entry.file_size,
            'source_entry_compressed_bytes': entry.compress_size,
            'source_region': [region_x, region_z],
            'source_block_origin': [region_x * 512, region_z * 512],
            'width': 512, 'height': 512, 'source_chunk_data_versions': sorted(versions),
            'encoding': 'gzip: NMS1 + big-endian uint16 width,height + signed int16 surface Y, rows z then columns x',
            'conversion': 'Exact highest existing non-air block Y from source paletted terrain sections (or stored WORLD_SURFACE when present); no smoothing or resampling',
            'source_scale': 'Creator describes the map as 1:600, derived from USGS MOLA DTM; Minecraft heights are not raw metre elevations',
            'location_note': 'Selected region contains creator-captioned Valles Marineris map coordinate 9000,9250; no geographic latitude/longitude derived',
            'range': [min(heights), max(heights)], 'mean': statistics.mean(heights),
            'standard_deviation': statistics.pstdev(heights), 'center_surface_y': center,
            'center_alignment_offset': 64 - center,
            'suggested_gameplay_offset': max(64 - center, -62 - min(heights)),
            'offset_note': 'The suggested offset keeps all source relief at Y=-62 or above, clear of destination Y=-64 bedrock; flattening/blending the camp is a separate authored edit, never a scan claim',
            'suggested_gameplay_mapping': 'local x,z -256..255 map to grid0..511; keep raw relief outside any explicitly authored outpost pad',
            'output_bytes': height_file.stat().st_size,
            'redistribution': 'LOCAL PREVIEW ONLY. Creator requests permission for server use. Do not bundle or push derived heights until rights are confirmed.'
        }
        (output / 'provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
        print(json.dumps(provenance, indent=2))
        print('HEIGHT_FILE', height_file.resolve())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path)
    parser.add_argument('--self-test', action='store_true', help='Run parser/packing checks without importing')
    parser.add_argument('--region', nargs=2, type=int, default=(17, 18), metavar=('X', 'Z'))
    parser.add_argument('--output', type=Path, default=Path('.local/marscraft'))
    args = parser.parse_args()
    self_test()
    if args.archive and not args.self_test:
        export(args.archive, *args.region, args.output)
    else:
        print('Importer self-check passed: compact/padded packing, section surfaces, bounded NBT and truncation rejection.')
