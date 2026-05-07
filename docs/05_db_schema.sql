-- PostgreSQL schema draft for Atom Go Mobile MVP

create table app_user (
  id uuid primary key,
  role text not null check (role in ('admin', 'client')),
  login text not null unique,
  password_hash text not null,
  tax_mode text not null default 'self_employed' check (tax_mode in ('self_employed', 'individual_entrepreneur')),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table bike (
  id uuid primary key,
  model_name text not null,
  avatar_url text,
  serial_no text,
  admin_id uuid references app_user(id),
  created_at timestamptz not null default now()
);

create table client_profile (
  id uuid primary key,
  user_id uuid not null unique references app_user(id),
  full_name text not null,
  weekly_rate_rub integer not null check (weekly_rate_rub > 0),
  admin_id uuid references app_user(id),
  address text,
  passport_data text,
  phones_json jsonb not null default '[]'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table rental (
  id uuid primary key,
  client_id uuid not null references client_profile(id),
  bike_id uuid not null references bike(id),
  start_date date not null,
  end_date date,
  weekly_rate_snapshot_rub integer not null,
  video_url text,
  contract_url text,
  comment text,
  admin_id uuid references app_user(id),
  tax_mode text not null default 'self_employed' check (tax_mode in ('self_employed', 'individual_entrepreneur')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table client_balance_adjustment (
  id uuid primary key,
  client_id uuid not null references client_profile(id),
  amount_rub integer not null check (amount_rub > 0),
  sign text not null check (sign in ('plus', 'minus')),
  note text,
  created_by uuid not null references app_user(id),
  created_at timestamptz not null default now()
);

create table ledger_entry (
  id uuid primary key,
  client_id uuid not null references client_profile(id),
  rental_id uuid references rental(id),
  type text not null check (type in ('charge', 'payment', 'adjustment')),
  direction smallint not null check (direction in (-1, 1)),
  amount_rub integer not null check (amount_rub > 0),
  source text not null,
  source_id text,
  note text,
  created_at timestamptz not null default now()
);

create index idx_ledger_client_created_at on ledger_entry(client_id, created_at desc);

create table payment (
  id uuid primary key,
  client_id uuid not null references client_profile(id),
  rental_id uuid references rental(id),
  payment_type text not null check (payment_type in ('day', 'week', 'two_weeks', 'month', 'debt_exact')),
  amount_rub integer not null check (amount_rub > 0),
  status text not null check (status in ('new', 'pending', 'succeeded', 'canceled', 'failed')),
  provider text not null default 'yookassa',
  provider_payment_id text,
  idempotence_key text not null,
  confirmation_url text,
  tax_mode text not null default 'self_employed' check (tax_mode in ('self_employed', 'individual_entrepreneur')),
  fiscalization_status text not null default 'npd_receipt_pending' check (
    fiscalization_status in ('npd_receipt_pending', 'yookassa_receipt_pending', 'fiscalization_not_configured')
  ),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index uq_payment_idempotence_key on payment(idempotence_key);

create table payment_webhook_event (
  id uuid primary key,
  provider text not null,
  event_type text not null,
  provider_event_id text,
  payload_json jsonb not null,
  received_at timestamptz not null default now(),
  processed_at timestamptz,
  process_status text not null default 'new'
);

create index idx_payment_webhook_provider_event_id on payment_webhook_event(provider_event_id);
