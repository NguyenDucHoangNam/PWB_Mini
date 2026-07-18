CREATE TABLE iam_roles (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ(6)  NOT NULL,
    updated_at      TIMESTAMPTZ(6)  NOT NULL,
    created_by      VARCHAR(255)    NOT NULL DEFAULT 'system',
    updated_by      VARCHAR(255)    NOT NULL DEFAULT 'system',
    deleted         BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMPTZ(6)  NULL,
    version         BIGINT          NOT NULL DEFAULT 0,

    name            VARCHAR(32)     NOT NULL,
    description     VARCHAR(255)    NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_iam_roles_name UNIQUE (name)
);

CREATE INDEX ix_iam_roles_name ON iam_roles (name);
