CREATE TABLE namespace (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    path               VARCHAR(40) NOT NULL UNIQUE,
    display_path       VARCHAR(40) NOT NULL,
    owner_type         VARCHAR(20) NOT NULL,
    owner_id           BIGINT NOT NULL,
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE UNIQUE INDEX uk_namespace_owner ON namespace(owner_type, owner_id);
CREATE INDEX idx_namespace_owner_type ON namespace(owner_type);
