UPDATE campus_building SET point_id = NULL WHERE id IN (3, 4);

INSERT INTO campus_building
    (id, point_id, name, category, height, color_hex, footprint_json, geom, status, sort_order)
VALUES
    (11, NULL, '14栋', 'DORM', 7.5, '#82965F',
     '[[-8.40,-45.95],[-4.26,-45.95],[-4.26,-43.80],[-8.40,-43.80]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-8.40,-45.95],[-4.26,-45.95],[-4.26,-43.80],[-8.40,-43.80],[-8.40,-45.95]]]}'),0),
     'PUBLISHED', 110),
    (12, NULL, '13栋', 'DORM', 7.0, '#82965F',
     '[[-8.31,-42.25],[-4.17,-42.25],[-4.17,-39.90],[-8.31,-39.90]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-8.31,-42.25],[-4.17,-42.25],[-4.17,-39.90],[-8.31,-39.90],[-8.31,-42.25]]]}'),0),
     'PUBLISHED', 120),
    (13, NULL, '12栋', 'DORM', 7.0, '#82965F',
     '[[-8.51,-37.65],[-4.23,-37.65],[-4.23,-35.45],[-8.51,-35.45]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-8.51,-37.65],[-4.23,-37.65],[-4.23,-35.45],[-8.51,-35.45],[-8.51,-37.65]]]}'),0),
     'PUBLISHED', 130),
    (14, NULL, '11栋', 'DORM', 7.0, '#82965F',
     '[[-15.06,-38.10],[-10.91,-38.10],[-10.91,-35.80],[-15.06,-35.80]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-15.06,-38.10],[-10.91,-38.10],[-10.91,-35.80],[-15.06,-35.80],[-15.06,-38.10]]]}'),0),
     'PUBLISHED', 140),
    (15, NULL, '10栋', 'DORM', 7.0, '#82965F',
     '[[-19.23,-37.55],[-15.60,-37.55],[-15.60,-35.25],[-19.23,-35.25]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-19.23,-37.55],[-15.60,-37.55],[-15.60,-35.25],[-19.23,-35.25],[-19.23,-37.55]]]}'),0),
     'PUBLISHED', 150),
    (16, NULL, '7栋', 'DORM', 7.0, '#82965F',
     '[[-19.00,-33.30],[-15.69,-33.30],[-15.69,-30.55],[-19.00,-30.55]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-19.00,-33.30],[-15.69,-33.30],[-15.69,-30.55],[-19.00,-30.55],[-19.00,-33.30]]]}'),0),
     'PUBLISHED', 160),
    (17, NULL, '8栋', 'DORM', 7.0, '#82965F',
     '[[-14.69,-33.85],[-11.06,-33.85],[-11.06,-31.50],[-14.69,-31.50]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-14.69,-33.85],[-11.06,-33.85],[-11.06,-31.50],[-14.69,-31.50],[-14.69,-33.85]]]}'),0),
     'PUBLISHED', 170),
    (18, NULL, '9栋', 'DORM', 7.0, '#82965F',
     '[[-8.51,-33.45],[-4.60,-33.45],[-4.60,-31.35],[-8.51,-31.35]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-8.51,-33.45],[-4.60,-33.45],[-4.60,-31.35],[-8.51,-31.35],[-8.51,-33.45]]]}'),0),
     'PUBLISHED', 180),
    (19, NULL, '4栋', 'DORM', 7.0, '#82965F',
     '[[-19.20,-28.95],[-15.74,-28.95],[-15.74,-26.65],[-19.20,-26.65]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-19.20,-28.95],[-15.74,-28.95],[-15.74,-26.65],[-19.20,-26.65],[-19.20,-28.95]]]}'),0),
     'PUBLISHED', 190),
    (20, NULL, '1栋', 'DORM', 7.0, '#82965F',
     '[[-18.17,-22.25],[-14.77,-22.25],[-14.77,-20.15],[-18.17,-20.15]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-18.17,-22.25],[-14.77,-22.25],[-14.77,-20.15],[-18.17,-20.15],[-18.17,-22.25]]]}'),0),
     'PUBLISHED', 200),
    (21, NULL, '2栋', 'DORM', 7.0, '#82965F',
     '[[-12.14,-22.30],[-9.00,-22.30],[-9.00,-20.20],[-12.14,-20.20]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-12.14,-22.30],[-9.00,-22.30],[-9.00,-20.20],[-12.14,-20.20],[-12.14,-22.30]]]}'),0),
     'PUBLISHED', 210),
    (22, NULL, '3栋', 'DORM', 7.0, '#82965F',
     '[[-6.80,-22.85],[-3.74,-22.85],[-3.74,-20.60],[-6.80,-20.60]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-6.80,-22.85],[-3.74,-22.85],[-3.74,-20.60],[-6.80,-20.60],[-6.80,-22.85]]]}'),0),
     'PUBLISHED', 220),
    (23, NULL, '6栋', 'DORM', 7.0, '#82965F',
     '[[-8.49,-27.05],[-4.11,-27.05],[-4.11,-24.70],[-8.49,-24.70]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-8.49,-27.05],[-4.11,-27.05],[-4.11,-24.70],[-8.49,-24.70],[-8.49,-27.05]]]}'),0),
     'PUBLISHED', 230),
    (24, NULL, '博文苑', 'DORM', 7.5, '#758992',
     '[[-14.43,-38.50],[-9.66,-38.50],[-9.66,-26.25],[-14.43,-26.25],[-14.43,-29.40],[-14.97,-29.40],[-14.97,-35.35],[-14.43,-35.35]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-14.43,-38.50],[-9.66,-38.50],[-9.66,-26.25],[-14.43,-26.25],[-14.43,-29.40],[-14.97,-29.40],[-14.97,-35.35],[-14.43,-35.35],[-14.43,-38.50]]]}'),0),
     'PUBLISHED', 240),
    (25, 4, '生物楼', 'LAB', 9.0, '#6A8C72',
     '[[-1.23,-44.95],[9.43,-44.95],[9.43,-34.75],[-1.23,-34.75]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-1.23,-44.95],[9.43,-44.95],[9.43,-34.75],[-1.23,-34.75],[-1.23,-44.95]]]}'),0),
     'PUBLISHED', 250),
    (26, NULL, '教学科研楼', 'TEACHING', 8.5, '#72857B',
     '[[-1.11,-31.15],[9.66,-31.15],[9.66,-20.15],[-1.11,-20.15]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[-1.11,-31.15],[9.66,-31.15],[9.66,-20.15],[-1.11,-20.15],[-1.11,-31.15]]]}'),0),
     'PUBLISHED', 260),
    (27, 3, '科技楼', 'TEACHING', 10.0, '#6A7F57',
     '[[9.94,-28.00],[21.29,-28.00],[21.29,-20.05],[9.94,-20.05]]'::jsonb,
     ST_SetSRID(ST_GeomFromGeoJSON('{"type":"Polygon","coordinates":[[[9.94,-28.00],[21.29,-28.00],[21.29,-20.05],[9.94,-20.05],[9.94,-28.00]]]}'),0),
     'PUBLISHED', 270);

