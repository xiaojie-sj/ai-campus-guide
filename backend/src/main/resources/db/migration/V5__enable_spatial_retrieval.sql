ALTER TABLE campus_point
    ADD COLUMN geom geometry(Point, 0);

UPDATE campus_point
SET geom = ST_SetSRID(ST_MakePoint(x, z), 0)
WHERE geom IS NULL;

CREATE INDEX idx_campus_point_geom ON campus_point USING GIST (geom);

CREATE TABLE campus_road_geometry (
    id BIGSERIAL PRIMARY KEY,
    edge_id BIGINT NOT NULL UNIQUE REFERENCES road_edge(id),
    geom geometry(LineString, 0) NOT NULL,
    source INTEGER,
    target INTEGER,
    cost DOUBLE PRECISION NOT NULL DEFAULT 1,
    reverse_cost DOUBLE PRECISION NOT NULL DEFAULT 1
);

INSERT INTO campus_road_geometry (edge_id, geom, source, target, cost, reverse_cost)
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
JOIN road_node target_node ON target_node.id = edge.to_node_id;

CREATE INDEX idx_campus_road_geom ON campus_road_geometry USING GIST (geom);
CREATE INDEX idx_campus_road_topology ON campus_road_geometry(source, target);

ALTER TABLE knowledge_chunk
    ADD COLUMN embedding_vector vector(1024);

CREATE INDEX idx_chunk_embedding_hnsw
ON knowledge_chunk
USING hnsw (embedding_vector vector_cosine_ops);

SELECT setval(pg_get_serial_sequence('admin_user', 'id'), COALESCE((SELECT MAX(id) FROM admin_user), 1), true);
SELECT setval(pg_get_serial_sequence('road_node', 'id'), COALESCE((SELECT MAX(id) FROM road_node), 1), true);
SELECT setval(pg_get_serial_sequence('road_edge', 'id'), COALESCE((SELECT MAX(id) FROM road_edge), 1), true);
SELECT setval(pg_get_serial_sequence('campus_point', 'id'), COALESCE((SELECT MAX(id) FROM campus_point), 1), true);
SELECT setval(pg_get_serial_sequence('camera_preset', 'id'), COALESCE((SELECT MAX(id) FROM camera_preset), 1), true);
SELECT setval(pg_get_serial_sequence('tour_route', 'id'), COALESCE((SELECT MAX(id) FROM tour_route), 1), true);
SELECT setval(pg_get_serial_sequence('tour_stop', 'id'), COALESCE((SELECT MAX(id) FROM tour_stop), 1), true);
SELECT setval(pg_get_serial_sequence('faq_item', 'id'), COALESCE((SELECT MAX(id) FROM faq_item), 1), true);
SELECT setval(pg_get_serial_sequence('system_config', 'id'), COALESCE((SELECT MAX(id) FROM system_config), 1), true);
SELECT setval(pg_get_serial_sequence('campus_media', 'id'), COALESCE((SELECT MAX(id) FROM campus_media), 1), true);
