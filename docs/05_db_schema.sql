-- PostgreSQL schema draft for Atom Go Mobile MVP
-- Source of truth for lifecycle terms: docs/14_rental_lifecycle.md

create table app_user (
  id uuid primary key,
  role text not null check (role in ('admin')),
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
  weekly_rate_rub integer not null check (weekly_rate_rub > 0),
  avatar_url text,
  frame_serial_number text,
  motor_serial_number text,
  battery_serial_number_1 text,
  battery_serial_number_2 text,
  admin_id uuid not null references app_user(id),
  status text not null default 'in_service' check (status in ('in_service', 'decommissioned')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table client_profile (
  id uuid primary key,
  full_name text not null,
  admin_id uuid not null references app_user(id),
  address text,
  passport_data text,
  phones_json jsonb not null default '[]'::jsonb,
  client_debt_rub integer not null default 0 check (client_debt_rub >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- "Аренда": lifecycle card for one physical bike.
create table rental (
  id uuid primary key,
  bike_id uuid not null references bike(id),
  admin_id uuid not null references app_user(id),
  pipeline_status text not null default 'long_term' check (pipeline_status in ('long_term', 'soon_return', 'mine')),
  next_login text,
  next_password_hash text,
  next_password_fingerprint text,
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index uq_active_rental_bike on rental(bike_id) where deleted_at is null;
create index idx_rental_status on rental(admin_id, pipeline_status) where deleted_at is null;

-- "Клиентская аренда": one client usage period inside a rental lifecycle.
create table client_rental (
  id uuid primary key,
  rental_id uuid not null references rental(id),
  client_id uuid not null references client_profile(id),
  bike_id uuid not null references bike(id),
  period_start date not null,
  period_end date,
  status text not null default 'active' check (status in ('active', 'completed')),
  login text not null unique,
  password_hash text not null,
  password_fingerprint text not null unique,
  weekly_rate_snapshot_rub integer not null check (weekly_rate_snapshot_rub > 0),
  video_url text,
  contract_url text,
  comment text,
  final_debt_rub integer not null default 0 check (final_debt_rub >= 0),
  tax_mode text not null default 'self_employed' check (tax_mode in ('self_employed', 'individual_entrepreneur')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index uq_active_client_rental_per_rental on client_rental(rental_id) where status = 'active';
create unique index uq_active_client_rental_per_client on client_rental(client_id) where status = 'active';
create index idx_client_rental_client_history on client_rental(client_id, period_start desc);

create table client_rental_adjustment (
  id uuid primary key,
  client_rental_id uuid not null references client_rental(id),
  client_id uuid not null references client_profile(id),
  amount_rub integer not null check (amount_rub > 0),
  sign text not null check (sign in ('plus', 'minus')),
  note text,
  created_by uuid not null references app_user(id),
  created_at timestamptz not null default now()
);

create table client_debt_transfer (
  id uuid primary key,
  client_id uuid not null references client_profile(id),
  source_client_rental_id uuid not null references client_rental(id),
  amount_rub integer not null check (amount_rub > 0),
  reason text not null,
  created_by uuid not null references app_user(id),
  created_at timestamptz not null default now()
);

create table ledger_entry (
  id uuid primary key,
  client_id uuid not null references client_profile(id),
  client_rental_id uuid references client_rental(id),
  type text not null check (type in ('charge', 'payment', 'adjustment', 'debt_transfer')),
  direction smallint not null check (direction in (-1, 1)),
  amount_rub integer not null check (amount_rub > 0),
  source text not null,
  source_id text,
  note text,
  created_at timestamptz not null default now()
);

create index idx_ledger_client_rental_created_at on ledger_entry(client_rental_id, created_at desc);
create index idx_ledger_client_created_at on ledger_entry(client_id, created_at desc);

create table payment (
  id uuid primary key,
  client_id uuid not null references client_profile(id),
  client_rental_id uuid not null references client_rental(id),
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
