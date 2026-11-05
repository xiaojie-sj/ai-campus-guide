"""
Canonical layout for 地图2.png (1706 x 1320).

Coordinate conventions
---------------------
* Source pixels: origin at top-left, +px east, +py south.
* Blender:  +X east, +Y north, +Z up.
* Three.js / backend: +X east, +Z south, +Y up.

A single pixel scale is used everywhere, so the GLB, the web scene and the
generated SQL all describe the same campus.
"""

PX_PER_UNIT = 33.0
ORIGIN_X = 853.0
ORIGIN_Y = 660.0


def wx(px):
    """Map pixel X -> world X (east positive)."""
    return (float(px) - ORIGIN_X) / PX_PER_UNIT


def wz(py):
    """Map pixel Y -> world Z (south positive), i.e. backend / three.js Z."""
    return (float(py) - ORIGIN_Y) / PX_PER_UNIT


def by(py):
    """Map pixel Y -> Blender Y (north positive)."""
    return -wz(py)


def rect_bounds(rect):
    """Return (xmin, xmax, ymin, ymax) in Blender coordinates."""
    x0, y0, x1, y1 = rect
    xa, xb = wx(x0), wx(x1)
    ya, yb = by(y1), by(y0)  # py grows south -> Blender Y decreases
    return (min(xa, xb), max(xa, xb), min(ya, yb), max(ya, yb))


def rect_center(rect):
    x0, y0, x1, y1 = rect
    return (wx((x0 + x1) / 2.0), by((y0 + y1) / 2.0))


# ---------------------------------------------------------------------------
# Buildings
# ---------------------------------------------------------------------------
# blocks: list of ((x0, y0, x1, y1), floors[, floor_height])
# style:  dorm | dorm_tower | lab | tech | chem | admin | hall | service | utility | gate
# category: DORM | LAB | TEACHING | ADMIN | SERVICE | UTILITY | GATE

def _b(key, name, blocks, style, category, point_id=None, note="", floors=None,
       floor_h=None, entrance=None, courtyard=None, roof=None):
    return {
        "key": key,
        "name": name,
        "blocks": blocks,
        "style": style,
        "category": category,
        "point_id": point_id,
        "note": note,
        "default_floors": floors,
        "floor_h": floor_h,
        "entrance": entrance,
        "courtyard": courtyard,
        "roof": roof or {},
    }


