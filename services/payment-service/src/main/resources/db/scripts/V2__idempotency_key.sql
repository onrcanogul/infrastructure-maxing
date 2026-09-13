create table idempotency_key (
                                 merchant_id     uuid        not null,
                                 idem_key        varchar(64) not null,
                                 request_hash    char(64)    not null,
                                 payment_id      uuid        not null references payment(id),
                                 created_at      timestamptz not null default now(),
                                 primary key (merchant_id, idem_key)
);