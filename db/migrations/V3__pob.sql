create table pob_builds(
    id UUID PRIMARY KEY default gen_random_uuid(),
    user_id UUID not null REFERENCES users(id) on delete cascade,
    scope varchar(20) not null, --current | target
    pastebin_url text not null,
    version_hash char(64) not null, --sha256 сырого кода
    parsed_data jsonb not null,
    created_at TIMESTAMPTZ not null default now(),
    updated_at TIMESTAMPTZ not null default now(),
    unique(user_id, scope)
);

create index pob_builds_user_idx on pob_builds(user_id);