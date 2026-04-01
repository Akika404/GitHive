CREATE TABLE app_user (
    id                 BIGSERIAL PRIMARY KEY,
    username           VARCHAR(40) NOT NULL UNIQUE,
    email              VARCHAR(255) NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,
    display_name       VARCHAR(100),
    system_role        VARCHAR(20) NOT NULL DEFAULT 'SYSTEM_USER',
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified     BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at      TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at         TIMESTAMPTZ
);

CREATE INDEX idx_app_user_status ON app_user(status);

CREATE TABLE user_refresh_token (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    token_hash         VARCHAR(64) NOT NULL UNIQUE,
    user_agent         VARCHAR(500),
    created_ip         VARCHAR(64),
    expires_at         TIMESTAMPTZ NOT NULL,
    last_used_at       TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_refresh_token_user_id ON user_refresh_token(user_id);
CREATE INDEX idx_user_refresh_token_expires_at ON user_refresh_token(expires_at);
