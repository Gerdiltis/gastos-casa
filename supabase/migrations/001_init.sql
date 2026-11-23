-- =====================================================================
-- Gastos Casa · esquema inicial
-- Pegar entero en Supabase > SQL Editor > New query > Run
-- =====================================================================

-- ---------- Tablas ----------------------------------------------------

create table if not exists public.profiles (
  id           uuid primary key references auth.users(id) on delete cascade,
  display_name text not null,
  created_at   timestamptz not null default now()
);

create table if not exists public.groups (
  id          uuid primary key default gen_random_uuid(),
  name        text not null check (char_length(name) between 1 and 60),
  invite_code text not null unique,
  created_by  uuid not null references public.profiles(id),
  created_at  timestamptz not null default now()
);

create table if not exists public.group_members (
  group_id  uuid not null references public.groups(id) on delete cascade,
  user_id   uuid not null references public.profiles(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);
create index if not exists group_members_user_idx on public.group_members(user_id);

create table if not exists public.categories (
  id       uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups(id) on delete cascade,
  name     text not null check (char_length(name) between 1 and 40),
  icon     text not null default '🏷️',
  color    text not null default '#FFD95A',
  unique (group_id, name)
);

create table if not exists public.archive_batches (
  id          uuid primary key default gen_random_uuid(),
  group_id    uuid not null references public.groups(id) on delete cascade,
  archived_at timestamptz not null default now(),
  archived_by uuid not null references public.profiles(id)
);

create table if not exists public.expenses (
  id               uuid primary key default gen_random_uuid(),
  group_id         uuid not null references public.groups(id) on delete cascade,
  paid_by          uuid not null references public.profiles(id),
  description      text not null check (char_length(description) between 1 and 120),
  amount_cents     bigint not null check (amount_cents > 0),
  category_id      uuid references public.categories(id) on delete set null,
  is_settlement    boolean not null default false,
  created_by       uuid not null references public.profiles(id),
  created_at       timestamptz not null default now(),
  archived_at      timestamptz,
  archive_batch_id uuid references public.archive_batches(id) on delete set null
);
create index if not exists expenses_group_idx on public.expenses(group_id, created_at desc);

create table if not exists public.expense_splits (
  expense_id  uuid not null references public.expenses(id) on delete cascade,
  user_id     uuid not null references public.profiles(id),
  share_cents bigint not null check (share_cents >= 0),
  primary key (expense_id, user_id)
);

-- ---------- Perfil automático al registrarse -------------------------

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(nullif(new.raw_user_meta_data->>'display_name', ''), split_part(new.email, '@', 1)));
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------- Helpers de seguridad -------------------------------------

create or replace function public.is_member(p_group_id uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.group_members
                 where group_id = p_group_id and user_id = auth.uid());
$$;

create or replace function public.shares_group_with(p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.group_members a
                 join public.group_members b on a.group_id = b.group_id
                 where a.user_id = auth.uid() and b.user_id = p_user);
$$;

-- ---------- Row Level Security ---------------------------------------

alter table public.profiles        enable row level security;
alter table public.groups          enable row level security;
alter table public.group_members   enable row level security;
alter table public.categories      enable row level security;
alter table public.archive_batches enable row level security;
alter table public.expenses        enable row level security;
alter table public.expense_splits  enable row level security;

drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles for select to authenticated
  using (id = auth.uid() or public.shares_group_with(id));
drop policy if exists profiles_update on public.profiles;
create policy profiles_update on public.profiles for update to authenticated
  using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists groups_select on public.groups;
create policy groups_select on public.groups for select to authenticated
  using (public.is_member(id));
drop policy if exists groups_update on public.groups;
create policy groups_update on public.groups for update to authenticated
  using (public.is_member(id)) with check (public.is_member(id));

drop policy if exists members_select on public.group_members;
create policy members_select on public.group_members for select to authenticated
  using (public.is_member(group_id));
drop policy if exists members_leave on public.group_members;
create policy members_leave on public.group_members for delete to authenticated
  using (user_id = auth.uid());

drop policy if exists categories_all on public.categories;
create policy categories_all on public.categories for all to authenticated
  using (public.is_member(group_id)) with check (public.is_member(group_id));

drop policy if exists batches_select on public.archive_batches;
create policy batches_select on public.archive_batches for select to authenticated
  using (public.is_member(group_id));

drop policy if exists expenses_select on public.expenses;
create policy expenses_select on public.expenses for select to authenticated
  using (public.is_member(group_id));
drop policy if exists expenses_delete on public.expenses;
create policy expenses_delete on public.expenses for delete to authenticated
  using (public.is_member(group_id) and archived_at is null);

drop policy if exists splits_select on public.expense_splits;
create policy splits_select on public.expense_splits for select to authenticated
  using (exists (select 1 from public.expenses e
                 where e.id = expense_id and public.is_member(e.group_id)));

-- Los INSERT de grupos, miembros, gastos y repartos se hacen solo vía RPC.

-- ---------- RPC: crear grupo -----------------------------------------

create or replace function public.gen_invite_code()
returns text language plpgsql as $$
declare
  alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  code text;
begin
  loop
    code := '';
    for i in 1..6 loop
      code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
    end loop;
    exit when not exists (select 1 from public.groups where invite_code = code);
  end loop;
  return code;
end $$;

