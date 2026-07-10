CREATE TABLE demo_downloads (
    id UUID PRIMARY KEY,
    distribution_id UUID NOT NULL,
    demo_id UUID NOT NULL,
    session_id_hash VARCHAR(64) NOT NULL,
    ip_subnet_hash VARCHAR(64),
    downloaded_at TIMESTAMP NOT NULL DEFAULT NOW(),
    s3_key VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    CONSTRAINT fk_dl_dist FOREIGN KEY (distribution_id) REFERENCES demo_distributions(id),
    CONSTRAINT fk_dl_demo FOREIGN KEY (demo_id) REFERENCES demos(id)
);

CREATE INDEX idx_dl_dist_time ON demo_downloads (distribution_id, downloaded_at);
CREATE INDEX idx_dl_demo_time ON demo_downloads (demo_id, downloaded_at);
CREATE INDEX idx_dl_session ON demo_downloads (session_id_hash);