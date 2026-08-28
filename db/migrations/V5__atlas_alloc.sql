create table atlas_allocations(
    nexus_id UUID PRIMARY KEY REFERENCES nexuses(id) ON DELETE CASCADE,
    node_ids jsonb NOT NULL DEFAULT '[]',
    updated_by UUID REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);