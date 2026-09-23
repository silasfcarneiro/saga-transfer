create table account (
    id uuid primary key,
    owner_name text not null,
    balance bigint not null,
    version bigint not null default 0
);

create table outbox (
    id uuid primary key,
    saga_id uuid not null,
    event_type text not null,
    topic text not null,
    payload text not null,
    created_at timestamptz not null default now(),
    published_at timestamptz
);

create table processed_event (
    event_id uuid primary key,
    processed_at timestamptz not null default now()
);