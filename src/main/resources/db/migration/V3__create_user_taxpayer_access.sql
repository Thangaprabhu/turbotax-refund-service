CREATE TABLE user_taxpayer_access (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
    role VARCHAR(20) NOT NULL,
    granted_by UUID REFERENCES users(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_taxpayer UNIQUE (user_id, taxpayer_id)
);

CREATE INDEX idx_access_user_id ON user_taxpayer_access(user_id);
CREATE INDEX idx_access_taxpayer_id ON user_taxpayer_access(taxpayer_id);
