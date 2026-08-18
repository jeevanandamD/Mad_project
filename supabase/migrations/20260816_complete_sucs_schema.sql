-- =============================================================================
-- SMART UTILITY COMPLAINT SYSTEM (SUCS) - Complete Database Schema & RLS Setup
-- =============================================================================
-- Instructions: Run this entire script in the Supabase SQL Editor.
-- It is idempotent (safe to run multiple times without losing existing data).
-- =============================================================================

-- Enable required extensions
create extension if not exists "uuid-ossp";
create extension if not exists "pgcrypto";

-- =============================================================================
-- 1. TABLES DEFINITIONS
-- =============================================================================

-- 1.1 PROFILES TABLE
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

-- 1.2 COMPLAINTS TABLE
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

-- 1.3 EMERGENCY REQUESTS TABLE
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
-- 2. INDEXES (for high performance queries)
-- =============================================================================
create index if not exists idx_profiles_role on public.profiles(role);
create index if not exists idx_complaints_customer on public.complaints(customer_id);
create index if not exists idx_complaints_worker on public.complaints(assigned_worker_id);
create index if not exists idx_complaints_status on public.complaints(status);
create index if not exists idx_emergency_user on public.emergency_requests(user_id);
create index if not exists idx_emergency_status on public.emergency_requests(status);


-- =============================================================================
-- 3. TRIGGERS & AUTOMATION
-- =============================================================================

-- 3.1 Security Trigger: Force client-inserted profiles to have role 'customer'
-- Only service role / direct database administrators can elevate roles.
create or replace function public.set_default_customer_role()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    -- If triggered by an authenticated client self-registering / modifying, keep role as customer
    if auth.uid() is not null and new.uid = auth.uid()::text then
        -- Preserve existing role if updating other fields, but block self-escalation to admin/worker
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


-- =============================================================================
-- 4. ROW LEVEL SECURITY (RLS) POLICIES
-- =============================================================================

-- Enable RLS on all tables
alter table public.profiles enable row level security;
alter table public.complaints enable row level security;
alter table public.emergency_requests enable row level security;

-- Helper security-definer function to check if current user is admin without recursion
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

-- 4.1 PROFILES POLICIES
drop policy if exists "profiles_select_policy" on public.profiles;
create policy "profiles_select_policy" on public.profiles
    for select using (
        auth.uid()::text = uid
        or public.is_admin()
        or role = 'worker' -- Allows admin & app to query available workers for assignment
    );

drop policy if exists "profiles_insert_policy" on public.profiles;
create policy "profiles_insert_policy" on public.profiles
    for insert with check (
        auth.uid()::text = uid
        or public.is_admin()
    );

drop policy if exists "profiles_update_policy" on public.profiles;
create policy "profiles_update_policy" on public.profiles
    for update using (
        auth.uid()::text = uid
        or public.is_admin()
    );

-- 4.2 COMPLAINTS POLICIES
drop policy if exists "complaints_select_policy" on public.complaints;
create policy "complaints_select_policy" on public.complaints
    for select using (
        auth.uid()::text = customer_id
        or auth.uid()::text = assigned_worker_id
        or public.is_admin()
    );

drop policy if exists "complaints_insert_policy" on public.complaints;
create policy "complaints_insert_policy" on public.complaints
    for insert with check (
        auth.uid()::text = customer_id
        or public.is_admin()
    );

drop policy if exists "complaints_update_policy" on public.complaints;
create policy "complaints_update_policy" on public.complaints
    for update using (
        public.is_admin()
        or (public.is_worker() and auth.uid()::text = assigned_worker_id)
        or (auth.uid()::text = customer_id and status = 'Pending')
    );

-- 4.3 EMERGENCY REQUESTS POLICIES
drop policy if exists "emergency_select_policy" on public.emergency_requests;
create policy "emergency_select_policy" on public.emergency_requests
    for select using (
        auth.uid()::text = user_id
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
-- 5. REALTIME REPLICATION (Supabase Realtime)
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
-- 6. DEVELOPER ROLE PROMOTION QUERIES (Run manually as needed)
-- =============================================================================
-- To promote an existing user to Admin:
--   UPDATE public.profiles SET role = 'admin' WHERE email = 'your_admin_email@example.com';
--
-- To promote an existing user to Worker (with available = true):
--   UPDATE public.profiles SET role = 'worker', available = true WHERE email = 'your_worker_email@example.com';
--
-- To verify user roles:
--   SELECT uid, email, name, role, available FROM public.profiles ORDER BY created_at DESC;
-- =============================================================================