BUILDINGS = [
    # ---- North dormitory rows (the numbers printed on the map) -------------
    _b("d14", "14栋", [((567, 41, 758, 99), 6)], "dorm", "DORM", 114),
    _b("d13", "13栋", [((566, 141, 756, 193), 6)], "dorm", "DORM", 113),
    _b("d12", "12栋", [((545, 237, 741, 282), 6)], "dorm", "DORM", 112),
    _b("d11", "11栋", [((352, 196, 482, 248), 6)], "dorm", "DORM", 111),
    _b("d10", "10栋", [((163, 221, 284, 263), 6)], "dorm", "DORM", 110),
    _b("d9", "9栋", [((543, 330, 746, 379), 6)], "dorm", "DORM", 109),
    _b("d8", "8栋", [((353, 306, 499, 355), 6)], "dorm", "DORM", 108),
    _b("d7", "7栋", [((170, 330, 300, 372), 6)], "dorm", "DORM", 107),
    _b("d6", "6栋", [((542, 428, 745, 482), 6)], "dorm", "DORM", 106),
    _b("d5", "5栋", [((343, 415, 506, 457), 6)], "dorm", "DORM", 105),
    _b("d4", "4栋", [((160, 428, 297, 470), 6)], "dorm", "DORM", 104),
    _b("d1", "1栋", [((210, 528, 294, 565), 6), ((326, 528, 370, 565), 6)], "dorm", "DORM", 101),
    _b("d2", "2栋", [((462, 533, 589, 574), 6)], "dorm", "DORM", 102),
    _b("d3", "3栋", [((616, 532, 732, 575), 6)], "dorm", "DORM", 103),

    # ---- North teaching / research cluster --------------------------------
    _b("bio", "生物楼", [
        ((851, 78, 1193, 120), 5),
        ((851, 120, 925, 280), 5),
        ((1120, 120, 1193, 280), 5),
        ((851, 232, 965, 280), 4),
        ((1065, 232, 1193, 280), 4),
        ((995, 120, 1045, 232), 3),
        ((985, 248, 1045, 280), 2),
    ], "lab", "LAB", 4, floor_h=0.28, entrance=((1015, 280), "south"),
       courtyard=(925, 120, 1120, 230), roof={"stacks": 3}),
    _b("tech", "科技楼", [
        ((845, 400, 1167, 565), 6),
        ((940, 430, 1075, 540), 8),
        ((845, 440, 905, 555), 4),
        ((1105, 420, 1167, 555), 5),
    ], "tech", "TEACHING", 3, floor_h=0.29, entrance=((1000, 565), "south"),
       roof={"hvac": 4, "tower": True}),
    _b("chem", "化学楼", [
        ((1374, 429, 1599, 578), 5),
        ((1351, 429, 1374, 551), 2),
    ], "chem", "LAB", 9, floor_h=0.28, entrance=((1485, 578), "south"),
       roof={"stacks": 4, "hvac": 2}),

    # ---- Central band (south of 学院路) -----------------------------------
    _b("south_hall", "南区1栋", [((492, 704, 667, 781), 2)], "hall", "DORM", 201,
       floors=2, floor_h=0.55, entrance=((580, 781), "south")),
    _b("career", "招生就业工作处", [
        ((1082, 728, 1200, 943), 4),
        ((1200, 790, 1257, 900), 3),
    ], "admin", "SERVICE", 10, floor_h=0.30, entrance=((1140, 943), "south")),
    _b("complex", "综合楼", [
        ((1347, 749, 1552, 775), 6),
        ((1347, 775, 1395, 848), 6),
        ((1505, 775, 1552, 848), 6),
        ((1395, 820, 1505, 848), 6),
        ((1316, 749, 1347, 848), 5),
        ((1552, 756, 1580, 849), 5),
    ], "admin", "ADMIN", 5, floor_h=0.30, entrance=((1450, 848), "south"),
       courtyard=(1395, 775, 1505, 820)),
    _b("express", "中通快递", [((1512, 868, 1605, 1015), 3), ((1503, 1021, 1614, 1040), 1, 0.32)], "service", "SERVICE", 13,
       floor_h=0.34, entrance=((1512, 940), "west"), roof={"hvac": 1}),
    _b("north_gatehouse", "北门", [((1230, 681, 1277, 742), 1)], "gate", "GATE", 1,
       floors=1, floor_h=0.62, entrance=((1253, 742), "south")),

    # ---- South dormitory grid --------------------------------------------
    _b("s01", "南区2栋", [((491, 787, 658, 826), 6)], "dorm", "DORM", 202),
    _b("s02", "南区3栋", [((693, 787, 864, 834), 6)], "dorm", "DORM", 203),
    _b("s03", "南区4栋", [((894, 793, 1041, 840), 6)], "dorm", "DORM", 204),
    _b("s04", "南区5栋", [((492, 885, 661, 926), 6)], "dorm", "DORM", 205),
    _b("s05", "南区6栋", [((696, 882, 865, 925), 6)], "dorm", "DORM", 206),
    _b("s06", "南区7栋", [((911, 889, 1045, 926), 6)], "dorm", "DORM", 207),
    _b("s07", "南区8栋", [((483, 932, 658, 989), 2)], "utility", "DORM", 208,
       floors=2, floor_h=0.34),
    _b("s08", "南区9栋", [((687, 931, 864, 990), 2)], "utility", "DORM", 209,
       floors=2, floor_h=0.34),
    _b("s09", "南区10栋", [((902, 933, 1092, 1010), 2)], "utility", "DORM", 210,
       floors=2, floor_h=0.34),
    _b("s10", "南区11栋", [((705, 1088, 842, 1138), 6)], "dorm", "DORM", 211),
    _b("s11", "南区12栋", [((877, 1093, 1053, 1135), 6)], "dorm", "DORM", 212),
    _b("s12", "南区13栋", [((465, 1155, 519, 1260), 1)], "utility", "DORM", 213,
       floors=1, floor_h=0.55),
    _b("s13", "南区配套用房", [((1059, 1100, 1114, 1133), 1)], "utility", "DORM", 214,
       floors=1, floor_h=0.5),
    _b("s13a", "南区13A栋", [((561, 1162, 647, 1215), 6)], "dorm", "DORM", 227),
    _b("s14", "南区15栋", [((708, 1181, 829, 1216), 6)], "dorm", "DORM", 215),
    _b("s15", "南区16栋", [((875, 1174, 1018, 1215), 6)], "dorm", "DORM", 216),
    _b("s16", "南区17栋", [((1085, 1184, 1233, 1226), 6)], "dorm", "DORM", 217),
    _b("s17", "南区18栋", [((1275, 1189, 1414, 1225), 6)], "dorm", "DORM", 218),
    _b("s18", "南区19栋", [((1451, 1191, 1587, 1227), 6)], "dorm", "DORM", 219),
    _b("s19", "南区20栋", [((544, 1218, 652, 1277), 6)], "dorm", "DORM", 220),
    _b("s20", "南区21栋", [((703, 1222, 846, 1286), 6)], "dorm", "DORM", 221),
    _b("s21", "南区22栋", [((875, 1222, 1052, 1295), 6)], "dorm", "DORM", 222),
    _b("s22", "南区23栋", [((1063, 1233, 1246, 1314), 6)], "dorm", "DORM", 223),
    _b("s23", "南区24栋", [((1258, 1231, 1443, 1314), 6)], "dorm", "DORM", 224),
    _b("s24", "南区25栋", [((1450, 1237, 1607, 1320), 6)], "dorm", "DORM", 225),
    _b("s25", "南区26栋", [((1422, 1115, 1575, 1162), 4)], "dorm", "DORM", 226),
]


