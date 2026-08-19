-- =============================================================================
-- SUCS - SMART UTILITY COMPLAINT SYSTEM
-- Complete Database Schema & RLS Setup
-- =============================================================================
-- INSTRUCTION: Run each section in order (1, 2, 3, 4).
-- Section 1 must run first to add the tneb_id column.
-- =============================================================================

-- =============================================================================
-- SECTION 1: ADD TNEB ID COLUMN & BASE SCHEMA
-- =============================================================================
-- Must run first because other sections reference tneb_id column

-- Add tneb_id column to profiles table
alter table public.profiles add column if not exists tneb_id text default '';

-- Add check constraint for TNEB ID format
do $$
begin
    if not exists (
        select 1 from pg_constraint where conname = 'profiles_tneb_id_check'
    ) then
        alter table public.profiles add constraint profiles_tneb_id_check
            check (tneb_id = '' or tneb_id ~ '^[A-Za-z0-9:._-]{6,20}$');
    end if;
end $$;

create index if not exists idx_profiles_tneb_id on public.profiles(tneb_id);

-- Ensure uuid and crypto extensions
create extension if not exists "uuid-ossp";
create extension if not exists "pgcrypto";

-- =============================================================================
-- 1.1 PROFILES TABLE
-- =============================================================================
-- uid is text to match auth.uid()::text comparisons in RLS policies
create table if not exists public.profiles (
    uid text primary key,
    email text not null,
    name text not null default '',
    role text not null default 'customer' check (role in ('customer', 'admin', 'worker')),
    phone text default '',
    address text default '',
    available boolean default true,
    location text default '',
    created_at timestamptz default now()
);

-- Ensure all columns exist if table already existed
alter table public.profiles add column if not exists phone text default '';
alter table public.profiles add column if not exists address text default '';
alter table public.profiles add column if not exists available boolean default true;
alter table public.profiles add column if not exists location text default '';
alter table public.profiles add column if not exists created_at timestamptz default now();

