# -*- coding: utf-8 -*-
"""Compose map2 (left) vs top-down render (right) comparison image."""
import os
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
BASE = os.path.join(ROOT, "docs", "assets", "maps", "地图2.png")
TOP = os.path.join(ROOT, "docs", "03-modeling", "renders", "02_overview_top.png")
OUT = os.path.join(ROOT, "docs", "03-modeling", "renders", "00_map_vs_top.png")

base = Image.open(BASE).convert("RGB")
top = Image.open(TOP).convert("RGB")
print("base", base.size, "top", top.size)
H = 1320


def fit(image):
    width = round(image.width * H / image.height)
    return image.resize((width, H), Image.LANCZOS)


b, t = fit(base), fit(top)
gap = 16
canvas = Image.new("RGB", (b.width + gap + t.width, H), (245, 245, 245))
canvas.paste(b, (0, 0))
canvas.paste(t, (b.width + gap, 0))
canvas.save(OUT, optimize=True)
print("saved", OUT, canvas.size)
