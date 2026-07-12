CREATE TABLE taxpayers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filer_type VARCHAR(20) NOT NULL,
    tax_id_encrypted TEXT NOT NULL,
    tax_id_hash VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50),
    state_of_reg VARCHAR(2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_taxpayers_tax_id_hash ON taxpayers(tax_id_hash);
