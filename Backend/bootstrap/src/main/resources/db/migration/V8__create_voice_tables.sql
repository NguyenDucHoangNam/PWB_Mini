-- V8: Create voice module tables (voice_voice_tags, voice_songs, voice_song_tag_configs)

CREATE TABLE voice_voice_tags (
    id               UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at       TIMESTAMPTZ(6)  NOT NULL,
    updated_at       TIMESTAMPTZ(6)  NOT NULL,
    created_by       VARCHAR(255)    NOT NULL DEFAULT 'system',
    updated_by       VARCHAR(255)    NOT NULL DEFAULT 'system',
    deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMPTZ(6),
    version          BIGINT          NOT NULL DEFAULT 0,

    user_id          UUID            NOT NULL,
    name             VARCHAR(128)    NOT NULL,
    description      VARCHAR(512),
    tag_type         VARCHAR(32)     NOT NULL,                          -- TTS, UPLOADED
    source_text      VARCHAR(4000),                                    -- For TTS type
    language_code    VARCHAR(10),
    s3_key           VARCHAR(512)    NOT NULL,
    duration_seconds INTEGER,
    file_size_bytes  BIGINT,
    is_default       BOOLEAN         NOT NULL DEFAULT FALSE,

    PRIMARY KEY (id),
    CONSTRAINT uk_voice_tags_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_voice_tags_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE RESTRICT
);

CREATE TABLE voice_songs (
    id                UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at        TIMESTAMPTZ(6)  NOT NULL,
    updated_at        TIMESTAMPTZ(6)  NOT NULL,
    created_by        VARCHAR(255)    NOT NULL DEFAULT 'system',
    updated_by        VARCHAR(255)    NOT NULL DEFAULT 'system',
    deleted           BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMPTZ(6),
    version           BIGINT          NOT NULL DEFAULT 0,

    user_id           UUID            NOT NULL,
    title             VARCHAR(256)    NOT NULL,
    artist            VARCHAR(256),
    album             VARCHAR(256),
    original_s3_key   VARCHAR(512)    NOT NULL,
    processed_s3_key  VARCHAR(512),
    file_size_bytes   BIGINT          NOT NULL,
    duration_seconds  INTEGER,
    format            VARCHAR(16)     NOT NULL,
    status            VARCHAR(32)     NOT NULL DEFAULT 'UPLOADED',     -- UPLOADED, PROCESSING, PROCESSED, FAILED
    thumbnail_url     VARCHAR(512),
    last_error        VARCHAR(2048),

    PRIMARY KEY (id),
    CONSTRAINT fk_voice_songs_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE RESTRICT
);

CREATE TABLE voice_song_tag_configs (
    id                   UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at           TIMESTAMPTZ(6)  NOT NULL,
    updated_at           TIMESTAMPTZ(6)  NOT NULL,
    created_by           VARCHAR(255)    NOT NULL DEFAULT 'system',
    updated_by           VARCHAR(255)    NOT NULL DEFAULT 'system',
    deleted              BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMPTZ(6),
    version              BIGINT          NOT NULL DEFAULT 0,

    song_id              UUID            NOT NULL,
    voice_tag_id         UUID            NOT NULL,
    interval_seconds     INTEGER         NOT NULL DEFAULT 25 CHECK (interval_seconds > 0),
    volume_percentage    INTEGER         NOT NULL DEFAULT 50 CHECK (volume_percentage BETWEEN 0 AND 100),
    fade_in_duration_ms  INTEGER         NOT NULL DEFAULT 500 CHECK (fade_in_duration_ms >= 0),
    fade_out_duration_ms INTEGER         NOT NULL DEFAULT 500 CHECK (fade_out_duration_ms >= 0),
    start_offset_seconds INTEGER         NOT NULL DEFAULT 0 CHECK (start_offset_seconds >= 0),
    enabled              BOOLEAN         NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    CONSTRAINT uk_song_tag_config_song UNIQUE (song_id),
    CONSTRAINT fk_song_tag_configs_song FOREIGN KEY (song_id) REFERENCES voice_songs(id) ON DELETE CASCADE,
    CONSTRAINT fk_song_tag_configs_voice_tag FOREIGN KEY (voice_tag_id) REFERENCES voice_voice_tags(id) ON DELETE RESTRICT
);

CREATE INDEX ix_voice_tags_user_id       ON voice_voice_tags (user_id);
CREATE INDEX ix_voice_tags_tag_type      ON voice_voice_tags (tag_type);
CREATE INDEX ix_voice_songs_user_id      ON voice_songs (user_id);
CREATE INDEX ix_voice_songs_status       ON voice_songs (status);
CREATE INDEX ix_voice_songs_user_status  ON voice_songs (user_id, status);
CREATE INDEX ix_song_tag_configs_song_id ON voice_song_tag_configs (song_id);
