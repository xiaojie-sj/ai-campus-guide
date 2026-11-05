# -*- coding: utf-8 -*-
"""
High-detail campus model generator for 地图2.png.

Run with:
    blender.exe --background --python tools/campus_map2/build.py

The script uses Blender's native bpy backend (the backend driven by the
CLI-Anything Blender harness) and produces:

    frontend/public/models/campus-buildings.glb
    docs/models/campus-buildings.blend
    docs/03-modeling/renders/*.png
"""

from __future__ import annotations

import bpy
import bmesh
import json
import math
import os
import random
import sys
import time
from mathutils import Euler, Matrix, Vector

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

import layout as L  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GLB_PATH = os.path.join(ROOT, "frontend", "public", "models", "campus-buildings.glb")
BLEND_PATH = os.path.join(ROOT, "docs", "models", "campus-buildings.blend")
RENDER_DIR = os.path.join(ROOT, "docs", "03-modeling", "renders")
FONT_CANDIDATES = [
    r"C:\Windows\Fonts\simhei.ttf",
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\msyhbd.ttc",
    r"C:\Windows\Fonts\simsun.ttc",
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
    "/System/Library/Fonts/PingFang.ttc",
]
FONT_PATH = next((path for path in FONT_CANDIDATES if os.path.exists(path)), "")

START = time.time()


def _srgb_to_linear(value: float) -> float:
    if value <= 0.04045:
        return value / 12.92
    return ((value + 0.055) / 1.055) ** 2.4


def hx(value: str):
    value = value.lstrip("#")
    return tuple(_srgb_to_linear(int(value[i:i + 2], 16) / 255.0) for i in (0, 2, 4))


MATERIALS = {}


def material(name, color, roughness=0.78, metallic=0.0, emission=None,
             emission_strength=0.0, alpha=1.0):
    if name in MATERIALS:
        return MATERIALS[name]
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    mat.use_backface_culling = False
    nodes = mat.node_tree.nodes
    bsdf = nodes.get("Principled BSDF")
    if bsdf is None:
        bsdf = nodes.new("ShaderNodeBsdfPrincipled")
        out = nodes.get("Material Output") or nodes.new("ShaderNodeOutputMaterial")
        mat.node_tree.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    rgb = hx(color)
    bsdf.inputs["Base Color"].default_value = (*rgb, 1.0)
    bsdf.inputs["Roughness"].default_value = roughness
    bsdf.inputs["Metallic"].default_value = metallic
    if "Specular IOR Level" in bsdf.inputs:
        bsdf.inputs["Specular IOR Level"].default_value = 0.35
    if alpha < 1.0:
        bsdf.inputs["Alpha"].default_value = alpha
        mat.blend_method = "BLEND"
    if emission is not None:
        if "Emission Color" in bsdf.inputs:
            bsdf.inputs["Emission Color"].default_value = (*hx(emission), 1.0)
            bsdf.inputs["Emission Strength"].default_value = emission_strength
    mat.diffuse_color = (*rgb, alpha)
    MATERIALS[name] = mat
    return mat


def build_materials():
    material("M_Ground", "#D4E7F5", 0.95)
    material("M_GroundEdge", "#C4D8E6", 0.96)
    material("M_Road", "#F7F8F6", 0.88)
    material("M_Sidewalk", "#E7EDF0", 0.92)
    material("M_Curb", "#C7D1D4", 0.86)
    material("M_Mark", "#FBF6E8", 0.72, emission="#F6E8C8", emission_strength=0.10)
    material("M_Plaza", "#E9F1F5", 0.9)
    material("M_PlazaWarm", "#EFEDE5", 0.9)
    material("M_PlazaLine", "#FFFFFF", 0.75)
    material("M_Grass", "#B1E3C0", 0.96)
    material("M_GrassLight", "#BFEBCB", 0.96)
    material("M_GrassDark", "#9BD2AC", 0.96)
    material("M_Asphalt", "#D9DDDA", 0.95)
    material("M_Stone", "#D9D3C6", 0.82)
    material("M_StoneDark", "#B7B0A2", 0.84)
    material("M_StoneWarm", "#CFC3AE", 0.8)
    material("M_Cream", "#EFE8D9", 0.8)
    material("M_CreamBright", "#F3EEE3", 0.78)
    material("M_WarmWhite", "#EDE4D2", 0.82)
    material("M_Sage", "#DDE4DA", 0.82)
    material("M_SageDark", "#C6D1C7", 0.82)
    material("M_GreyWall", "#D8DEE0", 0.8)
    material("M_Brick", "#B9795A", 0.86)
    material("M_Accent", "#C49A52", 0.42, metallic=0.45)
    material("M_AccentDark", "#8E6A36", 0.46, metallic=0.35)
    material("M_Teal", "#4E7C78", 0.55, metallic=0.1)
    material("M_Roof", "#AEB6B3", 0.86)
    material("M_RoofDark", "#7C8784", 0.84)
    material("M_RoofLight", "#C6CCCA", 0.84)
    material("M_Glass", "#6E9DA9", 0.16, metallic=0.25, emission="#2C5560", emission_strength=0.05)
    material("M_GlassDark", "#416D78", 0.14, metallic=0.35, emission="#1F4048", emission_strength=0.05)
    material("M_GlassBlue", "#7FAFBB", 0.13, metallic=0.3, emission="#315C66", emission_strength=0.05)
    material("M_Metal", "#465153", 0.4, metallic=0.5)
    material("M_MetalLight", "#7B8789", 0.38, metallic=0.55)
    material("M_Wood", "#9A6B45", 0.8)
    material("M_Trunk", "#6E5138", 0.9)
    material("M_Leaf1", "#5F8F5E", 0.92)
    material("M_Leaf2", "#77A46B", 0.92)
    material("M_Leaf3", "#8CB877", 0.92)
    material("M_Hedge", "#6D9B62", 0.92)
    material("M_Shadow", "#9AA8A4", 0.95)
    material("M_Solar", "#263B4A", 0.22, metallic=0.55, emission="#122230", emission_strength=0.05)
    material("M_LampGlow", "#FFD9A0", 0.3, emission="#FFD08A", emission_strength=3.0)
    material("M_Car1", "#A9B6B8", 0.35, metallic=0.4)
    material("M_Car2", "#7A8F8C", 0.35, metallic=0.4)
    material("M_Car3", "#C7C2B5", 0.35, metallic=0.4)
    material("M_Car4", "#5A6F79", 0.35, metallic=0.4)
    material("M_Water", "#8CC4CC", 0.1, metallic=0.2)


_ICO_CACHE = {}


def ico_template(subdiv=1):
    if subdiv in _ICO_CACHE:
        return _ICO_CACHE[subdiv]
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=subdiv, radius=1.0)
    bm.verts.ensure_lookup_table()
    verts = [v.co.copy() for v in bm.verts]
    faces = [tuple(v.index for v in f.verts) for f in bm.faces]
    bm.free()
    _ICO_CACHE[subdiv] = (verts, faces)
    return verts, faces


