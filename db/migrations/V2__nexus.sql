create table nexuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(100) not null,
    description text,
    leader_id UUID not null REFERENCES users(id),
    created_at TIMESTAMPTZ not null default now()
);

create table nexus_members(
    nexus_id UUID not null REFERENCES nexuses(id) on delete cascade,
    user_id UUID not null REFERENCES users(id) on delete cascade,
    role varchar(20) not null default 'member',
    joined_at TIMESTAMPTZ not null default now(),
    PRIMARY KEY (nexus_id, user_id),
    check (role in ('leader', 'officer', 'member'))
);

create index nexus_members_user_idx on nexus_members(user_id);

create table nexus_invites(
    id UUID PRIMARY KEY default gen_random_uuid(),
    nexus_id uuid not null REFERENCES nexuses(id) on delete cascade,
    code varchar(32) not null,
    created_by uuid not null REFERENCES users(id),
    expires_at TIMESTAMPTZ not null,
    max_users int,
    used_count int not null default 0,
    created_at TIMESTAMPTZ not null default now()
);


create unique index nexus_invites_code_uq on nexus_invites(code);