INSERT INTO road_node (id, name, x, z, enabled) VALUES
    (19, '北校区西环路', -19.0, -24.0, TRUE),
    (20, '宿舍区纵路', -15.0, -30.0, TRUE),
    (21, '博文苑北侧路', -7.0, -30.0, TRUE),
    (22, '北校区主轴', 0.0, -40.0, TRUE),
    (23, '北校区中央路', 10.0, -30.0, TRUE),
    (24, '北校区东环路', 21.0, -26.0, TRUE),
    (25, '学院路西口', -7.0, -16.5, TRUE),
    (26, '学院路中口', 2.0, -16.5, TRUE),
    (27, '学院路东口', 21.0, -16.5, TRUE),
    (28, '科技楼前道路', 15.6, -20.0, TRUE),
    (29, '生物楼前道路', 4.0, -34.75, TRUE),
    (30, '宿舍区入口', -8.0, -25.0, TRUE);

UPDATE campus_point SET x = 15.62, z = -24.03, road_node_id = 28 WHERE id = 3;
UPDATE campus_point SET x = 4.10, z = -39.85, road_node_id = 29 WHERE id = 4;

INSERT INTO road_edge (id, from_node_id, to_node_id, distance, walkable, enabled) VALUES
    (21, 25, 19, NULL, TRUE, TRUE),
    (22, 25, 20, NULL, TRUE, TRUE),
    (23, 20, 30, NULL, TRUE, TRUE),
    (24, 30, 21, NULL, TRUE, TRUE),
    (25, 21, 22, NULL, TRUE, TRUE),
    (26, 22, 29, NULL, TRUE, TRUE),
    (27, 29, 26, NULL, TRUE, TRUE),
    (28, 26, 23, NULL, TRUE, TRUE),
    (29, 23, 24, NULL, TRUE, TRUE),
    (30, 24, 27, NULL, TRUE, TRUE),
    (31, 27, 28, NULL, TRUE, TRUE),
    (32, 25, 26, NULL, TRUE, TRUE),
    (33, 26, 27, NULL, TRUE, TRUE),
    (34, 26, 1, NULL, TRUE, TRUE);

INSERT INTO campus_road_geometry
    (edge_id, geom, source, target, cost, reverse_cost)
SELECT
    edge.id,
    ST_SetSRID(
        ST_MakeLine(
            ST_MakePoint(source_node.x, source_node.z),
            ST_MakePoint(target_node.x, target_node.z)
        ),
        0
    ),
    edge.from_node_id,
    edge.to_node_id,
    GREATEST(ST_Distance(
        ST_SetSRID(ST_MakePoint(source_node.x, source_node.z), 0),
        ST_SetSRID(ST_MakePoint(target_node.x, target_node.z), 0)
    ), 0.1),
    GREATEST(ST_Distance(
        ST_SetSRID(ST_MakePoint(source_node.x, source_node.z), 0),
        ST_SetSRID(ST_MakePoint(target_node.x, target_node.z), 0)
    ), 0.1)
FROM road_edge edge
JOIN road_node source_node ON source_node.id = edge.from_node_id
JOIN road_node target_node ON target_node.id = edge.to_node_id
WHERE edge.id >= 21;

UPDATE camera_preset
SET x = 0, y = 72, z = 74, target_x = 0, target_y = 2, target_z = -12
WHERE preset_key = 'overview';

SELECT setval(pg_get_serial_sequence('road_node', 'id'), (SELECT MAX(id) FROM road_node), true);
SELECT setval(pg_get_serial_sequence('road_edge', 'id'), (SELECT MAX(id) FROM road_edge), true);
SELECT setval(pg_get_serial_sequence('campus_building', 'id'), (SELECT MAX(id) FROM campus_building), true);
