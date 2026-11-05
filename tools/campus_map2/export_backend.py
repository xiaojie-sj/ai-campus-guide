# -*- coding: utf-8 -*-
"""Generate V8 SQL + layout manifest from the canonical map2 layout."""
import io, json, math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import layout as L

ROOT = os.path.dirname(os.path.dirname(HERE))
SQL_PATH = os.path.join(ROOT, "backend", "src", "main", "resources", "db",
                        "migration", "postgresql", "V8__map2_campus_alignment.sql")
MANIFEST = os.path.join(ROOT, "docs", "03-modeling", "campus-layout.json")


def q(value):
    return "'" + str(value).replace("'", "''") + "'"


def f(value):
    return f"{value:.4f}"


POINTS = [
    dict(id=1, name="北门", category="GATE", px=1296, py=712, height=1.2,
         color="#2F6B63", aliases="北门,北校门", road="east_axis_gate",
         desc="地图2标注的北门，位于学院路南侧、东纵主路北端，门内为北门广场。"),
    dict(id=2, name="东门", category="GATE", px=1600, py=1080, height=1.2,
         color="#2F6B63", aliases="东门,东校门", road="east_gate_in",
         desc="地图2标注的东门，开向航空路，门内连接同大广场东侧。"),
    dict(id=3, name="科技楼", category="TEACHING", px=1006, py=482, height=2.42,
         color="#6A7F57", aliases="科技楼,科技大楼", road="tech_plaza",
         desc="地图2标注的科技楼，位于生物楼南侧、化学楼西侧，采用中部塔楼与两翼退台体量。"),
    dict(id=4, name="生物楼", category="LAB", px=1022, py=179, height=1.5,
         color="#6A8C72", aliases="生物楼,生科楼,生命科学楼", road="bio_front_e",
         desc="地图2标注的生物楼，位于校园东北角，回字形院落，南侧为凹入式主入口。"),
    dict(id=5, name="综合楼", category="ADMIN", px=1463, py=799, height=1.9,
         color="#936B4F", aliases="综合楼,同大综合楼", road="complex_front",
         desc="地图2标注的综合楼，位于北门东南侧，主楼与东西两翼围合出朝北内院。"),
    dict(id=6, name="化学楼", category="LAB", px=1486, py=503, height=1.5,
         color="#6A8C72", aliases="化学楼,化学院", road="tech_north_e",
         desc="地图2标注的化学楼，位于科技楼东侧，西侧以低层连廊与科技楼片区相连。"),
    dict(id=7, name="同大广场", category="PLAZA", px=1255, py=1035, height=0.2,
         color="#B9C9A8", aliases="同大广场,中心广场", road="east_axis_plaza",
         desc="地图2标注的同大广场，位于综合楼南侧、东门西侧，为校园东南片区的核心铺装广场。"),
    dict(id=8, name="运动场", category="SPORTS", px=316, py=972, height=0.2,
         color="#4D7C6A", aliases="运动场,操场,大草坪", road="south_north_w",
         desc="地图2西南侧的大面积运动场/草坪，图上为整片浅绿色，未绘制跑道。"),
    dict(id=9, name="中通快递", category="SERVICE", px=1558, py=941, height=1.12,
         color="#7B6755", aliases="中通快递,快递点,菜鸟", road="complex_front",
         desc="地图2标注的中通快递，位于综合楼东南侧，为南北向长条服务楼。"),
    dict(id=10, name="招生就业工作处", category="SERVICE", px=1169, py=835, height=1.3,
         color="#7B6755", aliases="招生就业工作处,招生办,就业处", road="career_gate",
         desc="地图2标注的招生就业工作处，位于北门西南侧，为南北向 L 形行政楼。"),
]

# Buildings that should exist in the backend fallback geometry. The GLB contains
# the complete, fully detailed set; this table keeps labels, footprints and
# spatial search consistent for the named / representative volumes.
BUILDING_KEYS = [
    "d14", "d13", "d12", "d11", "d10", "d9", "d8", "d7", "d6", "d5", "d4",
    "d1", "d2", "d3", "bio", "tech", "chem", "complex", "career", "express",
    "north_gatehouse", "south_hall", "s10", "s21", "s25",
]

