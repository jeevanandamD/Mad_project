-- SUCS - Row Level Security setup
-- Run this in the Supabase SQL editor after creating the tables:
--   profiles (uid text pk, email text, name text, role text, phone text, address text, available boolean, location text, created_at bigint)
--   complaints (see the Complaint model: customer_id text, customer_name, mobile, address, complaint_type, description, priority, status, category, assigned_worker_id, location, created_at, updated_at, remarks, admin_reply, emergency_request)
--   emergency_requests (user_id text, message text, status text, created_at timestamptz)

-- 1) Enable RLS on all tables (idempotent)
alter table public.profiles enable row level security;
alter table public.complaints enable row level security;
alter table public.emergency_requests enable row level security;

-- 2) Force self-registered users to always be 'customer'.
--    Admin/worker roles can ONLY be granted by a privileged user (service role / dashboard).
create or replace function public.set_default_customer_role()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.uid = auth.uid() then
    new.role := 'customer';
  end if;
  return new;
end;
$$;

drop trigger if exists profiles_force_customer_role on public.profiles;
create trigger profiles_force_customer_role
  before insert or update of role on public.profiles
  for each row execute function public.set_default_customer_role();

-- 3) profiles policies
drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own" on public.profiles
  for select using (auth.uid() = uid);

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own" on public.profiles
  for insert with check (auth.uid() = uid);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles
  for update using (auth.uid() = uid) with check (auth.uid() = uid);

-- 4) complaints policies
drop policy if exists "complaints_insert_own" on public.complaints;
create policy "complaints_insert_own" on public.complaints
  for insert with check (auth.uid() = customer_id);

drop policy if exists "complaints_select_all" on public.complaints;
create policy "complaints_select_all" on public.complaints
  for select using (
    auth.uid() = customer_id
    or exists (
      select 1 from public.profiles p
      where p.uid = auth.uid() and p.role in ('admin', 'worker')
    )
  );

drop policy if exists "complaints_update_staff" on public.complaints;
create policy "complaints_update_staff" on public.complaints
  for update using (
    exists (
      select 1 from public.profiles p
      where p.uid = auth.uid() and p.role in ('admin', 'worker')
    )
  );

-- 5) emergency_requests policies
drop policy if exists "emergency_insert_auth" on public.emergency_requests;
create policy "emergency_insert_auth" on public.emergency_requests
  for insert with check (auth.uid() is not null);

drop policy if exists "emergency_select_own_or_admin" on public.emergency_requests;
create policy "emergency_select_own_or_admin" on public.emergency_requests
  for select using (
    user_id = auth.uid()
    or exists (
      select 1 from public.profiles p
      where p.uid = auth.uid() and p.role = 'admin'
    )
  );

-- 6) Realtime support for live list updates (requires Supabase Realtime enabled)
do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'complaints'
  ) then
    alter publication supabase_realtime add table public.complaints;
  end if;
end $$;

do $$
begin
  if not exists (
    select 1 from pg_publication_tables
    where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'emergency_requests'
  ) then
    alter publication supabase_realtime add table public.emergency_requests;
  end if;
end $$;
