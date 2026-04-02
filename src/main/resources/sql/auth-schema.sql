CREATE TABLE app_user (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    username           VARCHAR(40) NOT NULL UNIQUE,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,
    display_name       VARCHAR(100),
    system_role        VARCHAR(20) NOT NULL DEFAULT 'SYSTEM_USER',
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified     TINYINT(1) NOT NULL DEFAULT 0,
    last_login_at      DATETIME(6) NULL,
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at         DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_app_user_status ON app_user(status);

CREATE TABLE user_refresh_token (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id            BIGINT NOT NULL,
    token_hash         VARCHAR(64) NOT NULL UNIQUE,
    user_agent         VARCHAR(500),
    created_ip         VARCHAR(64),
    expires_at         DATETIME(6) NOT NULL,
    last_used_at       DATETIME(6) NULL,
    revoked_at         DATETIME(6) NULL,
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_refresh_token_user_id ON user_refresh_token(user_id);
CREATE INDEX idx_user_refresh_token_expires_at ON user_refresh_token(expires_at);