-- =============================================================================
-- 1.2 COMPLAINTS TABLE
-- =============================================================================
create table if not exists public.complaints (
    id text primary key default gen_random_uuid()::text,
    customer_id text not null,
    customer_name text not null default '',
    mobile text not null default '',
    address text not null default '',
    complaint_type text not null default '',
    description text not null default '',
    priority text not null default 'Medium' check (priority in ('Low', 'Medium', 'High', 'Urgent')),
    status text not null default 'Pending' check (status in ('Pending', 'Assigned', 'In Progress', 'Resolved', 'Closed')),
    category text not null default 'Technical',
    assigned_worker_id text default null,
    location text default '',
    admin_reply text default '',
    remarks text[] default '{}',
    emergency_request boolean default false,
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- Ensure all columns exist if table already existed
alter table public.complaints add column if not exists customer_name text default '';
alter table public.complaints add column if not exists mobile text default '';
alter table public.complaints add column if not exists address text default '';
alter table public.complaints add column if not exists complaint_type text default '';
alter table public.complaints add column if not exists description text default '';
alter table public.complaints add column if not exists priority text default 'Medium';
alter table public.complaints add column if not exists status text default 'Pending';
alter table public.complaints add column if not exists category text default 'Technical';
alter table public.complaints add column if not exists assigned_worker_id text default null;
alter table public.complaints add column if not exists location text default '';
alter table public.complaints add column if not exists admin_reply text default '';
alter table public.complaints add column if not exists remarks text[] default '{}';
alter table public.complaints add column if not exists emergency_request boolean default false;
alter table public.complaints add column if not exists created_at timestamptz default now();
alter table public.complaints add column if not exists updated_at timestamptz default now();

-- =============================================================================
-- 1.3 EMERGENCY REQUESTS TABLE
-- =============================================================================
create table if not exists public.emergency_requests (
    id text primary key default gen_random_uuid()::text,
    user_id text not null,
    message text not null default '',
    status text not null default 'Open' check (status in ('Open', 'In Progress', 'Resolved', 'Dismissed')),
    created_at timestamptz default now()
);

-- Ensure all columns exist if table already existed
alter table public.emergency_requests add column if not exists message text default '';
alter table public.emergency_requests add column if not exists status text default 'Open';
alter table public.emergency_requests add column if not exists created_at timestamptz default now();

-- =============================================================================
-- INDEXES
-- =============================================================================
create index if not exists idx_profiles_role on public.profiles(role);
create index if not exists idx_complaints_customer on public.complaints(customer_id);
create index if not exists idx_complaints_worker on public.complaints(assigned_worker_id);
create index if not exists idx_complaints_status on public.complaints(status);
create index if not exists idx_emergency_user on public.emergency_requests(user_id);
create index if not exists idx_emergency_status on public.emergency_requests(status);

-- =============================================================================
-- SECTION 2: TRIGGERS & AUTOMATION
-- =============================================================================

-- 3.1 Security Trigger: Force client-inserted profiles to have role 'customer'
create or replace function public.set_default_customer_role()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is not null and new.uid = auth.uid()::text then
        if tg_op = 'UPDATE' then
            new.role := old.role;
        else
            new.role := 'customer';
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_profiles_force_customer_role on public.profiles;
create trigger trg_profiles_force_customer_role
    before insert or update of role on public.profiles
    for each row execute function public.set_default_customer_role();

-- 3.2 Auto-update updated_at on complaints
create or replace function public.set_complaint_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists trg_complaints_updated_at on public.complaints;
create trigger trg_complaints_updated_at
    before update on public.complaints
    for each row execute function public.set_complaint_updated_at();

-- Profile updated_at trigger
create or replace function public.set_profile_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists trg_profiles_updated_at on public.profiles;
create trigger trg_profiles_updated_at
    before update on public.profiles
    for each row execute function public.set_profile_updated_at();

-- =============================================================================
-- ROW LEVEL SECURITY (RLS) POLICIES
-- =============================================================================
alter table public.profiles enable row level security;
alter table public.complaints enable row level security;
alter table public.emergency_requests enable row level security;

-- Helper security-definer function to check if current user is admin
create or replace function public.is_admin()
returns boolean
language sql
security definer
stable
set search_path = public
as $$
    select exists (
        select 1 from public.profiles
        where uid = auth.uid()::text and role = 'admin'
    );
$$;

-- Helper security-definer function to check if current user is worker
create or replace function public.is_worker()
returns boolean
language sql
security definer
stable
set search_path = public
as $$
    select exists (
        select 1 from public.profiles
        where uid = auth.uid()::text and role = 'worker'
    );
$$;

-- =============================================================================
-- PROFILES POLICIES
-- =============================================================================
drop policy if exists "profiles_select_policy" on public.profiles;
create policy "profiles_select_policy" on public.profiles
    for select using (
        auth.uid()::text = uid::text
        or public.is_admin()
        or role = 'worker'
    );

drop policy if exists "profiles_insert_policy" on public.profiles;
create policy "profiles_insert_policy" on public.profiles
    for insert with check (
        auth.uid()::text = uid::text
        or public.is_admin()
    );

drop policy if exists "profiles_update_policy" on public.profiles;
create policy "profiles_update_policy" on public.profiles
    for update using (
        auth.uid()::text = uid::text
        or public.is_admin()
    );

-- =============================================================================
-- COMPLAINTS POLICIES
-- =============================================================================
drop policy if exists "complaints_select_policy" on public.complaints;
create policy "complaints_select_policy" on public.complaints
    for select using (
        auth.uid()::text = customer_id::text
        or auth.uid()::text = assigned_worker_id::text
        or public.is_admin()
    );

drop policy if exists "complaints_insert_policy" on public.complaints;
create policy "complaints_insert_policy" on public.complaints
    for insert with check (
        auth.uid()::text = customer_id::text
        or public.is_admin()
    );

drop policy if exists "complaints_update_policy" on public.complaints;
create policy "complaints_update_policy" on public.complaints
    for update using (
        public.is_admin()
        or (public.is_worker() and auth.uid()::text = assigned_worker_id::text)
        or (auth.uid()::text = customer_id::text and status = 'Pending')
    );

-- =============================================================================
-- EMERGENCY REQUESTS POLICIES
-- =============================================================================
drop policy if exists "emergency_select_policy" on public.emergency_requests;
create policy "emergency_select_policy" on public.emergency_requests
    for select using (
        auth.uid()::text = user_id::text
        or public.is_admin()
    );

drop policy if exists "emergency_insert_policy" on public.emergency_requests;
create policy "emergency_insert_policy" on public.emergency_requests
    for insert with check (
        auth.uid() is not null
    );

drop policy if exists "emergency_update_policy" on public.emergency_requests;
create policy "emergency_update_policy" on public.emergency_requests
    for update using (
        public.is_admin()
    );

-- =============================================================================
-- REALTIME REPLICATION
-- =============================================================================
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'profiles'
    ) then
        alter publication supabase_realtime add table public.profiles;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'complaints'
    ) then
        alter publication supabase_realtime add table public.complaints;
    end if;

    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'emergency_requests'
    ) then
        alter publication supabase_realtime add table public.emergency_requests;
    end if;
end $$;

-- =============================================================================
-- SECTION 3: TNEB ID VERIFICATION SYSTEM
-- =============================================================================

-- Create TNEB IDs registry table
create table if not exists public.tneb_ids (
    id text primary key,
    role text not null check (role in ('customer', 'worker')),
    status text not null default 'active' check (status in ('active', 'inactive')),
    name text not null default '',
    created_at timestamptz default now(),
    check (id ~ '^[A-Za-z0-9:._-]{6,20}$')
);

alter table public.tneb_ids enable row level security;

