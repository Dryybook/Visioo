create extension if not exists pgcrypto;

create table users (
  id uuid primary key default gen_random_uuid(),
  email text not null,
  password_hash text not null,
  created_at timestamptz not null default now(),
  deleted_at timestamptz
);
create unique index users_email_key on users (lower(email));

create table profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  name text not null,
  avatar text,
  is_child boolean not null default false,
  language text not null default 'fr',
  subtitle_language text,
  maturity_level int not null default 18 check (maturity_level between 0 and 18),
  autoplay boolean not null default true,
  created_at timestamptz not null default now()
);
create index profiles_user_id_idx on profiles (user_id);

create table devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  installation_id text not null,
  platform text not null,
  model text,
  app_version text,
  last_seen_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  revoked_at timestamptz,
  unique (user_id, installation_id)
);
create index devices_user_id_idx on devices (user_id);

create table refresh_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  device_id uuid not null references devices(id) on delete cascade,
  token_hash text not null unique,
  expires_at timestamptz not null,
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);
create index refresh_tokens_device_idx on refresh_tokens (device_id);

create table vip_plans (
  id text primary key,
  name text not null,
  duration_days int not null check (duration_days > 0),
  device_limit int not null check (device_limit > 0)
);
insert into vip_plans (id, name, duration_days, device_limit) values
  ('vip_30d', 'VIP 30 jours', 30, 2),
  ('vip_90d', 'VIP 90 jours', 90, 2),
  ('vip_365d', 'VIP 365 jours', 365, 3);

create table vip_keys (
  id uuid primary key default gen_random_uuid(),
  key_hash text not null unique,
  display_prefix text not null,
  plan_id text not null references vip_plans(id),
  status text not null default 'active' check (status in ('active', 'used', 'revoked')),
  max_activations int not null default 1 check (max_activations > 0),
  activation_count int not null default 0,
  device_limit int check (device_limit > 0),
  created_at timestamptz not null default now(),
  expires_at timestamptz,
  activated_at timestamptz,
  activated_by_user_id uuid references users(id),
  notes text
);

create table vip_key_activations (
  id uuid primary key default gen_random_uuid(),
  key_id uuid not null references vip_keys(id),
  user_id uuid not null references users(id) on delete cascade,
  device_id uuid references devices(id) on delete set null,
  activated_at timestamptz not null default now(),
  unique (key_id, user_id)
);

create table vip_entitlements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  plan_id text not null references vip_plans(id),
  key_id uuid references vip_keys(id),
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  device_limit int not null check (device_limit > 0),
  revoked_at timestamptz,
  created_at timestamptz not null default now()
);
create index vip_entitlements_user_id_idx on vip_entitlements (user_id);

create table genres (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,
  name text not null
);

create table movies (
  id uuid primary key default gen_random_uuid(),
  tmdb_id int,
  title text not null,
  original_title text,
  overview text,
  poster_url text,
  backdrop_url text,
  logo_url text,
  release_date date,
  year int,
  runtime_min int,
  rating numeric(3,1),
  age_rating int not null default 0 check (age_rating between 0 and 18),
  is_vip boolean not null default false,
  published boolean not null default false,
  created_at timestamptz not null default now()
);

create table series (
  id uuid primary key default gen_random_uuid(),
  tmdb_id int,
  title text not null,
  original_title text,
  overview text,
  poster_url text,
  backdrop_url text,
  year int,
  rating numeric(3,1),
  age_rating int not null default 0 check (age_rating between 0 and 18),
  is_vip boolean not null default false,
  published boolean not null default false,
  created_at timestamptz not null default now()
);

create table seasons (
  id uuid primary key default gen_random_uuid(),
  series_id uuid not null references series(id) on delete cascade,
  season_number int not null,
  title text,
  poster_url text,
  unique (series_id, season_number)
);

create table episodes (
  id uuid primary key default gen_random_uuid(),
  season_id uuid not null references seasons(id) on delete cascade,
  episode_number int not null,
  title text not null,
  overview text,
  thumbnail_url text,
  runtime_min int,
  air_date date,
  unique (season_id, episode_number)
);

create table movie_genres (
  movie_id uuid not null references movies(id) on delete cascade,
  genre_id uuid not null references genres(id) on delete cascade,
  primary key (movie_id, genre_id)
);

create table series_genres (
  series_id uuid not null references series(id) on delete cascade,
  genre_id uuid not null references genres(id) on delete cascade,
  primary key (series_id, genre_id)
);

-- A source is only ever served when `authorized` is true: VISIOO distributes
-- only streams it has the rights to. `url` is the origin and is never returned
-- to clients unsigned. Audio/subtitle tracks are stored as JSON arrays.
create table sources (
  id uuid primary key default gen_random_uuid(),
  content_type text not null check (content_type in ('movie', 'episode')),
  content_id uuid not null,
  provider text not null,
  url text not null,
  type text not null check (type in ('HLS', 'DASH', 'MP4', 'WEBM')),
  quality int,
  language text,
  priority int not null default 0,
  authorized boolean not null default false,
  audio_tracks jsonb not null default '[]',
  subtitle_tracks jsonb not null default '[]',
  created_at timestamptz not null default now()
);
create index sources_content_idx on sources (content_type, content_id);

create table source_health (
  source_id uuid primary key references sources(id) on delete cascade,
  status text not null default 'UNKNOWN' check (status in ('HEALTHY', 'DEGRADED', 'DOWN', 'UNKNOWN')),
  latency_ms int,
  failure_count int not null default 0,
  success_count int not null default 0,
  last_checked_at timestamptz
);

create table home_sections (
  id text primary key,
  title text not null,
  layout text not null default 'poster' check (layout in ('poster', 'landscape')),
  position int not null default 0,
  active boolean not null default true
);

create table home_section_items (
  section_id text not null references home_sections(id) on delete cascade,
  content_type text not null check (content_type in ('movie', 'series')),
  content_id uuid not null,
  position int not null default 0,
  primary key (section_id, content_type, content_id)
);

create table watch_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  profile_id uuid not null references profiles(id) on delete cascade,
  content_type text not null check (content_type in ('movie', 'episode')),
  content_id uuid not null,
  position_ms bigint not null,
  duration_ms bigint not null,
  updated_at timestamptz not null default now(),
  unique (profile_id, content_type, content_id)
);
create index watch_history_user_profile_idx on watch_history (user_id, profile_id);

create table watchlist (
  user_id uuid not null references users(id) on delete cascade,
  profile_id uuid not null references profiles(id) on delete cascade,
  content_type text not null check (content_type in ('movie', 'series')),
  content_id uuid not null,
  added_at timestamptz not null default now(),
  primary key (profile_id, content_type, content_id)
);
create index watchlist_user_profile_idx on watchlist (user_id, profile_id);

create table admin_users (
  id uuid primary key default gen_random_uuid(),
  email text not null,
  password_hash text not null,
  role text not null check (role in ('SUPER_ADMIN', 'ADMIN', 'CONTENT_MANAGER', 'SUPPORT')),
  created_at timestamptz not null default now()
);
create unique index admin_users_email_key on admin_users (lower(email));

create table audit_logs (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid references admin_users(id),
  action text not null,
  target_type text,
  target_id text,
  details jsonb not null default '{}',
  created_at timestamptz not null default now()
);
