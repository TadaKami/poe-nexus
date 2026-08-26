create table users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash TEXT NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

create unique index users_email_lower_uq on users (lower(email));

create table refresh_tokens(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    client_info TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

create unique index refresh_tokens_hash_uq on refresh_tokens(token_hash);
create index refresh_tokens_active_idx on refresh_tokens (user_id) where revoked_at is null;