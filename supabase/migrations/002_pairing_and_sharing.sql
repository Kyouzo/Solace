-- Solace: pairing, sharing toggle, profile bootstrap. Run in Supabase SQL Editor after 001.
-- No custom SMTP or domain is required for any of this.

-- Profiles are created automatically for every new account (display_name is NOT NULL).
create or replace function public.handle_new_user() returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles(id, display_name)
  values (new.id, left(coalesce(nullif(split_part(new.email, '@', 1), ''), 'Partner'), 80))
  on conflict (id) do nothing;
  return new;
end; $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();

-- Backfill accounts created before this migration.
insert into public.profiles(id, display_name)
select id, left(coalesce(nullif(split_part(email, '@', 1), ''), 'Partner'), 80) from auth.users
on conflict (id) do nothing;

-- Membership lookup that avoids RLS recursion.
create or replace function public.my_couple_id() returns uuid language sql stable security definer set search_path = public as $$
  select couple_id from public.couple_members where user_id = auth.uid()
$$;

drop policy if exists "read couple members" on public.couple_members;
create policy "read couple members" on public.couple_members for select using (couple_id = public.my_couple_id());
drop policy if exists "read own couple" on public.couples;
create policy "read own couple" on public.couples for select using (id = public.my_couple_id());

-- Create (or refresh) a 15-minute, single-use invite. Only a hash of the code is stored.
create or replace function public.create_pairing_invite() returns text language plpgsql security definer set search_path = public as $$
declare uid uuid := auth.uid(); cid uuid; code text;
begin
  if uid is null then raise exception 'Not signed in'; end if;
  select couple_id into cid from public.couple_members where user_id = uid;
  if cid is null then
    insert into public.couples default values returning id into cid;
    insert into public.couple_members(couple_id, user_id) values (cid, uid);
  elsif (select count(*) from public.couple_members where couple_id = cid) >= 2 then
    raise exception 'You are already paired';
  end if;
  delete from public.pairing_invites where couple_id = cid and redeemed_at is null;
  code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));
  insert into public.pairing_invites(couple_id, token_hash, created_by, expires_at)
  values (cid, encode(sha256(convert_to(code, 'utf8')), 'hex'), uid, now() + interval '15 minutes');
  return code;
end; $$;

create or replace function public.redeem_pairing_invite(code text) returns void language plpgsql security definer set search_path = public as $$
declare uid uuid := auth.uid(); inv public.pairing_invites;
begin
  if uid is null then raise exception 'Not signed in'; end if;
  if exists (select 1 from public.couple_members where user_id = uid) then raise exception 'You are already paired'; end if;
  select * into inv from public.pairing_invites
   where token_hash = encode(sha256(convert_to(upper(trim(code)), 'utf8')), 'hex') and redeemed_at is null and expires_at > now()
   for update;
  if not found then raise exception 'Invalid or expired code'; end if;
  if inv.created_by = uid then raise exception 'You cannot redeem your own invite'; end if;
  if (select count(*) from public.couple_members where couple_id = inv.couple_id) >= 2 then raise exception 'This couple is already full'; end if;
  insert into public.couple_members(couple_id, user_id) values (inv.couple_id, uid);
  update public.pairing_invites set redeemed_by = uid, redeemed_at = now() where id = inv.id;
end; $$;

-- Sharing is off by default; both people must turn it on before either can see the other.
create or replace function public.set_sharing(enabled boolean) returns void language plpgsql security definer set search_path = public as $$
begin
  update public.couple_members set sharing_enabled = enabled where user_id = auth.uid();
  if not found then raise exception 'Pair with someone first'; end if;
end; $$;

create or replace function public.couple_status() returns jsonb language plpgsql stable security definer set search_path = public as $$
declare uid uuid := auth.uid(); cid uuid; mine boolean; pid uuid; psharing boolean; pname text;
begin
  select couple_id, sharing_enabled into cid, mine from public.couple_members where user_id = uid;
  if cid is null then return jsonb_build_object('paired', false, 'my_sharing', false, 'partner_sharing', false); end if;
  select user_id, sharing_enabled into pid, psharing from public.couple_members where couple_id = cid and user_id <> uid;
  select display_name into pname from public.profiles where id = pid;
  return jsonb_build_object('paired', pid is not null, 'my_sharing', mine, 'partner_sharing', coalesce(psharing, false), 'partner_name', pname);
end; $$;

revoke all on function public.create_pairing_invite(), public.redeem_pairing_invite(text), public.set_sharing(boolean), public.couple_status() from public, anon;
grant execute on function public.create_pairing_invite(), public.redeem_pairing_invite(text), public.set_sharing(boolean), public.couple_status() to authenticated;
