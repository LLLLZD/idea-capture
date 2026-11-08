-- v1 初始化：用户 / 模板 / 记录 / 文档 / 用量

CREATE TABLE IF NOT EXISTS users (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username        TEXT NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    display_name    TEXT NOT NULL DEFAULT '',
    role            TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('admin', 'user')),
    status          TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS templates (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key             TEXT NOT NULL UNIQUE,
    name            TEXT NOT NULL,
    criterion       TEXT NOT NULL,
    skeleton_md     TEXT NOT NULL,
    sample_md       TEXT NOT NULL DEFAULT '',
    word_range      TEXT NOT NULL DEFAULT '',
    is_builtin      BOOLEAN NOT NULL DEFAULT false,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    sort            INT NOT NULL DEFAULT 100,
    version         INT NOT NULL DEFAULT 1,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_templates (
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    template_id     BIGINT NOT NULL REFERENCES templates(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, template_id)
);

CREATE TABLE IF NOT EXISTS records (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_id       TEXT NOT NULL,
    raw_text        TEXT NOT NULL,
    duration_ms     INT,
    recorded_at     TIMESTAMPTZ,
    lang            TEXT NOT NULL DEFAULT 'zh',
    asr_provider    TEXT,
    asr_model       TEXT,
    status          TEXT NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'running', 'done', 'failed')),
    error           TEXT,
    render_retry    INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, client_id)
);
CREATE INDEX IF NOT EXISTS records_user_created_idx ON records (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS records_status_idx ON records (status) WHERE status IN ('pending', 'running');

CREATE TABLE IF NOT EXISTS documents (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    record_id       BIGINT NOT NULL REFERENCES records(id) ON DELETE CASCADE,
    template_id     BIGINT REFERENCES templates(id),
    template_version INT,
    genre_reason    TEXT,
    body_md         TEXT NOT NULL,
    flags           JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_current      BOOLEAN NOT NULL DEFAULT true,
    model           TEXT,
    token_in        INT,
    token_out       INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS documents_record_idx ON documents (record_id, is_current);

CREATE TABLE IF NOT EXISTS usage_logs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    record_id       BIGINT REFERENCES records(id) ON DELETE SET NULL,
    model           TEXT,
    token_in        INT NOT NULL DEFAULT 0,
    token_out       INT NOT NULL DEFAULT 0,
    duration_ms     INT NOT NULL DEFAULT 0,
    ok              BOOLEAN NOT NULL DEFAULT true,
    error           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS usage_user_created_idx ON usage_logs (user_id, created_at DESC);
