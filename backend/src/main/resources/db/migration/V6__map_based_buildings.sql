CREATE TABLE campus_building (
    id BIGSERIAL PRIMARY KEY,
    point_id BIGINT UNIQUE REFERENCES campus_point(id),
    name VARCHAR(160) NOT NULL,
    category VARCHAR(40) NOT NULL,
    height DOUBLE PRECISION NOT NULL DEFAULT 6,
    color_hex VARCHAR(16),
    footprint_json JSONB NOT NULL,
    geom geometry(Polygon, 0) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_campus_building_geom ON campus_building USING GIST (geom);
CREATE INDEX idx_campus_building_point ON campus_building(point_id);

WITH building_data
    (id, point_id, name, category, height, color_hex, footprint_json, sort_order)
AS (
    VALUES
    (1, 10, '综合服务中心', 'SERVICE', 5.5, '#8A7258',
     '[[-10.11,-13.65],[-5.14,-13.25],[-5.17,-7.60],[-10.17,-7.80]]'::jsonb, 10),
    (2, 9, '实验楼', 'LAB', 6.5, '#6A7785',
     '[[19.54,-6.15],[21.97,-5.95],[21.97,3.15],[18.77,3.05],[18.43,2.20],[19.06,-5.00]]'::jsonb, 20),
    (3, 3, '科技楼', 'TEACHING', 7.5, '#6A7F57',
     '[[1.86,-4.40],[5.74,-4.25],[7.00,0.20],[6.94,1.65],[1.60,1.40]]'::jsonb, 30),
    (4, 4, '生科楼', 'TEACHING', 4.5, '#658B78',
     '[[-0.11,5.80],[-0.17,7.15],[-1.31,9.95],[-1.97,9.95],[-4.20,7.05],[-4.17,5.50]]'::jsonb, 40),
    (5, 5, '同大综合楼', 'ADMIN', 5.5, '#936B4F',
     '[[0.83,10.10],[2.09,7.60],[2.69,7.70],[5.23,10.35],[5.26,11.85],[0.86,11.65]]'::jsonb, 50),
    (6, 2, '图书馆', 'LIBRARY', 6.5, '#4F7F91',
     '[[-9.37,8.95],[-9.37,13.95],[-10.89,13.95],[-10.89,8.85]]'::jsonb, 60),
    (7, 6, '学生公寓', 'DORM', 7.5, '#8C6A67',
     '[[-8.14,9.30],[-5.71,9.35],[-5.69,11.85],[-8.14,11.75]]'::jsonb, 70),
    (8, 7, '学生食堂', 'CANTEEN', 4.5, '#B08A53',
     '[[6.86,10.35],[9.74,10.45],[11.03,11.10],[11.03,12.50],[9.77,13.95],[9.17,13.95],[6.77,11.65]]'::jsonb, 80),
    (9, NULL, '教学辅助建筑', 'TEACHING', 5.5, '#728577',
     '[[12.06,10.80],[16.26,10.70],[16.31,12.00],[13.29,13.95],[12.69,13.95],[12.06,12.15]]'::jsonb, 90),
    (10, NULL, '生活服务建筑', 'SERVICE', 6.5, '#7B6755',
     '[[-8.63,12.80],[-8.49,12.05],[-7.77,12.05],[-5.57,12.95],[-5.57,13.95],[-8.63,13.95]]'::jsonb, 100)
)
INSERT INTO campus_building
    (id, point_id, name, category, height, color_hex, footprint_json, geom, status, sort_order)
SELECT id, point_id, name, category, height, color_hex, footprint_json,
       ST_SetSRID(
           ST_GeomFromGeoJSON(
               jsonb_build_object(
                   'type', 'Polygon',
                   'coordinates', jsonb_build_array(footprint_json)
               )::text
           ),
           0
       ),
       'PUBLISHED', sort_order
FROM building_data;

UPDATE campus_point SET x = 7.43, z = -14.50, road_node_id = 1 WHERE id = 1;
UPDATE campus_point SET x = -10.13, z = 11.42, road_node_id = 15 WHERE id = 2;
UPDATE campus_point SET x = 4.63, z = -1.08, road_node_id = 10 WHERE id = 3;
UPDATE campus_point SET x = -1.99, z = 7.57, road_node_id = 11 WHERE id = 4;
UPDATE campus_point SET x = 2.83, z = 9.88, road_node_id = 13 WHERE id = 5;
UPDATE campus_point SET x = -6.92, z = 10.56, road_node_id = 15 WHERE id = 6;
UPDATE campus_point SET x = 9.20, z = 11.99, road_node_id = 16 WHERE id = 7;
UPDATE campus_point SET x = -15.71, z = 1.00, road_node_id = 9 WHERE id = 8;
UPDATE campus_point SET x = 19.96, z = -1.45, road_node_id = 14 WHERE id = 9;
UPDATE campus_point SET x = -7.65, z = -10.57, road_node_id = 5 WHERE id = 10;

UPDATE campus_point
SET geom = ST_SetSRID(ST_MakePoint(x, z), 0)
WHERE status = 'PUBLISHED';

UPDATE road_node SET x = 7.43, z = -14.50 WHERE id = 1;
UPDATE road_node SET x = 0.00, z = -14.00 WHERE id = 2;
UPDATE road_node SET x = 3.00, z = -5.00 WHERE id = 3;
UPDATE road_node SET x = 10.00, z = -8.00 WHERE id = 4;
UPDATE road_node SET x = -8.00, z = -4.00 WHERE id = 5;
UPDATE road_node SET x = -10.00, z = 6.00 WHERE id = 6;
UPDATE road_node SET x = -13.00, z = 2.00 WHERE id = 7;
UPDATE road_node SET x = 7.00, z = 10.00 WHERE id = 8;
UPDATE road_node SET x = 15.00, z = 1.00 WHERE id = 9;
UPDATE road_node SET x = 5.00, z = -1.00 WHERE id = 10;
UPDATE road_node SET x = 2.00, z = 7.00 WHERE id = 11;
UPDATE road_node SET x = 0.00, z = 7.00 WHERE id = 12;
UPDATE road_node SET x = 6.00, z = -12.00 WHERE id = 13;
UPDATE road_node SET x = 20.00, z = 0.00 WHERE id = 14;
UPDATE road_node SET x = -6.00, z = 11.00 WHERE id = 15;
UPDATE road_node SET x = 9.00, z = 12.00 WHERE id = 16;
UPDATE road_node SET x = 15.00, z = -5.00 WHERE id = 17;
UPDATE road_node SET x = 10.00, z = -10.00 WHERE id = 18;

UPDATE campus_road_geometry road
SET geom = ST_SetSRID(
        ST_MakeLine(
            ST_MakePoint(source_node.x, source_node.z),
            ST_MakePoint(target_node.x, target_node.z)
        ),
        0
    ),
    cost = GREATEST(ST_Distance(
        ST_SetSRID(ST_MakePoint(source_node.x, source_node.z), 0),
        ST_SetSRID(ST_MakePoint(target_node.x, target_node.z), 0)
    ), 0.1),
    reverse_cost = GREATEST(ST_Distance(
        ST_SetSRID(ST_MakePoint(source_node.x, source_node.z), 0),
        ST_SetSRID(ST_MakePoint(target_node.x, target_node.z), 0)
    ), 0.1)
FROM road_edge edge
JOIN road_node source_node ON source_node.id = edge.from_node_id
JOIN road_node target_node ON target_node.id = edge.to_node_id
WHERE road.edge_id = edge.id;

SELECT setval(
    pg_get_serial_sequence('campus_building', 'id'),
    COALESCE((SELECT MAX(id) FROM campus_building), 1),
    true
);
