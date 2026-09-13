create table payment (
                         id              uuid        primary key,
                         merchant_id     uuid        not null,
                         amount_minor    bigint      not null,
                         currency        char(3)     not null,
                         status          varchar(16) not null,
                         reference       varchar(64),
                         provider_code   varchar(32),
                         provider_ref    varchar(64),
                         failure_reason  varchar(128),
                         created_at      timestamptz not null default now(),
                         updated_at      timestamptz not null default now(),
                         version         bigint      not null default 0
);

create index payment_merchant_created_idx on payment (merchant_id, created_at desc);