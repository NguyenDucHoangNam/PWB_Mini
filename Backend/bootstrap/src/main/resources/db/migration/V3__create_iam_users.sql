CREATE TABLE iam_users (
    id                       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at               TIMESTAMPTZ(6)  NOT NULL,
    updated_at               TIMESTAMPTZ(6)  NOT NULL,
    created_by               VARCHAR(255)    NOT NULL DEFAULT 'system',
    updated_by               VARCHAR(255)    NOT NULL DEFAULT 'system',
    deleted                  BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at               TIMESTAMPTZ(6)  NULL,
    version                  BIGINT          NOT NULL DEFAULT 0,

    username                 VARCHAR(50)     NOT NULL,
    email                    VARCHAR(255)    NOT NULL,
    password                 VARCHAR(255)    NULL,
    full_name                VARCHAR(128)    NULL,
    avatar_url               VARCHAR(512)    NULL,
    phone                    VARCHAR(20)     NULL,
    status                   VARCHAR(32)     NOT NULL,
    role_id                  UUID            NULL,
    oauth_provider           VARCHAR(16)     NOT NULL DEFAULT 'LOCAL',
    oauth_id                 VARCHAR(255)    NULL,
    deletion_requested_at    TIMESTAMPTZ(6)  NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_iam_users_email UNIQUE (email),
    CONSTRAINT uk_iam_users_username UNIQUE (username),
    CONSTRAINT fk_iam_users_role FOREIGN KEY (role_id) REFERENCES iam_roles(id),
    CONSTRAINT uk_iam_users_oauth UNIQUE (oauth_provider, oauth_id)
);

CREATE INDEX ix_iam_users_email ON iam_users (email);
CREATE INDEX ix_iam_users_oauth ON iam_users (oauth_provider, oauth_id);
CREATE INDEX ix_iam_users_role_id ON iam_users (role_id);
CREATE INDEX ix_iam_users_status ON iam_users (status);