create or replace function public.create_group(p_name text)
returns public.groups language plpgsql security definer set search_path = public as $$
declare
  g public.groups;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  insert into public.groups (name, invite_code, created_by)
  values (trim(p_name), public.gen_invite_code(), auth.uid())
  returning * into g;

  insert into public.group_members (group_id, user_id) values (g.id, auth.uid());

  insert into public.categories (group_id, name, icon, color) values
    (g.id, 'Supermercado', '🛒', '#FFD95A'),
    (g.id, 'Hogar',        '🏠', '#4FD1B5'),
    (g.id, 'Restaurantes', '🍽️', '#FF8A65'),
    (g.id, 'Ocio',         '🎉', '#9B8CFF'),
    (g.id, 'Transporte',   '🚗', '#64B5F6'),
    (g.id, 'Otros',        '🏷️', '#B0BEC5');
  return g;
end $$;

-- ---------- RPC: unirse con código -----------------------------------

create or replace function public.join_group(p_code text)
returns public.groups language plpgsql security definer set search_path = public as $$
declare
  g public.groups;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select * into g from public.groups where invite_code = upper(trim(p_code));
  if not found then raise exception 'Código no válido'; end if;
  insert into public.group_members (group_id, user_id) values (g.id, auth.uid())
  on conflict do nothing;
  return g;
end $$;

-- ---------- RPC: añadir gasto (gasto + repartos en una transacción) --
-- p_splits: [{"user_id": "...", "share_cents": 123}, ...]

create or replace function public.add_expense(
  p_group_id uuid,
  p_description text,
  p_amount_cents bigint,
  p_category_id uuid,
  p_paid_by uuid,
  p_splits jsonb,
  p_is_settlement boolean default false
) returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_id uuid;
  v_sum bigint;
  s jsonb;
begin
  if not public.is_member(p_group_id) then raise exception 'No eres miembro del grupo'; end if;
  if not exists (select 1 from public.group_members where group_id = p_group_id and user_id = p_paid_by) then
    raise exception 'El pagador no pertenece al grupo';
  end if;
  if jsonb_array_length(p_splits) = 0 then raise exception 'Elige al menos una persona'; end if;

  select coalesce(sum((x->>'share_cents')::bigint), 0) into v_sum from jsonb_array_elements(p_splits) x;
  if v_sum <> p_amount_cents then raise exception 'El reparto no suma el total'; end if;

  insert into public.expenses (group_id, paid_by, description, amount_cents, category_id, is_settlement, created_by)
  values (p_group_id, p_paid_by, trim(p_description), p_amount_cents, p_category_id, p_is_settlement, auth.uid())
  returning id into v_id;

  for s in select * from jsonb_array_elements(p_splits) loop
    if not exists (select 1 from public.group_members
                   where group_id = p_group_id and user_id = (s->>'user_id')::uuid) then
      raise exception 'Una de las personas no pertenece al grupo';
    end if;
    insert into public.expense_splits (expense_id, user_id, share_cents)
    values (v_id, (s->>'user_id')::uuid, (s->>'share_cents')::bigint);
  end loop;
  return v_id;
end $$;

-- ---------- RPC: saldos del grupo (pagado - debido, sin archivados) --

create or replace function public.group_balances(p_group_id uuid)
returns table (user_id uuid, balance_cents bigint)
language sql stable security definer set search_path = public as $$
  select m.user_id,
         coalesce((select sum(e.amount_cents) from public.expenses e
                   where e.group_id = p_group_id and e.archived_at is null and e.paid_by = m.user_id), 0)
       - coalesce((select sum(s.share_cents) from public.expense_splits s
                   join public.expenses e on e.id = s.expense_id
                   where e.group_id = p_group_id and e.archived_at is null and s.user_id = m.user_id), 0)
  from public.group_members m
  where m.group_id = p_group_id and public.is_member(p_group_id);
$$;

-- ---------- RPC: archivar gastos saldados ----------------------------

create or replace function public.archive_group_expenses(p_group_id uuid)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_batch uuid;
begin
  if not public.is_member(p_group_id) then raise exception 'No eres miembro del grupo'; end if;
  if exists (select 1 from public.group_balances(p_group_id) where balance_cents <> 0) then
    raise exception 'Aún hay deudas pendientes en el grupo';
  end if;
  if not exists (select 1 from public.expenses where group_id = p_group_id and archived_at is null) then
    raise exception 'No hay gastos que archivar';
  end if;
  insert into public.archive_batches (group_id, archived_by) values (p_group_id, auth.uid())
  returning id into v_batch;
  update public.expenses set archived_at = now(), archive_batch_id = v_batch
  where group_id = p_group_id and archived_at is null;
  return v_batch;
end $$;

-- ---------- Permisos de la API (RLS sigue filtrando las filas) --------

grant usage on schema public to anon, authenticated;
grant select, update on public.profiles, public.groups to authenticated;
grant select, delete on public.group_members, public.expenses to authenticated;
grant select, insert, update, delete on public.categories to authenticated;
grant select on public.archive_batches, public.expense_splits to authenticated;

-- ---------- Permisos de ejecución ------------------------------------

revoke execute on function public.create_group(text)            from anon;
revoke execute on function public.join_group(text)              from anon;
revoke execute on function public.add_expense(uuid, text, bigint, uuid, uuid, jsonb, boolean) from anon;
revoke execute on function public.archive_group_expenses(uuid)  from anon;
revoke execute on function public.group_balances(uuid)          from anon;

-- ---------- Realtime (los cambios de gastos llegan al instante) ------

do $$ begin
  alter publication supabase_realtime add table public.expenses;
exception when duplicate_object then null;
end $$;

-- ---------- Ping para evitar la pausa del tier gratuito ---------------
create or replace function public.ping() returns text language sql as $$ select 'pong' $$;