# ---------------------------------------------------------------------------
# Roads (pixel polylines + pixel width)
# ---------------------------------------------------------------------------
# kind: main | secondary | lane
ROADS = [
    {"name": "宝成路", "kind": "main", "width": 40,
     "points": [(140, -40), (120, 200), (95, 430), (70, 660), (55, 900), (45, 1130), (38, 1360)]},
    {"name": "航空路", "kind": "main", "width": 46,
     "points": [(1645, -40), (1645, 1360)]},
    {"name": "学院路", "kind": "main", "width": 46,
     "points": [(92, 626), (400, 642), (800, 655), (1200, 670), (1642, 688)]},
    {"name": "南环路", "kind": "main", "width": 42,
     "points": [(46, 1340), (400, 1340), (800, 1340), (1200, 1340), (1622, 1340)]},

    {"name": "北区西纵路", "kind": "secondary", "width": 26,
     "points": [(335, 172), (330, 380), (325, 508)]},
    {"name": "北区中纵路", "kind": "secondary", "width": 30,
     "points": [(523, 184), (520, 380), (515, 508)]},
    {"name": "北区东纵路", "kind": "secondary", "width": 48,
     "points": [(806, 24), (803, 300), (800, 654)]},
    {"name": "生物楼前路", "kind": "secondary", "width": 26,
     "points": [(800, 298), (1000, 301), (1196, 305)]},
    {"name": "科技楼北路", "kind": "secondary", "width": 24,
     "points": [(806, 342), (1100, 346), (1372, 352)]},
    {"name": "1-3栋南侧路", "kind": "lane", "width": 20,
     "points": [(200, 505), (500, 508), (795, 512)]},

    {"name": "南区西纵路", "kind": "secondary", "width": 24,
     "points": [(463, 700), (452, 1050), (441, 1250)]},
    {"name": "南区北横路", "kind": "secondary", "width": 22,
     "points": [(450, 712), (800, 716), (1288, 722)]},
    {"name": "宿舍一巷", "kind": "lane", "width": 18,
     "points": [(707, 778), (690, 950), (665, 1180)]},
    {"name": "宿舍二巷", "kind": "lane", "width": 18,
     "points": [(881, 772), (879, 1000), (877, 1230)]},
    {"name": "南区宿舍北横路", "kind": "lane", "width": 18,
     "points": [(452, 778), (700, 780), (1062, 783)]},
    {"name": "南区宿舍中横路", "kind": "lane", "width": 18,
     "points": [(448, 1024), (760, 1030), (1062, 1032)]},
    {"name": "东纵主路", "kind": "secondary", "width": 36,
     "points": [(1296, 690), (1296, 820), (1296, 946)]},
    {"name": "综合楼前路", "kind": "lane", "width": 16,
     "points": [(1265, 858), (1400, 860), (1580, 862)]},
    {"name": "东门联络路", "kind": "secondary", "width": 18,
     "points": [(1608, 1060), (1500, 1060), (1420, 1060)]},
]


# ---------------------------------------------------------------------------
# Plazas / hard landscape
# ---------------------------------------------------------------------------
PLAZAS = [
    {"name": "同大广场", "rect": (1090, 940, 1420, 1130), "style": "grand"},
    {"name": "北门广场", "rect": (1180, 690, 1345, 805), "style": "gate"},
    {"name": "东门广场", "rect": (1420, 1030, 1630, 1170), "style": "gate"},
    {"name": "生物楼前广场", "rect": (835, 285, 1205, 398), "style": "campus"},
    {"name": "科技楼前广场", "rect": (1180, 398, 1375, 600), "style": "campus"},
    {"name": "西北停车场", "rect": (130, 25, 640, 165), "style": "parking"},
]

FIELD = {"name": "运动场", "rect": (228, 765, 405, 1180)}

# Gate structures (non-building parts)
GATES = [
    {"key": "north_gate", "name": "北门", "center": (1275, 712), "axis": "ns",
     "opening": (1248, 1302), "point_id": 1},
    {"key": "east_gate", "name": "东门", "center": (1600, 1080), "axis": "ew",
     "opening": (1058, 1102), "point_id": 11},
]

# Points that should exist in the app even when a building owns the label.
EXTRA_POINTS = [
    {"id": 12, "name": "同大广场", "category": "PLAZA", "px": 1250, "py": 1035,
     "building": None, "sort": 210},
]





# Circular landscape plazas (non-rectangular features)
ROUND_PLAZAS = [
    {"name": "生物楼前圆形广场", "center": (1265, 372), "radius": 52},
]
