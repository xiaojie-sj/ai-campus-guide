CREATE TABLE admin_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL
);

CREATE TABLE road_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    x DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE road_edge (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_node_id BIGINT NOT NULL,
    to_node_id BIGINT NOT NULL,
    distance DOUBLE,
    walkable BOOLEAN NOT NULL DEFAULT TRUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_edge_from FOREIGN KEY (from_node_id) REFERENCES road_node(id),
    CONSTRAINT fk_edge_to FOREIGN KEY (to_node_id) REFERENCES road_node(id)
);

CREATE TABLE campus_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    aliases VARCHAR(500),
    category VARCHAR(40) NOT NULL,
    description TEXT NOT NULL,
    x DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    building_height DOUBLE,
    color_hex VARCHAR(16),
    model_key VARCHAR(80),
    road_node_id BIGINT,
    status VARCHAR(20) NOT NULL,
    verification_status VARCHAR(30) NOT NULL,
    source_title VARCHAR(300),
    source_url VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_point_node FOREIGN KEY (road_node_id) REFERENCES road_node(id)
);

CREATE TABLE camera_preset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    preset_key VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    point_id BIGINT,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    target_x DOUBLE NOT NULL,
    target_y DOUBLE NOT NULL,
    target_z DOUBLE NOT NULL,
    public_visible BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_camera_point FOREIGN KEY (point_id) REFERENCES campus_point(id)
);

CREATE TABLE tour_route (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_key VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    summary TEXT,
    audience VARCHAR(160),
    estimated_minutes INT,
    status VARCHAR(20) NOT NULL,
    verification_status VARCHAR(30) NOT NULL,
    effective_from DATE,
    effective_to DATE,
    sort_order INT NOT NULL DEFAULT 0
);

CREATE TABLE tour_stop (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    route_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    narration TEXT,
    transition_type VARCHAR(30),
    CONSTRAINT fk_stop_route FOREIGN KEY (route_id) REFERENCES tour_route(id),
    CONSTRAINT fk_stop_point FOREIGN KEY (point_id) REFERENCES campus_point(id)
);

CREATE TABLE knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    original_filename VARCHAR(300) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    source_title VARCHAR(500),
    source_url VARCHAR(1000),
    effective_year VARCHAR(20),
    effective_from DATE,
    effective_to DATE,
    category VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    parse_status VARCHAR(30) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    index_version VARCHAR(80),
    reviewer VARCHAR(100),
    checksum VARCHAR(128) NOT NULL,
    failure_reason VARCHAR(1000),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE knowledge_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    title VARCHAR(300),
    heading VARCHAR(300),
    page_ref VARCHAR(100),
    content TEXT NOT NULL,
    embedding TEXT NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    metadata_json TEXT,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_chunk_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id)
);

CREATE TABLE faq_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question VARCHAR(500) NOT NULL,
    aliases VARCHAR(1000),
    answer TEXT NOT NULL,
    category VARCHAR(100),
    source_title VARCHAR(500),
    source_url VARCHAR(1000),
    priority INT NOT NULL DEFAULT 10,
    status VARCHAR(20) NOT NULL,
    effective_from DATE,
    effective_to DATE,
    updated_at DATETIME NOT NULL
);

CREATE TABLE chat_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    input_type VARCHAR(20) NOT NULL,
    intent VARCHAR(40) NOT NULL,
    confidence DOUBLE,
    point_id BIGINT,
    answer_status VARCHAR(40) NOT NULL,
    answer TEXT,
    sources_json TEXT,
    latency_ms BIGINT,
    feedback VARCHAR(30),
    created_at DATETIME NOT NULL
);

CREATE TABLE system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(1000) NOT NULL,
    description VARCHAR(500),
    value_type VARCHAR(20) NOT NULL,
    min_value DOUBLE,
    max_value DOUBLE,
    updated_at DATETIME NOT NULL
);

CREATE TABLE audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_user_id BIGINT,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(80),
    target_id VARCHAR(100),
    detail VARCHAR(2000),
    created_at DATETIME NOT NULL
);

CREATE INDEX idx_point_status ON campus_point(status, sort_order);
CREATE INDEX idx_edge_nodes ON road_edge(from_node_id, to_node_id);
CREATE INDEX idx_chunk_document ON knowledge_chunk(document_id, status);
CREATE INDEX idx_chat_created ON chat_log(created_at);
CREATE INDEX idx_audit_created ON audit_log(created_at);
