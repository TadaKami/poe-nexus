create table atlas_builds(
    user_id UUID PRIMARY KEY REFERENCES users(id) on delete cascade,
    url text not null,
    node_ids jsonb not null default '[]',
    updated_at TIMESTAMPTZ not null default now()
);