ROAD_NODES = {
    "baocheng_n": (140, 20),
    "xueyuan_w": (120, 630),
    "south_ring_w": (46, 1262),
    "baocheng_s": (38, 1345),
    "hangkong_n": (1645, 20),
    "xueyuan_east": (1626, 688),
    "hangkong_w": (1645, 1098),
    "hangkong_s": (1622, 1310),
    "xueyuan_b": (327, 642),
    "xueyuan_c": (516, 650),
    "xueyuan_d": (792, 654),
    "xueyuan_e": (1040, 664),
    "xueyuan_f": (1296, 682),
    "north_west_top": (335, 172),
    "north_mid_top": (523, 184),
    "north_east_top": (796, 24),
    "bio_front_w": (800, 298),
    "bio_gate": (1000, 301),
    "bio_front_e": (1196, 305),
    "tech_north_w": (800, 394),
    "tech_plaza": (1000, 400),
    "tech_north_e": (1372, 405),
    "south_north_w": (455, 712),
    "south_north_1": (680, 716),
    "south_north_2": (880, 718),
    "south_north_e": (1296, 722),
    "sw_v_1": (462, 900),
    "sw_v_2": (456, 1017),
    "sw_v_3": (456, 1272),
    "dorm_north_w": (458, 778),
    "dorm_north_1": (680, 780),
    "dorm_north_2": (880, 781),
    "dorm_north_e": (1062, 783),
    "dorm_mid_w": (456, 1017),
    "dorm_mid_1": (678, 1020),
    "dorm_mid_2": (878, 1021),
    "lane1_s": (676, 1230),
    "lane2_s": (876, 1230),
    "east_axis_gate": (1296, 712),
    "east_axis_plaza": (1296, 946),
    "east_axis_south": (1296, 1290),
    "plaza_w": (1090, 1040),
    "plaza_e": (1420, 1100),
    "east_gate_in": (1608, 1098),
    "complex_front": (1400, 860),
    "career_gate": (1169, 858),
    "south_ring_m": (800, 1289),
}

ROAD_EDGES = [
    ("baocheng_n", "xueyuan_w"), ("xueyuan_w", "south_ring_w"),
    ("south_ring_w", "baocheng_s"),
    ("hangkong_n", "xueyuan_east"), ("xueyuan_east", "hangkong_w"),
    ("hangkong_w", "hangkong_s"),
    ("xueyuan_w", "xueyuan_b"), ("xueyuan_b", "xueyuan_c"),
    ("xueyuan_c", "xueyuan_d"), ("xueyuan_d", "xueyuan_e"),
    ("xueyuan_e", "xueyuan_f"), ("xueyuan_f", "xueyuan_east"),
    ("north_west_top", "xueyuan_b"), ("north_mid_top", "xueyuan_c"),
    ("north_east_top", "xueyuan_d"),
    ("xueyuan_d", "bio_front_w"), ("bio_front_w", "bio_gate"),
    ("bio_gate", "bio_front_e"), ("bio_front_w", "tech_north_w"),
    ("tech_north_w", "tech_plaza"), ("tech_plaza", "tech_north_e"),
    ("xueyuan_w", "south_north_w"), ("south_north_w", "south_north_1"),
    ("south_north_1", "south_north_2"), ("south_north_2", "south_north_e"),
    ("south_north_w", "sw_v_1"), ("sw_v_1", "sw_v_2"), ("sw_v_2", "sw_v_3"),
    ("south_ring_w", "sw_v_3"), ("sw_v_3", "south_ring_m"),
    ("south_north_w", "dorm_north_w"), ("dorm_north_w", "dorm_north_1"),
    ("dorm_north_1", "dorm_north_2"), ("dorm_north_2", "dorm_north_e"),
    ("south_north_1", "dorm_north_1"), ("dorm_north_1", "dorm_mid_1"),
    ("dorm_mid_1", "lane1_s"),
    ("south_north_2", "dorm_north_2"), ("dorm_north_2", "dorm_mid_2"),
    ("dorm_mid_2", "lane2_s"),
    ("dorm_north_w", "dorm_mid_w"), ("dorm_mid_w", "dorm_mid_1"),
    ("dorm_mid_1", "dorm_mid_2"),
    ("xueyuan_f", "east_axis_gate"), ("east_axis_gate", "south_north_e"),
    ("south_north_e", "east_axis_plaza"), ("east_axis_plaza", "east_axis_south"),
    ("east_axis_south", "south_ring_m"),
    ("east_axis_plaza", "plaza_w"), ("east_axis_plaza", "plaza_e"),
    ("plaza_e", "east_gate_in"), ("east_gate_in", "hangkong_w"),
    ("east_axis_gate", "complex_front"), ("complex_front", "career_gate"),
    ("east_axis_south", "hangkong_s"),
]


