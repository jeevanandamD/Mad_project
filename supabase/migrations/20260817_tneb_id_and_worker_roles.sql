-- =============================================================================
-- POWERFIX - TNEB ID & Worker Self-Registration
-- Database Migration
-- =============================================================================
-- Adds the TNEB-provided ID (customer service connection number / worker ID) to
-- profiles and allows self-registered users to choose the 'worker' role while
-- still preventing self-assignment of the 'admin' role.
-- Backward Compatibility: existing rows get an empty tneb_id and keep working.
-- =============================================================================

-- 1. TNEB ID COLUMN
alter table public.profiles add column if not exists tneb_id text default '';

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

-- 2. ROLE TRIGGER - allow worker self-registration, never 'admin'
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
        elsif new.role is distinct from 'worker' then
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
