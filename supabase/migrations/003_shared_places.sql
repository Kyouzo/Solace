-- Solace: places shared by both people in a couple. Run in the Supabase SQL Editor after 002.
-- Either person can add or remove a place; both see it and both get arrive/leave alerts for it.

create table if not exists public.shared_places (
  id uuid primary key,
  couple_id uuid not null default public.my_couple_id() references public.couples(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 80),
  kind text not null default 'CUSTOM' check (kind in ('HOME', 'SCHOOL', 'WORK', 'CUSTOM')),
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  radius_meters real not null default 150 check (radius_meters between 20 and 2000),
  created_by uuid not null default auth.uid() references auth.users(id),
  created_at timestamptz not null default now()
);
create index if not exists shared_places_couple on public.shared_places(couple_id);

alter table public.shared_places enable row level security;

drop policy if exists "couple reads places" on public.shared_places;
create policy "couple reads places" on public.shared_places for select using (couple_id = public.my_couple_id());
drop policy if exists "couple adds places" on public.shared_places;
create policy "couple adds places" on public.shared_places for insert with check (couple_id = public.my_couple_id());
drop policy if exists "couple edits places" on public.shared_places;
create policy "couple edits places" on public.shared_places for update using (couple_id = public.my_couple_id()) with check (couple_id = public.my_couple_id());
drop policy if exists "couple removes places" on public.shared_places;
create policy "couple removes places" on public.shared_places for delete using (couple_id = public.my_couple_id());

notify pgrst, 'reload schema';
