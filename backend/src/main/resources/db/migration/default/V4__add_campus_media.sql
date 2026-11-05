CREATE TABLE campus_media (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    point_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    media_type VARCHAR(30) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    alt_text VARCHAR(500),
    source_title VARCHAR(300),
    source_url VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_media_point FOREIGN KEY (point_id) REFERENCES campus_point(id)
);

CREATE INDEX idx_media_point_status ON campus_media(point_id, status, sort_order);

INSERT INTO campus_media
    (point_id, title, media_type, file_url, alt_text, source_title, status, sort_order, created_at)
VALUES
    (1, '学校校门与校园平面位置示意', 'MAP', '/media/campus-map.png',
     '湖北工程学院新技术学院校园平面位置示意', '校园平面导览素材 · 演示素材',
     'PUBLISHED', 10, CURRENT_TIMESTAMP);
