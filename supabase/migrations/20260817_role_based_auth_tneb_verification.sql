-- =============================================================================
-- POWERFIX - Role-Based Auth & TNEB ID Verification
-- Database Migration
-- =============================================================================
-- Adds:
--   1. Role-constrained TNEB-provided ID registry (mock verification source).
--   2. Server-side TNEB verification + registration RPC functions.
--   3. Backend enforcement of role/TNEB association and duplicate prevention.
--   4. Session-friendly profile columns (updated_at, disabled).
-- Backward Compatibility:
--   - Existing profiles (including rows with empty tneb_id / missing role)
--     keep working. New registrations always store a role + TNEB ID.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. PROFILE COLUMNS FOR SESSION & ACCOUNT LIFECYCLE
-- -----------------------------------------------------------------------------
alter table public.profiles add column if not exists updated_at timestamptz default now();
alter table public.profiles add column if not exists disabled boolean not null default false;

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

-- -----------------------------------------------------------------------------
-- 2. DUPLICATE PREVENTION
--    A TNEB ID may only be attached to a single account. The empty-string
--    legacy default is excluded so existing rows remain untouched.
-- -----------------------------------------------------------------------------
drop index if exists idx_profiles_tneb_id_unique;
create unique index idx_profiles_tneb_id_unique
    on public.profiles (lower(trim(tneb_id)))
    where tneb_id <> '';

-- -----------------------------------------------------------------------------
-- 3. MOCK TNEB REGISTRY
--    Authoritative dev-only source of truth for "does this TNEB ID exist and
--    is it eligible?". RLS is enabled with NO policies so clients can never
--    read or mutate it directly - they can only call the RPC functions below.
--    Replace this table/provider with an authorized TNEB integration later.
-- -----------------------------------------------------------------------------
create table if not exists public.tneb_ids (
    id text primary key,
    role text not null check (role in ('customer', 'worker')),
    status text not null default 'active' check (status in ('active', 'inactive')),
    name text not null default '',
    created_at timestamptz default now(),
    check (id ~ '^[A-Za-z0-9:._-]{6,20}$')
);

alter table public.tneb_ids enable row level security;

-- Seed dev entries (sample TNEB customer connection numbers and worker IDs).
insert into public.tneb_ids (id, role, status, name) values
    ('12345678901',    'customer', 'active',   'Anna Nagar Service Connection'),
    ('98765432109',    'customer', 'active',   'T. Nagar Service Connection'),
    ('CUST-1234-5678', 'customer', 'active',   'Velachery Service Connection'),
    ('CUST-INACT-8888','customer', 'inactive', 'Decommissioned Connection'),
    ('WK-000123',      'worker',   'active',   'Field Technician 123'),
    ('WK-000456',      'worker',   'active',   'Field Technician 456'),
    ('WK-INACT-7777',  'worker',   'inactive', 'Former Technician 777')
on conflict (id) do nothing;

-- -----------------------------------------------------------------------------
-- 4. VERIFICATION RPC (backend-controlled)
--    Validates format, existence, eligibility and duplicate registration.
--    The TNEB ID supplied by the client is never trusted directly.
-- -----------------------------------------------------------------------------
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
        -- The ID exists but belongs to the other role - never mix them.
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

-- -----------------------------------------------------------------------------
-- 5. REGISTRATION RPC (backend-controlled)
--    Atomically re-verifies the TNEB ID, enforces the role/TNEB association and
--    creates the profile. Only the authenticated user may create their own
--    profile and only with the CUSTOMER / WORKER roles. Admin is never
--    reachable through this path. Idempotent for existing profiles.
-- -----------------------------------------------------------------------------
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
        -- Account already exists; never allow a role/TNEB swap here.
        return jsonb_build_object('success', true, 'code', 'ok', 'existing', true);
    end if;

    insert into public.profiles (uid, email, name, role, phone, address, tneb_id)
    values (p_uid, p_email, coalesce(p_name, ''), p_role, coalesce(p_phone, ''), coalesce(p_address, ''), p_tneb_id);

    return jsonb_build_object('success', true, 'code', 'ok', 'existing', false);
end;
$$;

-- -----------------------------------------------------------------------------
-- 6. PERMISSIONS
--    verify_tneb_id runs before authentication (email confirmation flow) so it
--    is exposed to anon. create_profile_with_tneb requires a session and is
--    only exposed to authenticated clients.
-- -----------------------------------------------------------------------------
grant execute on function public.verify_tneb_id(text, text) to anon, authenticated, service_role;
grant execute on function public.create_profile_with_tneb(text, text, text, text, text, text, text) to authenticated, service_role;
