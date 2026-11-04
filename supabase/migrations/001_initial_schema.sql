-- Solace: private, two-person location sharing. Apply with the Supabase CLI.
create extension if not exists pgcrypto;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (char_length(display_name) between 1 and 80),
  avatar_url text,
  created_at timestamptz not null default now()
);
create table public.couples (id uuid primary key default gen_random_uuid(), created_at timestamptz not null default now());
create table public.couple_members (
  couple_id uuid not null references public.couples(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  sharing_enabled boolean not null default false,
  joined_at timestamptz not null default now(),
  primary key (couple_id, user_id), unique (user_id)
);
create table public.devices (
  id uuid primary key, user_id uuid not null references auth.users(id) on delete cascade,
  name text not null, battery_level smallint check (battery_level between 0 and 100), charging boolean,
  last_seen_at timestamptz, updated_at timestamptz not null default now()
);
create table public.location_points (
  id uuid primary key, user_id uuid not null references auth.users(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete cascade,
  latitude double precision not null check (latitude between -90 and 90), longitude double precision not null check (longitude between -180 and 180),
  accuracy real not null check (accuracy > 0), altitude double precision, speed real, bearing real, battery_level smallint,
  recorded_at timestamptz not null, received_at timestamptz not null default now(), unique (id)
);
create index location_points_user_recorded on public.location_points(user_id, recorded_at desc);
create table public.latest_locations (
  user_id uuid primary key references auth.users(id) on delete cascade,
  location_point_id uuid not null references public.location_points(id) on delete cascade,
  recorded_at timestamptz not null, updated_at timestamptz not null default now()
);
create table public.pairing_invites (
  id uuid primary key default gen_random_uuid(), couple_id uuid not null references public.couples(id) on delete cascade,
  token_hash text not null unique, created_by uuid not null references auth.users(id), expires_at timestamptz not null,
  redeemed_by uuid references auth.users(id), redeemed_at timestamptz, created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.couples enable row level security;
alter table public.couple_members enable row level security;
alter table public.devices enable row level security;
alter table public.location_points enable row level security;
alter table public.latest_locations enable row level security;
alter table public.pairing_invites enable row level security;

create function public.same_couple(a uuid, b uuid) returns boolean language sql stable security definer set search_path = public as $$
 select exists(select 1 from couple_members x join couple_members y on x.couple_id=y.couple_id where x.user_id=a and y.user_id=b and x.sharing_enabled and y.sharing_enabled)
$$;
create policy "own profile" on public.profiles for select using (id = auth.uid() or public.same_couple(id, auth.uid()));
create policy "update own profile" on public.profiles for update using (id = auth.uid()) with check (id = auth.uid());
create policy "own device writes" on public.devices for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "couple reads locations" on public.location_points for select using (user_id = auth.uid() or public.same_couple(user_id, auth.uid()));
create policy "own location inserts" on public.location_points for insert with check (user_id = auth.uid());
create policy "couple reads latest" on public.latest_locations for select using (user_id = auth.uid() or public.same_couple(user_id, auth.uid()));

create function public.refresh_latest_location() returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into latest_locations(user_id, location_point_id, recorded_at)
  values (new.user_id, new.id, new.recorded_at)
  on conflict (user_id) do update set location_point_id = excluded.location_point_id, recorded_at = excluded.recorded_at, updated_at = now()
  where excluded.recorded_at > latest_locations.recorded_at;
  return new;
end; $$;
create trigger location_updates_latest after insert on public.location_points for each row execute function public.refresh_latest_location();
