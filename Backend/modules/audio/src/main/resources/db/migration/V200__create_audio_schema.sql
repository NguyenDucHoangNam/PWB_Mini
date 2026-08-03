CREATE TABLE audio_voice_tags (
    id               UUID            NOT NULL,
    created_at       TIMESTAMPTZ(6)  NOT NULL,
    updated_at       TIMESTAMPTZ(6)  NOT NULL,
    created_by       VARCHAR(255)    NOT NULL,
    updated_by       VARCHAR(255)    NOT NULL,
    deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    version          BIGINT          NOT NULL DEFAULT 0,

    user_id          UUID            NOT NULL,
    name             VARCHAR(128)    NOT NULL,
    tag_type         VARCHAR(16)     NOT NULL,
    source_text      VARCHAR(2048),
    language_code    VARCHAR(8),
    s3_key           VARCHAR(512),
    duration_seconds INTEGER,
    file_size_bytes  BIGINT,
    is_default       BOOLEAN         NOT NULL DEFAULT FALSE,

    PRIMARY KEY (id),
    CONSTRAINT uk_audio_voice_tags_user_name UNIQUE (user_id, name),
    CONSTRAINT fk_audio_voice_tags_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE RESTRICT
);

CREATE TABLE audio_songs (
    id                UUID            NOT NULL,
    created_at        TIMESTAMPTZ(6)  NOT NULL,
    updated_at        TIMESTAMPTZ(6)  NOT NULL,
    created_by        VARCHAR(255)    NOT NULL,
    updated_by        VARCHAR(255)    NOT NULL,
    deleted           BOOLEAN         NOT NULL DEFAULT FALSE,
    version           BIGINT          NOT NULL DEFAULT 0,

    user_id           UUID            NOT NULL,
    title             VARCHAR(256)    NOT NULL,
    artist            VARCHAR(256),
    album             VARCHAR(256),
    original_s3_key   VARCHAR(512)    NOT NULL,
    processed_s3_key  VARCHAR(512),
    file_size_bytes   BIGINT,
    duration_seconds  INTEGER,
    format            VARCHAR(16),
    status            VARCHAR(32)     NOT NULL,
    thumbnail_url     VARCHAR(512),
    last_error        VARCHAR(1024),

    PRIMARY KEY (id),
    CONSTRAINT fk_audio_songs_user FOREIGN KEY (user_id) REFERENCES iam_users(id) ON DELETE RESTRICT
);

CREATE TABLE audio_song_tag_configs (
    id                   UUID            NOT NULL,
    created_at           TIMESTAMPTZ(6)  NOT NULL,
    updated_at           TIMESTAMPTZ(6)  NOT NULL,
    created_by           VARCHAR(255)    NOT NULL,
    updated_by           VARCHAR(255)    NOT NULL,
    deleted              BOOLEAN         NOT NULL DEFAULT FALSE,
    version              BIGINT          NOT NULL DEFAULT 0,

    song_id              UUID            NOT NULL,
    voice_tag_id         UUID            NOT NULL,
    interval_seconds     INTEGER,
    volume_percentage    INTEGER,
    fade_in_duration_ms  INTEGER,
    fade_out_duration_ms INTEGER,
    start_offset_seconds INTEGER,
    enabled              BOOLEAN         NOT NULL DEFAULT TRUE,

    PRIMARY KEY (id),
    CONSTRAINT uk_audio_song_tag_configs_song_id UNIQUE (song_id),
    CONSTRAINT fk_audio_song_tag_configs_song FOREIGN KEY (song_id) REFERENCES audio_songs(id) ON DELETE CASCADE,
    CONSTRAINT fk_audio_song_tag_configs_voice_tag FOREIGN KEY (voice_tag_id) REFERENCES audio_voice_tags(id) ON DELETE RESTRICT
);

CREATE INDEX ix_audio_voice_tags_user_id       ON audio_voice_tags (user_id);
CREATE INDEX ix_audio_voice_tags_tag_type      ON audio_voice_tags (tag_type);
CREATE INDEX ix_audio_songs_user_id            ON audio_songs (user_id);
CREATE INDEX ix_audio_songs_status             ON audio_songs (status);
CREATE INDEX ix_audio_songs_user_status        ON audio_songs (user_id, status);
CREATE INDEX ix_audio_song_tag_configs_song_id ON audio_song_tag_configs (song_id);
