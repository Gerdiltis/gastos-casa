-- =====================================================================
-- Gastos Casa · v1.1: iconos propios de categoría + lista de la compra
-- Pegar entero en Supabase > SQL Editor > New query > Run
-- =====================================================================

-- ---------- Iconos: claves "ic:" que dibuja la app -------------------

update public.categories set icon = case name
    when 'Supermercado' then 'ic:cart'
    when 'Hogar'        then 'ic:home'
    when 'Restaurantes' then 'ic:food'
    when 'Ocio'         then 'ic:party'
    when 'Transporte'   then 'ic:car'
    when 'Otros'        then 'ic:tag'
  end
where name in ('Supermercado', 'Hogar', 'Restaurantes', 'Ocio', 'Transporte', 'Otros');

alter table public.categories alter column icon set default 'ic:tag';

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
    (g.id, 'Supermercado', 'ic:cart',  '#FFD95A'),
    (g.id, 'Hogar',        'ic:home',  '#4FD1B5'),
    (g.id, 'Restaurantes', 'ic:food',  '#FF8A65'),
    (g.id, 'Ocio',         'ic:party', '#9B8CFF'),
    (g.id, 'Transporte',   'ic:car',   '#64B5F6'),
    (g.id, 'Otros',        'ic:tag',   '#B0BEC5');
  return g;
end $$;
revoke execute on function public.create_group(text) from anon;

-- ---------- Lista de la compra (una por grupo) -----------------------

create table if not exists public.shopping_items (
  id         uuid primary key default gen_random_uuid(),
  group_id   uuid not null references public.groups(id) on delete cascade,
  name       text not null check (char_length(trim(name)) between 1 and 80),
  added_by   uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now()
);
create index if not exists shopping_items_group_idx on public.shopping_items(group_id, created_at desc);

alter table public.shopping_items enable row level security;

drop policy if exists shopping_select on public.shopping_items;
create policy shopping_select on public.shopping_items for select to authenticated
  using (public.is_member(group_id));
drop policy if exists shopping_insert on public.shopping_items;
create policy shopping_insert on public.shopping_items for insert to authenticated
  with check (public.is_member(group_id) and added_by = auth.uid());
drop policy if exists shopping_delete on public.shopping_items;
create policy shopping_delete on public.shopping_items for delete to authenticated
  using (public.is_member(group_id));

grant select, insert, delete on public.shopping_items to authenticated;

do $$ begin
  alter publication supabase_realtime add table public.shopping_items;
exception when duplicate_object then null;
end $$;