class MeshBuilder:
    def __init__(self, name):
        self.name = name
        self.verts = []
        self.faces = []
        self.face_mats = []
        self.face_smooth = []
        self.mats = []
        self.mat_index = {}

    def _mi(self, mat_name):
        if mat_name not in self.mat_index:
            self.mat_index[mat_name] = len(self.mats)
            self.mats.append(mat_name)
        return self.mat_index[mat_name]

    def add_face(self, points, mat_name, smooth=False):
        base = len(self.verts)
        self.verts.extend(points)
        self.faces.append(tuple(range(base, base + len(points))))
        self.face_mats.append(self._mi(mat_name))
        self.face_smooth.append(smooth)

    def quad(self, a, b, c, d, mat_name, smooth=False):
        self.add_face((a, b, c, d), mat_name, smooth)

    def box(self, x0, x1, y0, y1, z0, z1, mat_name):
        if x1 < x0:
            x0, x1 = x1, x0
        if y1 < y0:
            y0, y1 = y1, y0
        if z1 < z0:
            z0, z1 = z1, z0
        v = [
            (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
            (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1),
        ]
        base = len(self.verts)
        self.verts.extend(v)
        for face in ((0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4),
                     (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)):
            self.faces.append(tuple(base + i for i in face))
            self.face_mats.append(self._mi(mat_name))
            self.face_smooth.append(False)

    def box_c(self, cx, cy, sx, sy, z0, z1, mat_name):
        self.box(cx - sx / 2, cx + sx / 2, cy - sy / 2, cy + sy / 2, z0, z1, mat_name)

    def prism(self, poly, z0, z1, mat_name, cap_bottom=True, cap_top=True):
        n = len(poly)
        base = len(self.verts)
        for x, y in poly:
            self.verts.append((x, y, z0))
        for x, y in poly:
            self.verts.append((x, y, z1))
        if cap_bottom:
            self.faces.append(tuple(base + i for i in reversed(range(n))))
            self.face_mats.append(self._mi(mat_name))
            self.face_smooth.append(False)
        if cap_top:
            self.faces.append(tuple(base + n + i for i in range(n)))
            self.face_mats.append(self._mi(mat_name))
            self.face_smooth.append(False)
        for i in range(n):
            j = (i + 1) % n
            self.faces.append((base + i, base + j, base + n + j, base + n + i))
            self.face_mats.append(self._mi(mat_name))
            self.face_smooth.append(False)

    def cylinder(self, cx, cy, z0, z1, radius, mat_name, segments=12,
                 cap=True, smooth=True, radius_top=None):
        if radius_top is None:
            radius_top = radius
        base = len(self.verts)
        for k in range(segments):
            a = 2 * math.pi * k / segments
            self.verts.append((cx + radius * math.cos(a), cy + radius * math.sin(a), z0))
        for k in range(segments):
            a = 2 * math.pi * k / segments
            self.verts.append((cx + radius_top * math.cos(a), cy + radius_top * math.sin(a), z1))
        for k in range(segments):
            j = (k + 1) % segments
            self.faces.append((base + k, base + j, base + segments + j, base + segments + k))
            self.face_mats.append(self._mi(mat_name))
            self.face_smooth.append(smooth)
        if cap:
            self.faces.append(tuple(base + k for k in reversed(range(segments))))
            self.face_mats.append(self._mi(mat_name))
            self.face_smooth.append(False)
            self.faces.append(tuple(base + segments + k for k in range(segments)))
            self.face_mats.append(self._mi(mat_name))
            self.face_smooth.append(False)

    def ico(self, cx, cy, cz, rx, ry, rz, mat_name, subdiv=1, smooth=True):
        verts, faces = ico_template(subdiv)
        base = len(self.verts)
        self.verts.extend((cx + v.x * rx, cy + v.y * ry, cz + v.z * rz) for v in verts)
        for f in faces:
            self.faces.append(tuple(base + i for i in f))
            self.face_mats.append(self._mi(mat_name))
            self.face_smooth.append(smooth)

    def add_text(self, body, origin, size, facing="south", mat_name="M_AccentDark",
                 extrude=0.004):
        verts, faces = text_mesh(body, size, extrude)
        if not verts:
            return
        if facing == "south":
            rot = Euler((math.pi / 2, 0, 0), "XYZ")
        elif facing == "north":
            rot = Euler((math.pi / 2, 0, math.pi), "XYZ")
        elif facing == "east":
            rot = Euler((math.pi / 2, 0, math.pi / 2), "XYZ")
        elif facing == "west":
            rot = Euler((math.pi / 2, 0, -math.pi / 2), "XYZ")
        else:
            rot = Euler((0, 0, 0), "XYZ")
        rm = rot.to_matrix()
        base = len(self.verts)
        ox, oy, oz = origin
        for v in verts:
            p = rm @ v
            self.verts.append((ox + p.x, oy + p.y, oz + p.z))
        for f in faces:
            self.faces.append(tuple(base + i for i in f))
            self.face_mats.append(self._mi(mat_name))
            self.face_smooth.append(False)

    def finish(self, collection, extras=None):
        if not self.faces:
            return None
        mesh = bpy.data.meshes.new(self.name)
        mesh.from_pydata(self.verts, [], self.faces)
        mesh.update()
        mesh.validate(verbose=False)
        for name in self.mats:
            mesh.materials.append(MATERIALS[name])
        for poly, mi, smooth in zip(mesh.polygons, self.face_mats, self.face_smooth):
            poly.material_index = mi
            poly.use_smooth = smooth
        bm = bmesh.new()
        bm.from_mesh(mesh)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.to_mesh(mesh)
        bm.free()
        obj = bpy.data.objects.new(self.name, mesh)
        collection.objects.link(obj)
        if extras:
            for key, value in extras.items():
                try:
                    obj[key] = value
                except Exception:
                    pass
        return obj


_FONT = None
_TEXT_CACHE = {}


def get_font():
    global _FONT
    if _FONT is None:
        _FONT = bpy.data.fonts.load(FONT_PATH) if os.path.exists(FONT_PATH) else None
    return _FONT


def text_mesh(body, size, extrude=0.004):
    key = (body, round(size, 5), round(extrude, 5))
    if key in _TEXT_CACHE:
        return _TEXT_CACHE[key]
    font = get_font()
    curve = bpy.data.curves.new("_tmp_text_curve", type="FONT")
    curve.body = body
    if font:
        curve.font = font
    curve.size = size
    curve.extrude = extrude
    curve.align_x = "CENTER"
    curve.align_y = "CENTER"
    curve.resolution_u = 2
    obj = bpy.data.objects.new("_tmp_text", curve)
    bpy.context.scene.collection.objects.link(obj)
    bpy.context.view_layer.update()
    deps = bpy.context.evaluated_depsgraph_get()
    mesh = bpy.data.meshes.new_from_object(obj.evaluated_get(deps))
    verts = [v.co.copy() for v in mesh.vertices]
    faces = [tuple(p.vertices) for p in mesh.polygons]
    bpy.data.objects.remove(obj, do_unlink=True)
    bpy.data.meshes.remove(mesh)
    bpy.data.curves.remove(curve)
    _TEXT_CACHE[key] = (verts, faces)
    return verts, faces


def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for datablocks in (bpy.data.meshes, bpy.data.materials, bpy.data.curves,
                       bpy.data.cameras, bpy.data.lights):
        for block in list(datablocks):
            if block.users == 0:
                datablocks.remove(block)


COL_GROUND = None
COL_ROADS = None
COL_BUILDINGS = None
COL_LAND = None
COL_DETAIL = None
COL_RENDER = None


def make_collection(name):
    col = bpy.data.collections.get(name)
    if col is None:
        col = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(col)
    return col


def setup_collections():
    global COL_GROUND, COL_ROADS, COL_BUILDINGS, COL_LAND, COL_DETAIL, COL_RENDER
    COL_GROUND = make_collection("00_Ground")
    COL_ROADS = make_collection("10_Roads")
    COL_BUILDINGS = make_collection("20_Buildings")
    COL_LAND = make_collection("30_Landscape")
    COL_DETAIL = make_collection("40_Details")
    COL_RENDER = make_collection("90_Render")


def add_rect_slab(mb, rect, z0, z1, mat_name, expand=0.0):
    xmin, xmax, ymin, ymax = L.rect_bounds(rect)
    mb.box(xmin - expand, xmax + expand, ymin - expand, ymax + expand, z0, z1, mat_name)


def build_ground():
    mb = MeshBuilder("Site_Ground")
    mb.box(-34, 34, -26, 26, -0.18, -0.035, "M_GroundEdge")
    mb.box(-27.2, 27.2, -21.9, 20.6, -0.04, 0.0, "M_Ground")
    return mb.finish(COL_GROUND, {"campusRole": "ground"})


def build_plazas():
    obj_list = []
    for plaza in L.PLAZAS:
        xmin, xmax, ymin, ymax = L.rect_bounds(plaza["rect"])
        style = plaza["style"]
        mb = MeshBuilder("Plaza_" + plaza["name"])
        base_mat = "M_Asphalt" if style == "parking" else (
            "M_PlazaWarm" if style == "grand" else "M_Plaza")
        mb.box(xmin, xmax, ymin, ymax, 0.005, 0.022, base_mat)
        if style != "parking":
            step = 0.55 if style == "grand" else 0.7
            line = 0.008
            x = xmin + step
            while x < xmax - 0.05:
                mb.box(x, x + line, ymin, ymax, 0.022, 0.026, "M_PlazaLine")
                x += step
            y = ymin + step
            while y < ymax - 0.05:
                mb.box(xmin, xmax, y, y + line, 0.022, 0.026, "M_PlazaLine")
                y += step
        if style == "grand":
            cx = (xmin + xmax) / 2
            cy = (ymin + ymax) / 2
            for radius in (1.05, 1.55, 2.05):
                seg = 56
                for k in range(seg):
                    a0 = 2 * math.pi * k / seg
                    a1 = 2 * math.pi * (k + 1) / seg
                    x0 = cx + radius * math.cos(a0)
                    y0 = cy + radius * math.sin(a0)
                    x1 = cx + radius * math.cos(a1)
                    y1 = cy + radius * math.sin(a1)
                    nx = math.cos((a0 + a1) / 2) * 0.012
                    ny = math.sin((a0 + a1) / 2) * 0.012
                    mb.quad((x0 - nx, y0 - ny, 0.027), (x1 - nx, y1 - ny, 0.027),
                            (x1 + nx, y1 + ny, 0.027), (x0 + nx, y0 + ny, 0.027),
                            "M_PlazaLine")
            mb.cylinder(cx, cy, 0.024, 0.03, 0.62, "M_PlazaWarm", 40, cap=True, smooth=False)
            mb.cylinder(cx, cy, 0.03, 0.034, 0.72, "M_Accent", 40, cap=False, smooth=False)
        if style == "parking":
            rows = [ymin + (ymax - ymin) * 0.30, ymin + (ymax - ymin) * 0.70]
            for row_y in rows:
                x = xmin + 0.55
                while x < xmax - 0.55:
                    mb.box(x, x + 0.012, row_y - 0.55, row_y + 0.55, 0.022, 0.027, "M_PlazaLine")
                    x += 0.62
                mb.box(xmin + 0.4, xmax - 0.4, row_y - 0.55, row_y - 0.54, 0.022, 0.027, "M_PlazaLine")
                mb.box(xmin + 0.4, xmax - 0.4, row_y + 0.54, row_y + 0.55, 0.022, 0.027, "M_PlazaLine")
        obj = mb.finish(COL_GROUND, {"campusRole": "plaza", "plazaName": plaza["name"]})
        if obj:
            obj_list.append(obj)
    return obj_list


def build_round_plazas():
    objs = []
    for item in getattr(L, "ROUND_PLAZAS", []):
        cx, cy = L.wx(item["center"][0]), L.by(item["center"][1])
        r = item["radius"] / L.PX_PER_UNIT
        mb = MeshBuilder("Plaza_" + item["name"])
        mb.cylinder(cx, cy, 0.005, 0.024, r, "M_Plaza", 64, cap=True, smooth=False)
        mb.cylinder(cx, cy, 0.024, 0.050, r * 0.72, "M_Grass", 64, cap=True, smooth=False)
        seg = 64
        for k in range(seg):
            a0 = 2 * math.pi * k / seg
            a1 = 2 * math.pi * (k + 1) / seg
            r0, r1 = r * 0.80, r * 0.86
            p0 = (cx + r0 * math.cos(a0), cy + r0 * math.sin(a0), 0.052)
            p1 = (cx + r1 * math.cos(a0), cy + r1 * math.sin(a0), 0.052)
            p2 = (cx + r1 * math.cos(a1), cy + r1 * math.sin(a1), 0.052)
            p3 = (cx + r0 * math.cos(a1), cy + r0 * math.sin(a1), 0.052)
            mb.quad(p0, p1, p2, p3, "M_PlazaLine")
        mb.cylinder(cx, cy, 0.050, 0.20, 0.24, "M_Stone", 24, cap=True, smooth=False)
        mb.cylinder(cx, cy, 0.20, 0.26, 0.20, "M_Hedge", 24, cap=True, smooth=True)
        obj = mb.finish(COL_GROUND, {"campusRole": "plaza", "plazaName": item["name"]})
        if obj:
            objs.append(obj)
    return objs


def build_field():
    rect = L.FIELD["rect"]
    xmin, xmax, ymin, ymax = L.rect_bounds(rect)
    mb = MeshBuilder("SportsField")
    mb.box(xmin - 0.12, xmax + 0.12, ymin - 0.12, ymax + 0.12, 0.0, 0.045, "M_Curb")
    mb.box(xmin, xmax, ymin, ymax, 0.045, 0.058, "M_Grass")
    y = ymin + 0.25
    index = 0
    while y < ymax - 0.2:
        if index % 2 == 0:
            mb.box(xmin + 0.05, xmax - 0.05, y, min(y + 0.55, ymax - 0.1), 0.058, 0.061,
                   "M_GrassLight")
        y += 0.55
        index += 1
    for x in [xmin, xmax]:
        yy = ymin + 0.15
        while yy < ymax - 0.1:
            mb.box(x - 0.018, x + 0.018, yy - 0.018, yy + 0.018, 0.058, 0.42, "M_Metal")
            yy += 0.42
    for yedge in [ymin, ymax]:
        xx = xmin + 0.15
        while xx < xmax - 0.1:
            mb.box(xx - 0.018, xx + 0.018, yedge - 0.018, yedge + 0.018, 0.058, 0.42, "M_Metal")
            xx += 0.42
    for z in (0.34, 0.42):
        mb.box(xmin - 0.02, xmax + 0.02, ymin - 0.02, ymin + 0.02, z - 0.012, z + 0.012, "M_Metal")
        mb.box(xmin - 0.02, xmax + 0.02, ymax - 0.02, ymax + 0.02, z - 0.012, z + 0.012, "M_Metal")
        mb.box(xmin - 0.02, xmin + 0.02, ymin, ymax, z - 0.012, z + 0.012, "M_Metal")
        mb.box(xmax - 0.02, xmax + 0.02, ymin, ymax, z - 0.012, z + 0.012, "M_Metal")
    return mb.finish(COL_LAND, {"campusRole": "sportsField", "name": "运动场"})


def _polyline_world(points):
    return [(L.wx(px), L.by(py)) for px, py in points]


def _add_disk(mb, cx, cy, radius, z, mat_name, segments=18):
    poly = [(cx + radius * math.cos(2 * math.pi * k / segments),
             cy + radius * math.sin(2 * math.pi * k / segments)) for k in range(segments)]
    mb.prism(poly, z, z + 0.004, mat_name, cap_bottom=False, cap_top=True)


def _ribbon(mb, pts, width, z, mat_name):
    half = width / 2
    for i in range(len(pts) - 1):
        x0, y0 = pts[i]
        x1, y1 = pts[i + 1]
        dx, dy = x1 - x0, y1 - y0
        length = math.hypot(dx, dy)
        if length < 1e-6:
            continue
        nx, ny = -dy / length * half, dx / length * half
        mb.quad((x0 + nx, y0 + ny, z), (x1 + nx, y1 + ny, z),
                (x1 - nx, y1 - ny, z), (x0 - nx, y0 - ny, z), mat_name)
    for i, (x, y) in enumerate(pts):
        if i == 0 or i == len(pts) - 1:
            continue
        _add_disk(mb, x, y, half, z, mat_name, 18)


def _distance_point_segment(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    length2 = dx * dx + dy * dy
    if length2 < 1e-9:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def road_world_data():
    data = []
    for road in L.ROADS:
        pts = _polyline_world(road["points"])
        width = road["width"] / L.PX_PER_UNIT * 1.02
        data.append({"name": road["name"], "kind": road["kind"], "points": pts,
                     "width": width})
    return data


def build_roads():
    roads = road_world_data()
    mb_side = MeshBuilder("Roads_Sidewalks")
    mb_curb = MeshBuilder("Roads_Curbs")
    mb_road = MeshBuilder("Roads_Surface")
    mb_mark = MeshBuilder("Roads_Markings")
    for road in roads:
        pts, w = road["points"], road["width"]
        _ribbon(mb_side, pts, w + 0.16, 0.030, "M_Sidewalk")
        _ribbon(mb_curb, pts, w + 0.07, 0.040, "M_Curb")
        _ribbon(mb_road, pts, w, 0.052, "M_Road")
        if road["kind"] == "lane":
            continue
        spacing = 1.05
        dash = 0.42
        for i in range(len(pts) - 1):
            x0, y0 = pts[i]
            x1, y1 = pts[i + 1]
            dx, dy = x1 - x0, y1 - y0
            length = math.hypot(dx, dy)
            if length < 0.1:
                continue
            ux, uy = dx / length, dy / length
            t = 0.25
            while t < length - 0.1:
                cx = x0 + ux * t
                cy = y0 + uy * t
                half_w = 0.018 if road["kind"] == "main" else 0.014
                mb_mark.box(cx - ux * dash / 2 - uy * half_w,
                            cx + ux * dash / 2 + uy * half_w,
                            cy - uy * dash / 2 + ux * half_w,
                            cy + uy * dash / 2 - ux * half_w,
                            0.055, 0.061, "M_Mark")
                t += spacing
    objs = []
    for mb in (mb_side, mb_curb, mb_road, mb_mark):
        obj = mb.finish(COL_ROADS, {"campusRole": "roads"})
        if obj:
            objs.append(obj)
    return objs


def add_crosswalk(mb, cx, cy, angle, road_width):
    ux, uy = math.cos(angle), math.sin(angle)
    nx, ny = -uy, ux
    count = max(4, int(road_width / 0.17))
    for i in range(count):
        off = (i - (count - 1) / 2) * 0.17
        bx = cx + nx * off
        by = cy + ny * off
        mb.box(bx - ux * 0.26 - nx * 0.032, bx + ux * 0.26 + nx * 0.032,
               by - uy * 0.26 - ny * 0.032, by + uy * 0.26 + ny * 0.032,
               0.062, 0.066, "M_Mark")


def build_crosswalks():
    mb = MeshBuilder("Roads_Crosswalks")
    items = [
        (L.wx(120), L.by(630), 0.0, 1.6),
        (L.wx(792), L.by(652), 0.0, 1.7),
        (L.wx(1296), L.by(682), 0.0, 1.7),
        (L.wx(1626), L.by(688), 0.0, 1.6),
        (L.wx(680), L.by(780), 0.0, 0.75),
        (L.wx(1600), L.by(1060), 0.0, 1.05),
        (L.wx(465), L.by(900), math.pi / 2, 0.9),
    ]
    for item in items:
        add_crosswalk(mb, *item)
    return mb.finish(COL_ROADS, {"campusRole": "crosswalks"})


STYLE_WINDOW = {
    "dorm": (0.185, 0.345, 0.42),
    "lab": (0.235, 0.292, 0.54),
    "tech": (0.25, 0.275, 0.60),
    "chem": (0.215, 0.305, 0.50),
    "admin": (0.205, 0.36, 0.46),
    "service": (0.18, 0.36, 0.42),
    "utility": (0.16, 0.42, 0.38),
    "hall": (0.30, 0.38, 0.62),
    "gate": (0.18, 0.40, 0.46),
}


def wall_band(mb, xmin, xmax, ymin, ymax, z, height, out, mat_name):
    mb.box(xmin - out, xmax + out, ymin - out, ymin + 0.012, z, z + height, mat_name)
    mb.box(xmin - out, xmax + out, ymax - 0.012, ymax + out, z, z + height, mat_name)
    mb.box(xmin - out, xmin + 0.012, ymin, ymax, z, z + height, mat_name)
    mb.box(xmax - 0.012, xmax + out, ymin, ymax, z, z + height, mat_name)


def facade_windows(mb, xmin, xmax, ymin, ymax, floors, floor_h, style,
                   glass_mat="M_Glass", frame_mat="M_Metal", base_z=0.10,
                   margin=0.20, skip_sides=()):
    win_w, spacing, ratio = STYLE_WINDOW.get(style, STYLE_WINDOW["dorm"])
    win_h = max(0.075, floor_h * ratio)
    facets = [
        ("south", xmin, xmax, ymin, "x"),
        ("north", xmin, xmax, ymax, "x"),
        ("west", ymin, ymax, xmin, "y"),
        ("east", ymin, ymax, xmax, "y"),
    ]
    for name, a0, a1, fixed, axis in facets:
        if name in skip_sides:
            continue
        length = a1 - a0
        usable = length - margin * 2
        if usable < 0.18:
            continue
        count = max(1, int(usable / spacing))
        actual = usable / count
        for floor in range(floors):
            zc = base_z + floor * floor_h + floor_h * 0.56
            for i in range(count):
                c = a0 + margin + (i + 0.5) * actual
                w = min(win_w, actual * 0.72)
                if axis == "x":
                    if name == "south":
                        yf0, yf1 = ymin - 0.018, ymin + 0.014
                        yg0, yg1 = ymin - 0.031, ymin - 0.017
                    else:
                        yf0, yf1 = ymax - 0.014, ymax + 0.018
                        yg0, yg1 = ymax + 0.017, ymax + 0.031
                    mb.box(c - w / 2 - 0.018, c + w / 2 + 0.018, yf0, yf1,
                           zc - win_h / 2 - 0.018, zc + win_h / 2 + 0.018, frame_mat)
                    mb.box(c - w / 2, c + w / 2, yg0, yg1,
                           zc - win_h / 2, zc + win_h / 2, glass_mat)
                    mb.box(c - w / 2 - 0.032, c + w / 2 + 0.032,
                           min(yf0, yg0) - 0.008, max(yf1, yg1) + 0.006,
                           zc - win_h / 2 - 0.022, zc - win_h / 2 - 0.006, "M_Stone")
                else:
                    if name == "west":
                        xf0, xf1 = xmin - 0.018, xmin + 0.014
                        xg0, xg1 = xmin - 0.031, xmin - 0.017
                    else:
                        xf0, xf1 = xmax - 0.014, xmax + 0.018
                        xg0, xg1 = xmax + 0.017, xmax + 0.031
                    mb.box(xf0, xf1, c - w / 2 - 0.018, c + w / 2 + 0.018,
                           zc - win_h / 2 - 0.018, zc + win_h / 2 + 0.018, frame_mat)
                    mb.box(xg0, xg1, c - w / 2, c + w / 2,
                           zc - win_h / 2, zc + win_h / 2, glass_mat)
                    mb.box(min(xf0, xg0) - 0.008, max(xf1, xg1) + 0.006,
                           c - w / 2 - 0.032, c + w / 2 + 0.032,
                           zc - win_h / 2 - 0.022, zc - win_h / 2 - 0.006, "M_Stone")


def corner_trim(mb, xmin, xmax, ymin, ymax, height, mat_name="M_Stone", w=0.055):
    for x in (xmin, xmax):
        for y in (ymin, ymax):
            mb.box(x - w / 2, x + w / 2, y - w / 2, y + w / 2, 0.10, height, mat_name)


def roof_parapet(mb, xmin, xmax, ymin, ymax, z, mat_name="M_RoofDark", h=0.12, t=0.05):
    mb.box(xmin - 0.02, xmax + 0.02, ymin - 0.02, ymin + t, z, z + h, mat_name)
    mb.box(xmin - 0.02, xmax + 0.02, ymax - t, ymax + 0.02, z, z + h, mat_name)
    mb.box(xmin - 0.02, xmin + t, ymin, ymax, z, z + h, mat_name)
    mb.box(xmax - t, xmax + 0.02, ymin, ymax, z, z + h, mat_name)


def dorm_balconies(mb, xmin, xmax, ymin, ymax, floors, floor_h, base_z=0.10):
    span = xmax - xmin
    if span < 0.7:
        return
    x0, x1 = xmin + 0.09, xmax - 0.09
    bays = max(3, int(span / 0.95))
    for floor in range(floors):
        z = base_z + floor * floor_h + 0.015
        mb.box(x0, x1, ymin - 0.16, ymin - 0.015, z, z + 0.032, "M_Stone")
        mb.box(x0, x1, ymin - 0.165, ymin - 0.145, z + 0.175, z + 0.195, "M_Metal")
        mb.box(x0 + 0.02, x1 - 0.02, ymin - 0.158, ymin - 0.148,
               z + 0.045, z + 0.17, "M_GlassBlue")
        for i in range(bays + 1):
            bx = x0 + (x1 - x0) * i / bays
            mb.box(bx - 0.012, bx + 0.012, ymin - 0.168, ymin - 0.142,
                   z + 0.03, z + 0.20, "M_Metal")
        for i in range(1, bays, 2):
            bx = x0 + (x1 - x0) * i / bays
            mb.box(bx - 0.022, bx + 0.022, ymin - 0.17, ymin - 0.02,
                   z + 0.03, z + 0.19, "M_WarmWhite")


def dorm_roof(mb, xmin, xmax, ymin, ymax, z, variant=0):
    sx = xmin + (xmax - xmin) * 0.18
    mb.box(sx, sx + 0.5, ymin + 0.16, ymin + 0.58, z, z + 0.30, "M_Cream")
    mb.box(sx - 0.02, sx + 0.52, ymin + 0.14, ymin + 0.60, z + 0.30, z + 0.33, "M_RoofDark")
    for i in range(2):
        cx = xmax - 0.35 - i * 0.34
        cy = ymax - 0.30
        for dx in (-0.09, 0.09):
            mb.box(cx + dx - 0.02, cx + dx + 0.02, cy - 0.02, cy + 0.02, z, z + 0.12, "M_Metal")
        mb.cylinder(cx, cy, z + 0.12, z + 0.42, 0.115, "M_MetalLight", 14, cap=True, smooth=True)
        mb.cylinder(cx, cy, z + 0.42, z + 0.45, 0.12, "M_RoofDark", 14, cap=True, smooth=False)
    if variant % 3 != 2 and (xmax - xmin) > 1.6:
        cols = min(6, max(3, int((xmax - xmin) / 0.42)))
        pw = 0.30
        for i in range(cols):
            cx = xmin + 0.3 + i * 0.44
            if cx > xmax - 0.25:
                break
            cy = (ymin + ymax) / 2 + 0.20
            mb.box(cx - pw / 2, cx + pw / 2, cy - 0.24, cy + 0.24, z + 0.01, z + 0.05, "M_Metal")
            dy = 0.30
            zz = z + 0.09
            mb.quad((cx - pw / 2, cy - dy, zz), (cx + pw / 2, cy - dy, zz),
                    (cx + pw / 2, cy + dy, zz + 0.10), (cx - pw / 2, cy + dy, zz + 0.10),
                    "M_Solar")
            mb.quad((cx - pw / 2, cy + dy, zz + 0.10), (cx + pw / 2, cy + dy, zz + 0.10),
                    (cx + pw / 2, cy - dy, zz), (cx - pw / 2, cy - dy, zz),
                    "M_Solar")


def roof_hvac(mb, xmin, xmax, ymin, ymax, z, count=2, tower=False):
    if tower:
        for i in range(2):
            cx = xmin + (xmax - xmin) * (0.32 + 0.36 * i)
            cy = ymin + (ymax - ymin) * 0.5
            mb.cylinder(cx, cy, z, z + 0.38, 0.19, "M_MetalLight", 18, cap=True, smooth=True)
            mb.cylinder(cx, cy, z + 0.38, z + 0.41, 0.21, "M_RoofDark", 18, cap=True, smooth=False)
    for i in range(count):
        cx = xmin + (xmax - xmin) * (0.16 + 0.22 * i)
        cy = ymax - 0.38
        if cx > xmax - 0.2:
            break
        mb.box(cx - 0.22, cx + 0.22, cy - 0.16, cy + 0.16, z, z + 0.20, "M_RoofLight")
        mb.cylinder(cx, cy, z + 0.20, z + 0.215, 0.10, "M_Metal", 16, cap=True, smooth=False)
        mb.cylinder(cx, cy, z + 0.215, z + 0.225, 0.055, "M_MetalLight", 12, cap=True, smooth=False)


def add_entrance(mb, px, py, side, style, text=None, width=1.15, height=0.46):
    x, y = L.wx(px), L.by(py)
    depth = 0.42
    if side == "south":
        ox, oy = 0, -1
    elif side == "north":
        ox, oy = 0, 1
    elif side == "west":
        ox, oy = -1, 0
    else:
        ox, oy = 1, 0
    for i, (dz, extra) in enumerate(((0.0, 0.18), (0.035, 0.10), (0.07, 0.03))):
        if side in ("south", "north"):
            mb.box(x - width / 2 - extra, x + width / 2 + extra,
                   y + oy * (0.06 + i * 0.09) - 0.06,
                   y + oy * (0.06 + i * 0.09) + 0.06,
                   dz, dz + 0.035, "M_Stone")
        else:
            mb.box(x + ox * (0.06 + i * 0.09) - 0.06,
                   x + ox * (0.06 + i * 0.09) + 0.06,
                   y - width / 2 - extra, y + width / 2 + extra,
                   dz, dz + 0.035, "M_Stone")
    if side in ("south", "north"):
        y0 = y + oy * 0.02
        y1 = y + oy * 0.09
        mb.box(x - width * 0.40, x + width * 0.40, min(y0, y1), max(y0, y1),
               0.10, 0.44, "M_GlassDark")
        mb.box(x - 0.02, x + 0.02, min(y0, y1) - 0.01, max(y0, y1) + 0.01,
               0.10, 0.44, "M_Metal")
    else:
        x0 = x + ox * 0.02
        x1 = x + ox * 0.09
        mb.box(min(x0, x1), max(x0, x1), y - width * 0.40, y + width * 0.40,
               0.10, 0.44, "M_GlassDark")
    cz = 0.46
    if side in ("south", "north"):
        cy0 = y + oy * 0.05
        cy1 = y + oy * (0.05 + depth)
        mb.box(x - width * 0.66, x + width * 0.66, min(cy0, cy1), max(cy0, cy1),
               cz, cz + 0.05, "M_CreamBright")
        mb.box(x - width * 0.60, x + width * 0.60, min(cy0, cy1) + 0.02,
               max(cy0, cy1) - 0.02, cz - 0.012, cz, "M_Accent")
        for sx in (-1, 1):
            cxx = x + sx * width * 0.58
            mb.box(cxx - 0.022, cxx + 0.022, y + oy * 0.10, y + oy * 0.14, 0.10, cz, "M_Metal")
    else:
        cx0 = x + ox * 0.05
        cx1 = x + ox * (0.05 + depth)
        mb.box(min(cx0, cx1), max(cx0, cx1), y - width * 0.66, y + width * 0.66,
               cz, cz + 0.05, "M_CreamBright")
        mb.box(min(cx0, cx1) + 0.02, max(cx0, cx1) - 0.02, y - width * 0.60,
               y + width * 0.60, cz - 0.012, cz, "M_Accent")
    if side in ("south", "north"):
        sy = y + oy * 0.025
        mb.box(x - 0.52, x + 0.52, sy - 0.015, sy + 0.015, 0.63, 0.78, "M_AccentDark")
        if text:
            mb.add_text(text, (x, sy + oy * 0.018, 0.705), 0.105,
                        "south" if side == "south" else "north", "M_CreamBright")
    else:
        sx = x + ox * 0.025
        mb.box(sx - 0.015, sx + 0.015, y - 0.52, y + 0.52, 0.63, 0.78, "M_AccentDark")
        if text:
            mb.add_text(text, (sx + ox * 0.018, y, 0.705), 0.105,
                        "east" if side == "east" else "west", "M_CreamBright")


BUILDING_STYLE = {
    "dorm": ("M_Cream", "M_Teal", "M_Glass"),
    "lab": ("M_WarmWhite", "M_Teal", "M_GlassBlue"),
    "tech": ("M_GreyWall", "M_Teal", "M_GlassDark"),
    "chem": ("M_Sage", "M_Teal", "M_Glass"),
    "admin": ("M_CreamBright", "M_Accent", "M_GlassBlue"),
    "service": ("M_WarmWhite", "M_StoneWarm", "M_Glass"),
    "utility": ("M_SageDark", "M_StoneDark", "M_Glass"),
    "hall": ("M_GreyWall", "M_Accent", "M_GlassDark"),
    "gate": ("M_CreamBright", "M_Accent", "M_Glass"),
}


def build_building(spec):
    mb = MeshBuilder("B_" + spec["key"])
    wall, accent, glass = BUILDING_STYLE.get(spec["style"], BUILDING_STYLE["dorm"])
    style = spec["style"]
    floor_h = spec["floor_h"] or (0.255 if style == "dorm" else 0.30)
    total_height = 0.0
    for item in spec["blocks"]:
        rect, floors = item[0], item[1]
        fh = item[2] if len(item) > 2 else floor_h
        xmin, xmax, ymin, ymax = L.rect_bounds(rect)
        h = 0.10 + floors * fh
        total_height = max(total_height, h)
        mb.box(xmin - 0.055, xmax + 0.055, ymin - 0.055, ymax + 0.055, 0.0, 0.10, "M_StoneDark")
        mb.box(xmin, xmax, ymin, ymax, 0.10, h, wall)
        corner_trim(mb, xmin, xmax, ymin, ymax, h)
        for f in range(1, floors):
            z = 0.10 + f * fh
            wall_band(mb, xmin, xmax, ymin, ymax, z - 0.017, 0.034, 0.020, "M_Stone")
        wall_band(mb, xmin, xmax, ymin, ymax, h - 0.055, 0.09, 0.045, "M_Stone")
        mb.box(xmin + 0.015, xmax - 0.015, ymin + 0.015, ymax - 0.015, h, h + 0.028, "M_Roof")
        roof_parapet(mb, xmin, xmax, ymin, ymax, h + 0.02)
        facade_windows(mb, xmin, xmax, ymin, ymax, floors, fh, style, glass_mat=glass)
        wall_band(mb, xmin, xmax, ymin, ymax, 0.115, 0.07, 0.028, accent)
        if style == "dorm":
            dorm_balconies(mb, xmin, xmax, ymin, ymax, floors, fh)
        if style == "dorm" and (xmax - xmin) > 1.1:
            for xx in (xmin + 0.06, xmax - 0.06):
                mb.box(xx - 0.035, xx + 0.035, ymin - 0.03, ymax + 0.03,
                       0.10, h - 0.06, accent)
        if style == "dorm":
            dorm_roof(mb, xmin, xmax, ymin, ymax, h + 0.03, variant=len(mb.faces))
        else:
            hv = spec["roof"].get("hvac", 2)
            roof_hvac(mb, xmin, xmax, ymin, ymax, h + 0.03, hv,
                      tower=spec["roof"].get("tower", False))
            mb.box(xmin + 0.22, xmin + 0.78, ymax - 0.62, ymax - 0.18, h + 0.03, h + 0.29, wall)
            mb.box(xmin + 0.20, xmin + 0.80, ymax - 0.64, ymax - 0.16, h + 0.29, h + 0.32, "M_RoofDark")
        for i in range(spec["roof"].get("stacks", 0)):
            cx = xmin + (xmax - xmin) * (0.25 + 0.16 * i)
            cy = ymax - 0.30
            mb.cylinder(cx, cy, h + 0.03, h + 0.60, 0.045, "M_MetalLight", 10, cap=True, smooth=True)
            mb.cylinder(cx, cy, h + 0.60, h + 0.64, 0.065, "M_Metal", 10, cap=True, smooth=False)

    if spec.get("courtyard"):
        cx0, cy0, cx1, cy1 = spec["courtyard"]
        xmin, xmax, ymin, ymax = L.rect_bounds((cx0, cy0, cx1, cy1))
        mb.box(xmin, xmax, ymin, ymax, 0.01, 0.035, "M_PlazaWarm")
        mb.box(xmin + 0.22, xmax - 0.22, ymin + 0.22, ymax - 0.22, 0.035, 0.06, "M_Grass")
        mb.cylinder((xmin + xmax) / 2, (ymin + ymax) / 2, 0.06, 0.22, 0.22, "M_Stone", 20,
                    cap=True, smooth=False)
        mb.cylinder((xmin + xmax) / 2, (ymin + ymax) / 2, 0.22, 0.27, 0.18, "M_Hedge", 20,
                    cap=True, smooth=True)

    if spec.get("entrance"):
        (px, py), side = spec["entrance"]
        add_entrance(mb, px, py, side, style, text=spec["name"],
                     width=1.35 if style in ("lab", "tech", "admin") else 1.0)

    return mb.finish(COL_BUILDINGS, {
        "campusRole": "building",
        "buildingKey": spec["key"],
        "buildingName": spec["name"],
        "category": spec["category"],
        "pointId": int(spec["point_id"]) if spec.get("point_id") else 0,
        "floors": int(spec["blocks"][0][1]),
        "height": round(total_height, 3),
    })


def build_all_buildings():
    objs = []
    for spec in L.BUILDINGS:
        obj = build_building(spec)
        if obj:
            objs.append(obj)
    return objs


def build_gates():
    objs = []
    mb = MeshBuilder("Gate_North")
    x0 = L.wx(1278)
    x1 = L.wx(1314)
    y = L.by(712)
    for x in (x0, x1):
        mb.box(x - 0.17, x + 0.17, y - 0.17, y + 0.17, 0.0, 0.88, "M_StoneWarm")
        mb.box(x - 0.21, x + 0.21, y - 0.21, y + 0.21, 0.88, 0.95, "M_StoneDark")
        mb.box(x - 0.12, x + 0.12, y - 0.22, y + 0.22, 0.35, 0.48, "M_Accent")
    mb.box(x0 + 0.18, x1 - 0.18, y - 0.09, y + 0.09, 0.95, 1.18, "M_CreamBright")
    mb.box(x0 + 0.12, x1 - 0.12, y - 0.11, y + 0.11, 1.18, 1.23, "M_StoneDark")
    mb.add_text("北门", ((x0 + x1) / 2, y - 0.10, 1.065), 0.15, "south", "M_AccentDark")
    mb.box(L.wx(1236), L.wx(1266), L.by(735), L.by(705), 0.0, 0.62, "M_GreyWall")
    mb.box(L.wx(1234), L.wx(1268), L.by(737), L.by(703), 0.62, 0.69, "M_RoofDark")
    mb.box(L.wx(1238), L.wx(1264), L.by(733), L.by(731), 0.18, 0.52, "M_GlassBlue")
    objs.append(mb.finish(COL_DETAIL, {"campusRole": "gate", "gateName": "北门", "pointId": 1}))

    mb = MeshBuilder("Gate_East")
    x = L.wx(1600)
    y0 = L.by(1056)
    y1 = L.by(1104)
    for yy in (y0, y1):
        mb.box(x - 0.17, x + 0.17, yy - 0.17, yy + 0.17, 0.0, 0.95, "M_StoneWarm")
        mb.box(x - 0.21, x + 0.21, yy - 0.21, yy + 0.21, 0.95, 1.02, "M_StoneDark")
    mb.box(x - 0.10, x + 0.10, y0 + 0.18, y1 - 0.18, 0.95, 1.16, "M_CreamBright")
    mb.box(x - 0.12, x + 0.12, y0 + 0.12, y1 - 0.12, 1.16, 1.21, "M_StoneDark")
    mb.add_text("东门", (x + 0.11, (y0 + y1) / 2, 1.055), 0.13, "east", "M_AccentDark")
    sy = y1 - 0.28
    for i in range(6):
        yy = sy - i * 0.10
        mb.box(x - 0.045, x - 0.02, yy - 0.012, yy + 0.012, 0.10, 0.85, "M_Metal")
    for z in (0.18, 0.82):
        mb.box(x - 0.05, x - 0.015, sy - 0.52, sy, z, z + 0.03, "M_Metal")
    objs.append(mb.finish(COL_DETAIL, {"campusRole": "gate", "gateName": "东门", "pointId": 11}))
    return objs


def building_clearance(x, y, pad=0.18):
    for spec in L.BUILDINGS:
        for item in spec["blocks"]:
            xmin, xmax, ymin, ymax = L.rect_bounds(item[0])
            if xmin - pad <= x <= xmax + pad and ymin - pad <= y <= ymax + pad:
                return True
    return False


def plaza_clearance(x, y, pad=0.0):
    for plaza in L.PLAZAS:
        xmin, xmax, ymin, ymax = L.rect_bounds(plaza["rect"])
        if xmin - pad <= x <= xmax + pad and ymin - pad <= y <= ymax + pad:
            return True
    return False


def field_clearance(x, y):
    xmin, xmax, ymin, ymax = L.rect_bounds(L.FIELD["rect"])
    return xmin - 0.3 <= x <= xmax + 0.3 and ymin - 0.3 <= y <= ymax + 0.3


def road_clearance(x, y, extra=0.18):
    for road in road_world_data():
        w = road["width"] / 2
        pts = road["points"]
        for i in range(len(pts) - 1):
            if _distance_point_segment(x, y, *pts[i], *pts[i + 1]) < w + extra:
                return True
    return False


def site_clear(x, y):
    if not (-26.4 < x < 26.4 and -19.8 < y < 19.8):
        return False
    if building_clearance(x, y) or plaza_clearance(x, y) or field_clearance(x, y):
        return False
    if road_clearance(x, y):
        return False
    return True


def add_tree(mb, x, y, scale=1.0, variant=0):
    trunk_h = 0.42 * scale
    mb.cylinder(x, y, 0.02, 0.02 + trunk_h, 0.032 * scale, "M_Trunk", 8, cap=True, smooth=True)
    leaf = ("M_Leaf1", "M_Leaf2", "M_Leaf3")[variant % 3]
    leaf2 = ("M_Leaf2", "M_Leaf3", "M_Leaf1")[(variant + 1) % 3]
    r = 0.21 * scale
    mb.ico(x, y, 0.02 + trunk_h + r * 0.75, r, r * 0.92, r * 0.88, leaf, 1, True)
    mb.ico(x + r * 0.55, y + r * 0.18, 0.02 + trunk_h + r * 0.52, r * 0.72, r * 0.66, r * 0.62,
           leaf2, 1, True)
    mb.ico(x - r * 0.50, y - r * 0.12, 0.02 + trunk_h + r * 0.62, r * 0.68, r * 0.62, r * 0.58,
           leaf, 1, True)


def build_trees():
    mb = MeshBuilder("Trees")
    rng = random.Random(20261104)
    placed = []
    for road in road_world_data():
        pts = road["points"]
        w = road["width"] / 2
        for side in (-1, 1):
            for i in range(len(pts) - 1):
                x0, y0 = pts[i]
                x1, y1 = pts[i + 1]
                dx, dy = x1 - x0, y1 - y0
                length = math.hypot(dx, dy)
                if length < 0.4:
                    continue
                ux, uy = dx / length, dy / length
                nx, ny = -uy, ux
                t = 0.55
                while t < length - 0.3:
                    x = x0 + ux * t + nx * side * (w + 0.42)
                    y = y0 + uy * t + ny * side * (w + 0.42)
                    if site_clear(x, y):
                        if all((x - px) ** 2 + (y - py) ** 2 > 0.55 for px, py in placed):
                            add_tree(mb, x, y, 0.78 + rng.random() * 0.30, len(placed))
                            placed.append((x, y))
                    t += 1.55
    clusters = [
        (-12.0, 16.5, 8, 1.2), (-14.5, 9.0, 6, 1.0), (-9.5, 6.0, 6, 1.0),
        (-18.0, 3.0, 5, 1.0), (-5.0, 16.0, 5, 1.0), (0.5, 12.5, 5, 1.0),
        (5.5, 11.5, 5, 1.0), (11.5, 4.0, 5, 1.0), (17.5, 9.0, 5, 1.0),
        (-9.5, -2.0, 5, 1.0), (-9.5, -9.0, 5, 1.0), (-14.0, -12.0, 6, 1.0),
        (-5.5, -15.0, 4, 1.0), (2.0, -4.0, 4, 1.0), (19.0, -5.0, 5, 1.0),
        (22.0, -16.0, 4, 1.0), (-19.5, -16.0, 4, 1.0),
    ]
    for cx, cy, count, spread in clusters:
        for _ in range(count):
            x = cx + (rng.random() - 0.5) * spread * 2
            y = cy + (rng.random() - 0.5) * spread * 2
            if site_clear(x, y) and all((x - px) ** 2 + (y - py) ** 2 > 0.55 for px, py in placed):
                add_tree(mb, x, y, 0.7 + rng.random() * 0.45, len(placed))
                placed.append((x, y))
    for x, y in [(11.8, 8.6), (13.1, 8.6), (12.45, 9.5), (1.1, 13.3), (3.2, 13.3), (5.2, 13.3), (1.6, 15.3), (4.6, 15.3)]:
        add_tree(mb, x, y, 0.72, len(placed))
        placed.append((x, y))
    obj = mb.finish(COL_LAND, {"campusRole": "trees"})
    print(f"[trees] placed={len(placed)}")
    return obj


def build_lamps():
    mb = MeshBuilder("StreetLamps")
    count = 0
    for road in road_world_data():
        if road["kind"] == "lane":
            continue
        pts = road["points"]
        w = road["width"] / 2
        side = 1
        for i in range(len(pts) - 1):
            x0, y0 = pts[i]
            x1, y1 = pts[i + 1]
            dx, dy = x1 - x0, y1 - y0
            length = math.hypot(dx, dy)
            if length < 0.4:
                continue
            ux, uy = dx / length, dy / length
            nx, ny = -uy, ux
            t = 1.0
            while t < length - 0.5:
                x = x0 + ux * t + nx * side * (w + 0.22)
                y = y0 + uy * t + ny * side * (w + 0.22)
                if not building_clearance(x, y, 0.05) and not field_clearance(x, y):
                    mb.cylinder(x, y, 0.02, 0.06, 0.055, "M_Metal", 10, cap=True, smooth=True)
                    mb.cylinder(x, y, 0.06, 0.82, 0.022, "M_Metal", 8, cap=True, smooth=True)
                    mb.box(x - 0.02, x + 0.02, y - 0.02, y + 0.14, 0.80, 0.84, "M_Metal")
                    mb.box(x - 0.055, x + 0.055, y + 0.10, y + 0.20, 0.82, 0.86, "M_LampGlow")
                    count += 1
                side *= -1
                t += 2.6
    obj = mb.finish(COL_DETAIL, {"campusRole": "lamps"})
    print(f"[lamps] placed={count}")
    return obj


def add_bench(mb, x, y, angle=0.0):
    ca, sa = math.cos(angle), math.sin(angle)
    def rot(px, py):
        return (x + px * ca - py * sa, y + px * sa + py * ca)
    for i in range(3):
        p0 = rot(-0.42, -0.10 + i * 0.09)
        p1 = rot(0.42, -0.10 + i * 0.09)
        mb.box(min(p0[0], p1[0]), max(p0[0], p1[0]) - 0.01,
               min(p0[1], p1[1]), max(p0[1], p1[1]) + 0.02,
               0.10, 0.13, "M_Wood")
    for sx in (-0.32, 0.32):
        p0 = rot(sx, -0.16)
        mb.box(p0[0] - 0.018, p0[0] + 0.018, p0[1] - 0.10, p0[1] + 0.10, 0.02, 0.10, "M_Metal")
        p1 = rot(sx, 0.10)
        mb.box(p1[0] - 0.018, p1[0] + 0.018, p1[1] - 0.02, p1[1] + 0.02, 0.10, 0.24, "M_Metal")
    p0 = rot(-0.40, 0.12)
    p1 = rot(0.40, 0.12)
    mb.box(min(p0[0], p1[0]), max(p0[0], p1[0]),
           min(p0[1], p1[1]), max(p0[1], p1[1]) + 0.02, 0.20, 0.24, "M_Wood")


def build_benches():
    mb = MeshBuilder("Benches")
    cx, cy = L.rect_center((1090, 940, 1420, 1130))
    for i in range(6):
        a = 2 * math.pi * i / 6
        add_bench(mb, cx + 2.05 * math.cos(a), cy + 1.65 * math.sin(a), a + math.pi / 2)
    xmin, xmax, ymin, ymax = L.rect_bounds(L.FIELD["rect"])
    for yy in (ymin + 0.8, ymax - 0.8):
        for xx in (xmin + 0.6, xmax - 0.6):
            add_bench(mb, xx, yy, 0.0)
    return mb.finish(COL_DETAIL, {"campusRole": "benches"})


def add_car(mb, x, y, angle, color_mat):
    ca, sa = math.cos(angle), math.sin(angle)
    def rot(px, py):
        return (x + px * ca - py * sa, y + px * sa + py * ca)
    p0 = rot(-0.23, -0.10)
    p1 = rot(0.23, 0.10)
    mb.box(min(p0[0], p1[0]), max(p0[0], p1[0]), min(p0[1], p1[1]), max(p0[1], p1[1]),
           0.03, 0.12, color_mat)
    p0 = rot(-0.10, -0.09)
    p1 = rot(0.12, 0.09)
    mb.box(min(p0[0], p1[0]), max(p0[0], p1[0]), min(p0[1], p1[1]), max(p0[1], p1[1]),
           0.12, 0.20, "M_GlassDark")
    for sx in (-0.15, 0.15):
        for sy in (-0.10, 0.10):
            p = rot(sx, sy)
            mb.cylinder(p[0], p[1], 0.01, 0.035, 0.032, "M_Metal", 8, cap=True, smooth=True)


def build_cars():
    mb = MeshBuilder("ParkedCars")
    rect = None
    for plaza in L.PLAZAS:
        if plaza["style"] == "parking":
            rect = plaza["rect"]
    if rect is None:
        return None
    xmin, xmax, ymin, ymax = L.rect_bounds(rect)
    colors = ["M_Car1", "M_Car2", "M_Car3", "M_Car4"]
    rng = random.Random(77)
    for row_i, row_y in enumerate((ymin + (ymax - ymin) * 0.30, ymin + (ymax - ymin) * 0.70)):
        for i in range(10):
            if rng.random() < 0.28:
                continue
            x = xmin + 0.75 + i * 0.62
            add_car(mb, x, row_y, math.pi / 2 if row_i else -math.pi / 2,
                    colors[(i + row_i) % len(colors)])
    return mb.finish(COL_DETAIL, {"campusRole": "cars"})


def make_world():
    world = bpy.data.worlds.new("CampusWorld")
    world.use_nodes = True
    nodes = world.node_tree.nodes
    links = world.node_tree.links
    for node in list(nodes):
        nodes.remove(node)
    out = nodes.new("ShaderNodeOutputWorld")
    bg = nodes.new("ShaderNodeBackground")
    bg.inputs["Color"].default_value = (*hx("#BBD6E4"), 1)
    bg.inputs["Strength"].default_value = 1.0
    links.new(bg.outputs["Background"], out.inputs["Surface"])
    bpy.context.scene.world = world
    mb = MeshBuilder("RenderSky")
    verts = []
    seg = 48
    ring = 8
    for ri in range(ring + 1):
        phi = math.pi * 0.5 * ri / ring
        z = 18 + 62 * math.sin(phi)
        r = 90 * math.cos(phi) + 8
        for k in range(seg):
            a = 2 * math.pi * k / seg
            verts.append((r * math.cos(a), r * math.sin(a), z))
    for i in range(ring):
        for k in range(seg):
            j = (k + 1) % seg
            a = i * seg + k
            b = i * seg + j
            c = (i + 1) * seg + j
            d = (i + 1) * seg + k
            mb.quad(verts[a], verts[b], verts[c], verts[d], "M_Sky")
    obj = mb.finish(COL_RENDER, {"campusRole": "renderOnly"})
    if obj:
        obj.visible_shadow = False


def build_render_sky_material():
    mat = bpy.data.materials.new("M_Sky")
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    links = mat.node_tree.links
    for n in list(nodes):
        nodes.remove(n)
    out = nodes.new("ShaderNodeOutputMaterial")
    tex = nodes.new("ShaderNodeTexCoord")
    sep = nodes.new("ShaderNodeSeparateXYZ")
    ramp = nodes.new("ShaderNodeValToRGB")
    emit = nodes.new("ShaderNodeEmission")
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = (*hx("#F6E4C8"), 1)
    ramp.color_ramp.elements[1].position = 0.65
    ramp.color_ramp.elements[1].color = (*hx("#8FBAD6"), 1)
    e = ramp.color_ramp.elements.new(0.30)
    e.color = (*hx("#D9E7E8"), 1)
    emit.inputs["Strength"].default_value = 1.0
    links.new(tex.outputs["Generated"], sep.inputs["Vector"])
    links.new(sep.outputs["Z"], ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], emit.inputs["Color"])
    links.new(emit.outputs["Emission"], out.inputs["Surface"])
    MATERIALS["M_Sky"] = mat
    return mat


def setup_lights():
    sun_data = bpy.data.lights.new("Sun", type="SUN")
    sun_data.energy = 3.2
    sun_data.angle = math.radians(3.0)
    sun_data.color = hx("#FFE9C9")
    sun = bpy.data.objects.new("Sun", sun_data)
    COL_RENDER.objects.link(sun)
    sun.rotation_euler = Euler((math.radians(50), math.radians(0), math.radians(-38)), "XYZ")

    fill_data = bpy.data.lights.new("Fill", type="AREA")
    fill_data.energy = 1800
    fill_data.size = 26
    fill_data.color = hx("#CFE7F0")
    fill = bpy.data.objects.new("Fill", fill_data)
    COL_RENDER.objects.link(fill)
    fill.location = (18, -20, 22)
    fill.rotation_euler = Euler((math.radians(55), 0, math.radians(40)), "XYZ")

    rim_data = bpy.data.lights.new("Rim", type="AREA")
    rim_data.energy = 1200
    rim_data.size = 18
    rim_data.color = hx("#FFD9A8")
    rim = bpy.data.objects.new("Rim", rim_data)
    COL_RENDER.objects.link(rim)
    rim.location = (-24, 22, 16)
    rim.rotation_euler = Euler((math.radians(66), 0, math.radians(-130)), "XYZ")


def make_camera(name, loc, target, lens=45, ortho=None):
    data = bpy.data.cameras.new(name)
    if ortho:
        data.type = "ORTHO"
        data.ortho_scale = ortho
    else:
        data.lens = lens
    data.clip_start = 0.1
    data.clip_end = 400
    cam = bpy.data.objects.new(name, data)
    COL_RENDER.objects.link(cam)
    cam.location = loc
    direction = Vector(target) - Vector(loc)
    cam.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
    return cam


CAMERAS = []


def setup_cameras():
    CAMERAS.clear()
    CAMERAS.append(("01_overview_hero", make_camera(
        "Cam_Hero", (29.0, -27.0, 21.0), (0.0, 0.2, 0.4), lens=52)))
    CAMERAS.append(("02_overview_top", make_camera(
        "Cam_Top", (0.0, 0.0, 58.0), (0.0, 0.0, 0.0), ortho=51.7)))
    CAMERAS.append(("03_north_dorms", make_camera(
        "Cam_Dorms", (-13.5, -9.5, 7.2), (-13.0, 5.0, 1.15), lens=44)))
    CAMERAS.append(("04_teaching_cluster", make_camera(
        "Cam_Teaching", (2.0, -5.5, 8.0), (7.2, 7.5, 1.4), lens=46)))
    CAMERAS.append(("05_gate_plaza", make_camera(
        "Cam_Gate", (23.5, -21.0, 9.5), (15.0, -2.0, 0.9), lens=42)))
    CAMERAS.append(("06_south_grid", make_camera(
        "Cam_South", (1.0, -30.0, 11.5), (-0.5, -10.5, 0.9), lens=46)))


def setup_render():
    scene = bpy.context.scene
    try:
        scene.render.engine = "BLENDER_EEVEE_NEXT"
    except Exception:
        scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = 1600
    scene.render.resolution_y = 1000
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False
    scene.render.use_file_extension = True
    try:
        scene.eevee.taa_render_samples = 64
    except Exception:
        pass
    try:
        scene.eevee.use_raytracing = True
    except Exception:
        pass
    try:
        scene.eevee.use_shadows = True
    except Exception:
        pass
    try:
        scene.view_settings.look = "AgX - Medium High Contrast"
    except Exception:
        pass
    scene.view_settings.exposure = 0.35


def render_views():
    scene = bpy.context.scene
    os.makedirs(RENDER_DIR, exist_ok=True)
    for name, cam in CAMERAS:
        scene.camera = cam
        if name == "02_overview_top":
            scene.render.resolution_x = 1706
            scene.render.resolution_y = 1320
        else:
            scene.render.resolution_x = 1600
            scene.render.resolution_y = 1000
        scene.render.filepath = os.path.join(RENDER_DIR, name + ".png")
        t0 = time.time()
        bpy.ops.render.render(write_still=True)
        print(f"[render] {name} in {time.time() - t0:.1f}s")


def export_glb():
    os.makedirs(os.path.dirname(GLB_PATH), exist_ok=True)
    bpy.ops.object.select_all(action="DESELECT")
    for col in (COL_GROUND, COL_ROADS, COL_BUILDINGS, COL_LAND, COL_DETAIL):
        for obj in col.objects:
            obj.select_set(True)
    bpy.ops.export_scene.gltf(
        filepath=GLB_PATH,
        export_format="GLB",
        use_selection=True,
        export_apply=True,
        export_extras=True,
        export_cameras=False,
        export_lights=False,
        export_yup=True,
        export_materials="EXPORT",
    )
    print(f"[export] {GLB_PATH} ({os.path.getsize(GLB_PATH) / 1024 / 1024:.2f} MB)")


def save_blend():
    os.makedirs(os.path.dirname(BLEND_PATH), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=BLEND_PATH)
    print(f"[save] {BLEND_PATH}")


def main():
    clear_scene()
    setup_collections()
    build_materials()
    build_render_sky_material()
    build_ground()
    build_plazas()
    build_round_plazas()
    build_field()
    build_roads()
    build_crosswalks()
    build_all_buildings()
    build_gates()
    build_trees()
    build_lamps()
    build_benches()
    build_cars()
    make_world()
    setup_lights()
    setup_cameras()
    setup_render()

    triangles = 0
    for obj in bpy.data.objects:
        if obj.type == "MESH":
            obj.data.calc_loop_triangles()
            triangles += len(obj.data.loop_triangles)
    print(f"[stats] objects={len(bpy.data.objects)} triangles={triangles}")

    export_glb()
    save_blend()
    render_views()
    save_blend()
    print(f"[done] total {time.time() - START:.1f}s")


if __name__ == "__main__":
    main()



