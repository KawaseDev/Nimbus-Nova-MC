"""Generates the original prop meshes and 32px textures for the outdoor scenery blocks."""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/nimbus_nova"


def box(start, end, texture, rotation=None, faces="north south east west up down", overrides=None):
    element = {"from": start, "to": end,
               "faces": {face: {"texture": "#" + (overrides or {}).get(face, texture)} for face in faces.split()}}
    if rotation:
        element["rotation"] = rotation
    return element


def write(name, textures, elements):
    textures = {key: "nimbus_nova:block/" + value for key, value in textures.items()}
    textures["particle"] = next(iter(textures.values()))
    display = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}}
    (ASSETS / f"models/block/scifi_{name}.json").write_text(json.dumps({"textures": textures, "elements": elements, "display": display}, indent=2) + "\n")
    (ASSETS / f"models/item/scifi_{name}.json").write_text(json.dumps({"parent": f"nimbus_nova:block/scifi_{name}"}, indent=2) + "\n")
    (ASSETS / f"items/scifi_{name}.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"nimbus_nova:item/scifi_{name}"}}, indent=2) + "\n")
    (ASSETS / f"blockstates/scifi_{name}.json").write_text(json.dumps({"variants": {"": {"model": f"nimbus_nova:block/scifi_{name}"}}}, indent=2) + "\n")


def metal(base, seed):
    random.seed(seed)
    image = Image.new("RGBA", (32, 32), base + (255,))
    draw = ImageDraw.Draw(image)
    for _ in range(90):
        x, y, shift = random.randrange(32), random.randrange(32), random.randrange(-10, 11)
        draw.point((x, y), tuple(max(0, min(255, channel + shift)) for channel in base) + (255,))
    for line in (0, 15, 16, 31):
        draw.line((0, line, 31, line), tuple(max(0, channel - 28) for channel in base) + (255,))
        draw.line((line, 0, line, 31), tuple(max(0, channel - 28) for channel in base) + (255,))
    return image, draw


def textures():
    out = ASSETS / "textures/block"
    image, draw = metal((86, 96, 112), 1)
    for rivet in (3, 12, 19, 28):
        draw.point((rivet, 3), (170, 182, 200, 255))
        draw.point((rivet, 28), (40, 46, 58, 255))
    image.save(out / "scifi_prop_metal.png")

    image = Image.new("RGBA", (32, 32), (16, 22, 40, 255))
    draw = ImageDraw.Draw(image)
    for column in range(4):
        for row in range(4):
            left, top = column * 8 + 1, row * 8 + 1
            draw.rectangle((left, top, left + 5, top + 5), (28, 58, 128, 255))
            draw.line((left, top, left + 5, top), (86, 132, 214, 255))
            draw.point((left + 4, top + 1), (190, 220, 255, 255))
    image.save(out / "scifi_solar_cells.png")

    image, draw = metal((214, 220, 228), 2)
    for ring in (4, 9, 14):
        draw.ellipse((ring, ring, 31 - ring, 31 - ring), outline=(150, 160, 176, 255))
    draw.rectangle((14, 14, 17, 17), (95, 225, 240, 255))
    image.save(out / "scifi_dish.png")

    image, draw = metal((176, 110, 44), 3)
    draw.rectangle((0, 12, 31, 19), (40, 46, 58, 255))
    for stripe in range(-8, 32, 8):
        draw.polygon(((stripe, 19), (stripe + 4, 19), (stripe + 11, 12), (stripe + 7, 12)), (255, 200, 87, 255))
    draw.rectangle((12, 3, 19, 8), (232, 238, 248, 255))
    draw.line((13, 5, 18, 5), (86, 96, 112, 255))
    image.save(out / "scifi_supply_crate.png")

    image = Image.new("RGBA", (32, 64), (0, 0, 0, 255))
    draw = ImageDraw.Draw(image)
    for frame, colors in enumerate((((255, 92, 80), (255, 210, 190)), ((92, 26, 26), (140, 50, 46)))):
        top = frame * 32
        draw.rectangle((0, top, 31, top + 31), colors[0] + (255,))
        draw.rectangle((8, top + 8, 23, top + 23), colors[1] + (255,))
        draw.rectangle((0, top, 31, top + 2), (40, 46, 58, 255))
        draw.rectangle((0, top + 29, 31, top + 31), (40, 46, 58, 255))
    image.save(out / "scifi_beacon_lamp.png")
    (out / "scifi_beacon_lamp.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 14, "interpolate": True}}, indent=2) + "\n")


def models():
    tilt = {"angle": 22.5, "axis": "x", "origin": [8, 10, 8]}
    write("solar_panel", {"metal": "scifi_prop_metal", "cells": "scifi_solar_cells"}, [
        box([6, 0, 6], [10, 1, 10], "metal"),
        box([7, 1, 7], [9, 10, 9], "metal"),
        box([0, 9.5, 0], [16, 10.5, 16], "metal", tilt, overrides={"up": "cells"}),
        box([0, 10.5, 0], [16, 11, 1], "metal", tilt),
        box([0, 10.5, 15], [16, 11, 16], "metal", tilt),
    ])
    lean = {"angle": -22.5, "axis": "x", "origin": [8, 10, 8]}
    write("satellite_dish", {"metal": "scifi_prop_metal", "dish": "scifi_dish"}, [
        box([5, 0, 5], [11, 2, 11], "metal"),
        box([7, 2, 7], [9, 9, 9], "metal"),
        box([2, 5, 8], [14, 15, 9], "metal", lean, overrides={"north": "dish"}),
        box([1, 4, 7], [15, 5, 9], "metal", lean),
        box([1, 15, 7], [15, 16, 9], "metal", lean),
        box([1, 5, 7], [2, 15, 9], "metal", lean),
        box([14, 5, 7], [15, 15, 9], "metal", lean),
        box([7.5, 9.5, 3], [8.5, 10.5, 8], "metal", lean),
        box([7, 9, 2], [9, 11, 3], "dish", lean),
    ])
    write("beacon_mast", {"metal": "scifi_prop_metal", "lamp": "scifi_beacon_lamp"}, [
        box([5, 0, 5], [11, 1, 11], "metal"),
        box([7, 1, 7], [9, 11, 9], "metal"),
        box([6, 11, 6], [10, 12, 10], "metal"),
        box([6.5, 12, 6.5], [9.5, 15, 9.5], "lamp"),
        box([6, 15, 6], [10, 16, 10], "metal"),
    ])
    write("supply_crate", {"crate": "scifi_supply_crate", "metal": "scifi_prop_metal"}, [
        box([1, 0, 1], [15, 12, 15], "crate"),
        box([0, 12, 0], [16, 14, 16], "metal"),
        box([0, 0, 0], [2, 12, 2], "metal"),
        box([14, 0, 0], [16, 12, 2], "metal"),
        box([0, 0, 14], [2, 12, 16], "metal"),
        box([14, 0, 14], [16, 12, 16], "metal"),
        box([6, 14, 6], [10, 15, 10], "metal"),
    ])


textures()
models()
