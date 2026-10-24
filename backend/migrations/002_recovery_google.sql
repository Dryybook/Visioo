alter table users alter column password_hash drop not null;
alter table users add column recovery_hash text;
alter table users add column recovery_set_at timestamptz;
alter table users add column google_sub text;
create unique index users_google_sub_key on users (google_sub) where google_sub is not null;