-- Seed dev entries (sample TNEB customer connection numbers and worker IDs)
insert into public.tneb_ids (id, role, status, name) values
    ('12345678901',    'customer', 'active',   'Anna Nagar Service Connection'),
    ('98765432109',    'customer', 'active',   'T. Nagar Service Connection'),
    ('CUST-1234-5678', 'customer', 'active',   'Velachery Service Connection'),
    ('CUST-INACT-8888','customer', 'inactive', 'Decommissioned Connection'),
    ('WK-000123',      'worker',   'active',   'Field Technician 123'),
    ('WK-000456',      'worker',   'active',   'Field Technician 456'),
    ('WK-INACT-7777',  'worker',   'inactive', 'Former Technician 777')
on conflict (id) do nothing;

-- =============================================================================
-- VERIFICATION RPC (backend-controlled)
-- =============================================================================
create or replace function public.verify_tneb_id(
    p_role text,
    p_tneb_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_status text;
    v_role   text;
    v_count  bigint;
begin
    if p_tneb_id is null or length(trim(p_tneb_id)) = 0 then
        return jsonb_build_object('verified', false, 'code', 'empty');
    end if;
    if p_role is null or p_role not in ('customer', 'worker') then
        return jsonb_build_object('verified', false, 'code', 'invalid_role');
    end if;
    if not p_tneb_id ~ '^[A-Za-z0-9:._-]{6,20}$' then
        return jsonb_build_object('verified', false, 'code', 'invalid_format');
    end if;

    select ti.status, ti.role
      into v_status, v_role
      from public.tneb_ids ti
     where ti.id = p_tneb_id
     limit 1;

    if v_status is null then
        return jsonb_build_object('verified', false, 'code', 'not_found');
    end if;
    if v_role is distinct from p_role then
        return jsonb_build_object('verified', false, 'code', 'role_mismatch');
    end if;
    if v_status <> 'active' then
        return jsonb_build_object('verified', false, 'code', 'inactive');
    end if;

    select count(*) into v_count
      from public.profiles
     where tneb_id <> '' and lower(trim(tneb_id)) = lower(trim(p_tneb_id));

    if v_count > 0 then
        return jsonb_build_object('verified', false, 'code', 'already_registered');
    end if;

    return jsonb_build_object('verified', true, 'code', 'ok');
end;
$$;

-- =============================================================================
-- REGISTRATION RPC (backend-controlled)
-- =============================================================================
create or replace function public.create_profile_with_tneb(
    p_uid text,
    p_email text,
    p_name text,
    p_phone text,
    p_address text,
    p_role text,
    p_tneb_id text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_verified jsonb;
    v_exists   boolean;
begin
    if auth.uid() is null then
        return jsonb_build_object('success', false, 'code', 'unauthorized');
    end if;
    if p_uid is null or p_uid <> auth.uid()::text then
        return jsonb_build_object('success', false, 'code', 'unauthorized');
    end if;
    if p_role is null or p_role not in ('customer', 'worker') then
        return jsonb_build_object('success', false, 'code', 'invalid_role');
    end if;
    if p_tneb_id is null or length(trim(p_tneb_id)) = 0 then
        return jsonb_build_object('success', false, 'code', 'empty');
    end if;

    -- Re-verify through the same backend path used by the app.
    v_verified := public.verify_tneb_id(p_role, p_tneb_id);
    if (v_verified->>'verified')::boolean is distinct from true then
        return jsonb_build_object(
            'success', false,
            'code', coalesce(v_verified->>'code', 'verification_failed')
        );
    end if;

    select exists (
        select 1 from public.profiles where uid = p_uid
    ) into v_exists;

    if v_exists then
        return jsonb_build_object('success', true, 'code', 'ok', 'existing', true);
    end if;

    insert into public.profiles (uid, email, name, role, phone, address, tneb_id)
    values (p_uid, p_email, coalesce(p_name, ''), p_role, coalesce(p_phone, ''), coalesce(p_address, ''), p_tneb_id);

    return jsonb_build_object('success', true, 'code', 'ok', 'existing', false);
end;
$$;

-- =============================================================================
-- PERMISSIONS
-- =============================================================================
grant execute on function public.verify_tneb_id(text, text) to anon, authenticated, service_role;
grant execute on function public.create_profile_with_tneb(text, text, text, text, text, text, text) to authenticated, service_role;

-- =============================================================================
-- SECTION 4: INITIAL RLS SETUP (Optional if not already run)
-- =============================================================================

-- Enable RLS if not already enabled
alter table public.profiles enable row level security;
alter table public.complaints enable row level security;
alter table public.emergency_requests enable row level security;

-- Force self-registered users to always be 'customer'
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

-- profiles policies
drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own" on public.profiles
  for select using (auth.uid() = uid);

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own" on public.profiles
  for insert with check (auth.uid() = uid);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own" on public.profiles
  for update using (auth.uid() = uid) with check (auth.uid() = uid);

-- complaints policies
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

-- emergency_requests policies
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

-- Realtime support
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