def building_polygon(spec):
    xs, ys = [], []
    for item in spec["blocks"]:
        x0, y0, x1, y1 = item[0]
        xs += [x0, x1]
        ys += [y0, y1]
    return [(L.wx(min(xs)), L.wz(min(ys))), (L.wx(max(xs)), L.wz(min(ys))),
            (L.wx(max(xs)), L.wz(max(ys))), (L.wx(min(xs)), L.wz(max(ys)))]


def height_of(spec):
    fh = spec["floor_h"] or (0.255 if spec["style"] == "dorm" else 0.30)
    return 0.10 + max(item[1] for item in spec["blocks"]) * fh


def main():
    specs = {s["key"]: s for s in L.BUILDINGS}
    lines = []
    a = lines.append
    a("-- V8: align points, fallback footprints and the walkable road graph with 地图2.png")
    a("-- Generated by tools/campus_map2/export_backend.py. Do not hand-edit.")
    a("")
    a("UPDATE campus_point SET road_node_id = NULL;")
    a("DELETE FROM campus_road_geometry;")
    a("DELETE FROM road_edge;")
    a("DELETE FROM road_node;")
    a("DELETE FROM campus_building;")
    a("")
    # campus_point rows 1..10 already exist in seed data and are referenced by
    # tour_stop. Update in place, and only insert when an id is missing so the
    # migration stays re-runnable without breaking foreign keys.
    for p in POINTS:
        x, z = L.wx(p["px"]), L.wz(p["py"])
        vals = dict(name=q(p["name"]), aliases=q(p["aliases"]), cat=q(p["category"]),
                    desc=q(p["desc"]), x=f(x), z=f(z), h=f(p["height"]),
                    color=q(p["color"]),
                    model=q("GATE" if p["category"] == "GATE" else "BLOCK"),
                    src=q("地图2.png 标注"), sort=100 + p["id"] * 10)
        a("UPDATE campus_point SET")
        a("    name = {name}, aliases = {aliases}, category = {cat},".format(**vals))
        a("    description = {desc}, x = {x}, z = {z},".format(**vals))
        a("    building_height = {h}, color_hex = {color},".format(**vals))
        a("    model_key = {model}, road_node_id = NULL,".format(**vals))
        a("    status = 'PUBLISHED', verification_status = 'MAP_2_DIGITIZED',")
        a("    source_title = {src}, source_url = NULL, sort_order = {sort},".format(**vals))
        a("    updated_at = CURRENT_TIMESTAMP")
        a("WHERE id = {};".format(p["id"]))
        a("INSERT INTO campus_point")
        a("    (id, name, aliases, category, description, x, z, building_height, color_hex,")
        a("     model_key, road_node_id, status, verification_status, source_title, source_url,")
        a("     sort_order, updated_at)")
        a("SELECT {id}, {name}, {aliases}, {cat}, {desc}, {x}, {z}, {h}, {color},".format(id=p["id"], **vals))
        a("       {model}, NULL, 'PUBLISHED', 'MAP_2_DIGITIZED', {src}, NULL,".format(**vals))
        a("       {sort}, CURRENT_TIMESTAMP".format(**vals))
        a("WHERE NOT EXISTS (SELECT 1 FROM campus_point WHERE id = {});".format(p["id"]))
    a("")
    a("-- Building footprints are the trustworthy fallback used for labels and spatial search.")
    a("INSERT INTO campus_building")
    a("    (id, point_id, name, category, height, color_hex, footprint_json, geom, status, sort_order)")
    a("VALUES")
    point_by_key = {"bio": 4, "tech": 3, "complex": 5, "career": 10, "express": 9,
                    "north_gatehouse": 1}
    rows = []
    base_id = 101
    for idx, key in enumerate(BUILDING_KEYS):
        spec = specs[key]
        poly = building_polygon(spec)
        coords = [[round(x, 4), round(z, 4)] for x, z in poly]
        coords.append(coords[0])
        geo = json.dumps({"type": "Polygon", "coordinates": [coords]}, ensure_ascii=False)
        footprint = json.dumps(coords[:-1], ensure_ascii=False)
        rows.append(
            "    ({id}, {pid}, {name}, {cat}, {h}, {color}, {fp}::jsonb,\n"
            "     ST_SetSRID(ST_GeomFromGeoJSON({geo}), 0), 'PUBLISHED', {sort})"
            .format(id=base_id + idx, pid=point_by_key.get(key, "NULL"), name=q(spec["name"]),
                    cat=q(spec["category"]), h=f(height_of(spec)),
                    color=q("#6D8E7D" if spec["category"] == "DORM" else "#6A8C72"),
                    fp=q(footprint), geo=q(geo), sort=1000 + idx * 10))
    a(",\n".join(rows) + ";")
    a("")
    a("INSERT INTO road_node (id, name, x, z, enabled) VALUES")
    rows = []
    node_ids = {}
    for idx, (name, (px, py)) in enumerate(ROAD_NODES.items()):
        nid = 101 + idx
        node_ids[name] = nid
        rows.append("    ({}, {}, {}, {}, TRUE)".format(nid, q(name), f(L.wx(px)), f(L.wz(py))))
    a(",\n".join(rows) + ";")
    a("")
    a("INSERT INTO road_edge (id, from_node_id, to_node_id, distance, walkable, enabled) VALUES")
    rows = []
    for idx, (a_name, b_name) in enumerate(ROAD_EDGES):
        ax, az = ROAD_NODES[a_name]
        bx, bz = ROAD_NODES[b_name]
        dist = math.hypot(L.wx(ax) - L.wx(bx), L.wz(az) - L.wz(bz))
        rows.append("    ({}, {}, {}, {}, TRUE, TRUE)".format(301 + idx, node_ids[a_name],
                                                               node_ids[b_name], f(max(dist, 0.1))))
    a(",\n".join(rows) + ";")
    a("")
    a("INSERT INTO campus_road_geometry (edge_id, geom, source, target, cost, reverse_cost)")
    a("SELECT edge.id,")
    a("       ST_SetSRID(ST_MakeLine(ST_MakePoint(src.x, src.z), ST_MakePoint(dst.x, dst.z)), 0),")
    a("       edge.from_node_id, edge.to_node_id,")
    a("       GREATEST(edge.distance, 0.1), GREATEST(edge.distance, 0.1)")
    a("FROM road_edge edge")
    a("JOIN road_node src ON src.id = edge.from_node_id")
    a("JOIN road_node dst ON dst.id = edge.to_node_id")
    a("WHERE edge.id >= 301;")
    a("")
    for p in POINTS:
        a("UPDATE campus_point SET road_node_id = {} WHERE id = {};".format(
            node_ids[p["road"]], p["id"]))
    a("")
    a("-- Camera presets follow the new campus extents.")
    a("DELETE FROM camera_preset;")
    a("INSERT INTO camera_preset")
    a("    (id, preset_key, name, point_id, x, y, z, target_x, target_y, target_z, public_visible, status, sort_order)")
    a("VALUES")
    a("    (1, 'gate', '北门全景', 1, 20.0, 12.0, 16.0, 13.4, 1.0, 1.6, TRUE, 'PUBLISHED', 10),")
    a("    (2, 'biology', '生物楼外观', 4, 12.0, 10.0, -6.0, 5.1, 1.2, -14.6, TRUE, 'PUBLISHED', 20),")
    a("    (3, 'teaching', '科技楼与化学楼', 3, 11.0, 11.0, 2.0, 8.0, 1.4, -5.0, TRUE, 'PUBLISHED', 30),")
    a("    (4, 'sports', '运动场鸟瞰', 8, -16.0, 15.0, 20.0, -16.0, 0.5, 9.5, TRUE, 'PUBLISHED', 40),")
    a("    (5, 'plaza', '同大广场与东门', 7, 26.0, 13.0, 22.0, 15.0, 1.0, 11.0, TRUE, 'PUBLISHED', 50),")
    a("    (6, 'overview', '校园总览', NULL, 0.0, 52.0, 58.0, 0.0, 0.0, 0.0, TRUE, 'PUBLISHED', 60);")
    a("")
    a("-- Keep the demo tours coherent with the renamed points.")
    a("UPDATE tour_route SET name = '北门—综合楼—同大广场' WHERE id = 1;")
    a("UPDATE tour_route SET name = '教学科研线' WHERE id = 2;")
    a("UPDATE tour_route SET name = '生活服务线' WHERE id = 3;")
    a("UPDATE tour_stop SET point_id = 1, narration = '从北门进入校园，沿东纵主路向南前往综合楼片区。' WHERE id = 1;")
    a("UPDATE tour_stop SET point_id = 5, narration = '综合楼位于北门东南侧，主楼与东西两翼围合出朝北内院。' WHERE id = 2;")
    a("UPDATE tour_stop SET point_id = 7, narration = '同大广场是校园东南片区的核心铺装广场，东侧连接东门。' WHERE id = 3;")
    a("UPDATE tour_stop SET point_id = 9, narration = '中通快递位于综合楼东南侧，是校园生活服务节点。' WHERE id = 4;")
    a("UPDATE tour_stop SET point_id = 1, narration = '校园导览从北门开始。' WHERE id = 5;")
    a("UPDATE tour_stop SET point_id = 4, narration = '生物楼位于校园东北角，回字形院落与南侧凹入式入口是其显著特征。' WHERE id = 6;")
    a("UPDATE tour_stop SET point_id = 3, narration = '科技楼位于生物楼南侧，中部塔楼与两翼退台形成层次。' WHERE id = 7;")
    a("UPDATE tour_stop SET point_id = 8, narration = '运动场位于校园西南侧，是图中唯一的大面积绿地。' WHERE id = 8;")
    a("UPDATE tour_stop SET point_id = 7, narration = '从同大广场开始生活服务线。' WHERE id = 9;")
    a("UPDATE tour_stop SET point_id = 10, narration = '招生就业工作处位于北门西南侧，为 L 形行政楼。' WHERE id = 10;")
    a("UPDATE tour_stop SET point_id = 6, narration = '化学楼位于科技楼东侧，以低层连廊与科技楼片区相连。' WHERE id = 11;")
    a("")
    a("SELECT setval(pg_get_serial_sequence('campus_point', 'id'), (SELECT MAX(id) FROM campus_point), true);")
    a("SELECT setval(pg_get_serial_sequence('campus_building', 'id'), (SELECT MAX(id) FROM campus_building), true);")
    a("SELECT setval(pg_get_serial_sequence('road_node', 'id'), (SELECT MAX(id) FROM road_node), true);")
    a("SELECT setval(pg_get_serial_sequence('road_edge', 'id'), (SELECT MAX(id) FROM road_edge), true);")
    a("SELECT setval(pg_get_serial_sequence('camera_preset', 'id'), (SELECT MAX(id) FROM camera_preset), true);")
    a("")
    sql = "\n".join(lines)
    io.open(SQL_PATH, "w", encoding="utf-8", newline="\n").write(sql)
    print("SQL", SQL_PATH, len(sql))

    manifest = {
        "source": "地图2.png",
        "pixelScale": L.PX_PER_UNIT,
        "originPx": [L.ORIGIN_X, L.ORIGIN_Y],
        "points": [{"id": p["id"], "name": p["name"], "x": round(L.wx(p["px"]), 4),
                    "z": round(L.wz(p["py"]), 4)} for p in POINTS],
        "roads": L.ROADS,
        "buildings": [{"key": s["key"], "name": s["name"], "category": s["category"],
                       "blocks": [list(b[0]) for b in s["blocks"]]} for s in L.BUILDINGS],
    }
    io.open(MANIFEST, "w", encoding="utf-8", newline="\n").write(
        json.dumps(manifest, ensure_ascii=False, indent=2))
    print("manifest", MANIFEST)


if __name__ == "__main__":
    main()
