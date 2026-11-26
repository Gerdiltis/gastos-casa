-- =====================================================================
-- Gastos Casa · v1.3: gastos recurrentes con confirmación por notificación
-- Además: supabase functions deploy recurring-due --no-verify-jwt
-- =====================================================================

create extension if not exists pg_cron;

-- ---------- Tabla --------------------------------------------------------

create table if not exists public.recurring_expenses (
  id            uuid primary key default gen_random_uuid(),
  group_id      uuid not null references public.groups(id) on delete cascade,
  created_by    uuid not null references public.profiles(id) on delete cascade,
  description   text not null check (char_length(description) between 1 and 120),
  amount_cents  bigint not null check (amount_cents > 0),
  category_id   uuid references public.categories(id) on delete set null,
  paid_by       uuid not null references public.profiles(id),
  participants  uuid[] not null check (cardinality(participants) > 0),
  frequency     text not null check (frequency in ('weekly', 'monthly', 'yearly')),
  start_date    date not null,
  end_date      date not null,
  occurrence    int not null default 0,          -- cuántas se han resuelto (apuntadas u omitidas)
  next_date     date,                            -- null cuando ya no quedan
  notified_on   date,
  notify_count  int not null default 0,          -- avisos enviados para next_date
  active        boolean not null default true,
  created_at    timestamptz not null default now(),
  check (end_date >= start_date)
);
create index if not exists recurring_due_idx on public.recurring_expenses(next_date) where active;
create index if not exists recurring_group_idx on public.recurring_expenses(group_id);

alter table public.recurring_expenses enable row level security;
drop policy if exists recurring_select on public.recurring_expenses;
create policy recurring_select on public.recurring_expenses for select to authenticated
  using (public.is_member(group_id));
grant select on public.recurring_expenses to authenticated;

alter table public.expenses add column if not exists recurring_id uuid
  references public.recurring_expenses(id) on delete set null;
alter table public.expenses add column if not exists occurrence int;

-- ---------- Fechas -------------------------------------------------------

-- Fecha de la ocurrencia n (0 = inicio). Siempre desde el inicio: el 31 vuelve a ser 31.
create or replace function public.recurring_date(p_start date, p_freq text, p_n int)
returns date language sql immutable as $$
  select (p_start + case p_freq
            when 'weekly'  then make_interval(weeks => p_n)
            when 'monthly' then make_interval(months => p_n)
            else                make_interval(years => p_n) end)::date;
$$;

create or replace function public.recurring_total(p_start date, p_end date, p_freq text)
returns int language plpgsql immutable as $$
declare n int := 0;
begin
  while public.recurring_date(p_start, p_freq, n) <= p_end loop n := n + 1; end loop;
  return n;
end $$;

create or replace function public.madrid_today() returns date language sql stable as $$
  select (now() at time zone 'Europe/Madrid')::date;
$$;

-- ---------- Reparto igual (como SplitCalculator: los céntimos sobrantes uno a uno) ----

create or replace function public.equal_splits(p_amount bigint, p_people uuid[])
returns jsonb language sql immutable as $$
  select jsonb_agg(jsonb_build_object(
           'user_id', u,
           'share_cents', p_amount / cardinality(p_people) + case when i <= p_amount % cardinality(p_people) then 1 else 0 end
         ) order by i)
  from unnest(p_people) with ordinality as t(u, i);
$$;

-- ---------- Validación común --------------------------------------------

create or replace function public.check_recurring_input(
  p_group_id uuid, p_amount bigint, p_paid_by uuid, p_participants uuid[], p_freq text, p_start date, p_end date
) returns void language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_member(p_group_id) then raise exception 'No eres miembro del grupo'; end if;
  if p_amount is null or p_amount <= 0 then raise exception 'Importe no válido'; end if;
  if p_freq not in ('weekly', 'monthly', 'yearly') then raise exception 'Frecuencia no válida'; end if;
  if p_end < p_start then raise exception 'La fecha de fin debe ser posterior al inicio'; end if;
  if coalesce(cardinality(p_participants), 0) = 0 then raise exception 'Elige al menos una persona'; end if;
  if not exists (select 1 from public.group_members where group_id = p_group_id and user_id = p_paid_by) then
    raise exception 'El pagador no pertenece al grupo';
  end if;
  if exists (select 1 from unnest(p_participants) u
             where not exists (select 1 from public.group_members where group_id = p_group_id and user_id = u)) then
    raise exception 'Una de las personas no pertenece al grupo';
  end if;
end $$;

-- ---------- Resolver una ocurrencia (apuntar u omitir) -------------------

create or replace function public.resolve_recurring(
  p_recurring_id uuid, p_date date, p_accept boolean, p_amount_cents bigint default null
) returns uuid language plpgsql security definer set search_path = public as $$
declare
  r public.recurring_expenses;
  v_amount bigint;
  v_id uuid;
  v_when timestamptz;
  v_next date;
begin
  select * into r from public.recurring_expenses where id = p_recurring_id for update;
  if not found or not public.is_member(r.group_id) then raise exception 'No eres miembro del grupo'; end if;
  if r.created_by <> auth.uid() then raise exception 'Solo quien creó la recurrencia puede confirmarla'; end if;
  if not r.active or r.next_date is null or r.next_date <> p_date then
    raise exception 'Esta repetición ya se resolvió';
  end if;
  if p_date > public.madrid_today() then raise exception 'Aún no toca esta repetición'; end if;

  if p_accept then
    v_amount := coalesce(p_amount_cents, r.amount_cents);
    if v_amount <= 0 then raise exception 'Importe no válido'; end if;
    -- Hoy: ahora mismo. Una fecha pasada: a mediodía de ese día (hora de Madrid).
    v_when := case when p_date = public.madrid_today() then now()
                   else (p_date + time '12:00') at time zone 'Europe/Madrid' end;
    insert into public.expenses (group_id, paid_by, description, amount_cents, category_id, created_by,
                                 created_at, recurring_id, occurrence)
    values (r.group_id, r.paid_by, r.description, v_amount, r.category_id, auth.uid(),
            v_when, r.id, r.occurrence + 1)
    returning id into v_id;
    insert into public.expense_splits (expense_id, user_id, share_cents)
    select v_id, (x->>'user_id')::uuid, (x->>'share_cents')::bigint
    from jsonb_array_elements(public.equal_splits(v_amount, r.participants)) x;
  end if;

  v_next := public.recurring_date(r.start_date, r.frequency, r.occurrence + 1);
  update public.recurring_expenses set
    occurrence = occurrence + 1,
    next_date = case when v_next <= r.end_date then v_next end,
    active = v_next <= r.end_date,
    notified_on = null, notify_count = 0
  where id = r.id;
  return v_id;
end $$;
revoke execute on function public.resolve_recurring(uuid, date, boolean, bigint) from anon;

-- ---------- Crear --------------------------------------------------------

create or replace function public.create_recurring_expense(
  p_group_id uuid, p_description text, p_amount_cents bigint, p_category_id uuid, p_paid_by uuid,
  p_participants uuid[], p_frequency text, p_start date, p_end date
) returns uuid language plpgsql security definer set search_path = public as $$
declare v_id uuid;
begin
  perform public.check_recurring_input(p_group_id, p_amount_cents, p_paid_by, p_participants, p_frequency, p_start, p_end);
  if p_start < public.madrid_today() then raise exception 'El inicio no puede ser anterior a hoy'; end if;
  if char_length(trim(coalesce(p_description, ''))) = 0 then raise exception 'Escribe una descripción'; end if;

  insert into public.recurring_expenses (group_id, created_by, description, amount_cents, category_id, paid_by,
                                         participants, frequency, start_date, end_date, next_date)
  values (p_group_id, auth.uid(), trim(p_description), p_amount_cents, p_category_id, p_paid_by,
          p_participants, p_frequency, p_start, p_end, p_start)
  returning id into v_id;

  -- Empieza hoy: la primera se apunta ya.
  if p_start = public.madrid_today() then
    perform public.resolve_recurring(v_id, p_start, true, null);
  end if;
  return v_id;
end $$;
revoke execute on function public.create_recurring_expense(uuid, text, bigint, uuid, uuid, uuid[], text, date, date) from anon;

-- ---------- Editar (para las próximas veces) y detener -------------------

create or replace function public.update_recurring(
  p_recurring_id uuid, p_description text, p_amount_cents bigint, p_category_id uuid, p_paid_by uuid,
  p_participants uuid[], p_end date
) returns void language plpgsql security definer set search_path = public as $$
declare r public.recurring_expenses;
begin
  select * into r from public.recurring_expenses where id = p_recurring_id for update;
  if not found or not public.is_member(r.group_id) then raise exception 'No eres miembro del grupo'; end if;
  if r.created_by <> auth.uid() then raise exception 'Solo quien creó la recurrencia puede cambiarla'; end if;
  perform public.check_recurring_input(r.group_id, p_amount_cents, p_paid_by, p_participants, r.frequency, r.start_date, p_end);
  if char_length(trim(coalesce(p_description, ''))) = 0 then raise exception 'Escribe una descripción'; end if;

  update public.recurring_expenses set
    description = trim(p_description), amount_cents = p_amount_cents, category_id = p_category_id,
    paid_by = p_paid_by, participants = p_participants, end_date = p_end,
    next_date = case when r.active and public.recurring_date(start_date, frequency, occurrence) <= p_end
                     then public.recurring_date(start_date, frequency, occurrence) end,
    active = r.active and public.recurring_date(start_date, frequency, occurrence) <= p_end
  where id = r.id;
end $$;
revoke execute on function public.update_recurring(uuid, text, bigint, uuid, uuid, uuid[], date) from anon;

create or replace function public.stop_recurring(p_recurring_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare r public.recurring_expenses;
begin
  select * into r from public.recurring_expenses where id = p_recurring_id;
  if not found or not public.is_member(r.group_id) then raise exception 'No eres miembro del grupo'; end if;
  if r.created_by <> auth.uid() then raise exception 'Solo quien creó la recurrencia puede detenerla'; end if;
  update public.recurring_expenses set active = false, next_date = null where id = r.id;
end $$;
revoke execute on function public.stop_recurring(uuid) from anon;

-- Las funciones internas no se llaman desde la app.
revoke execute on function public.check_recurring_input(uuid, bigint, uuid, uuid[], text, date, date) from public, anon, authenticated;

-- ---------- Avisos pendientes (los marca y devuelve; lo usa la Edge Function) --------

create or replace function public.claim_recurring_due()
returns table (id uuid, group_id uuid, created_by uuid, description text, amount_cents bigint,
               next_date date, occurrence int, total int, group_name text)
language sql security definer set search_path = public as $$
  with due as (
    update public.recurring_expenses r set notified_on = public.madrid_today(), notify_count = r.notify_count + 1
    where r.active and r.next_date <= public.madrid_today()
      and (r.notified_on is null or r.notified_on < public.madrid_today())
      and r.notify_count < 3
    returning r.*
  )
  select d.id, d.group_id, d.created_by, d.description, d.amount_cents, d.next_date, d.occurrence + 1,
         public.recurring_total(d.start_date, d.end_date, d.frequency), g.name
  from due d join public.groups g on g.id = d.group_id;
$$;
revoke execute on function public.claim_recurring_due() from public, anon, authenticated;
grant execute on function public.claim_recurring_due() to service_role;

-- ---------- Reloj diario: 08:00 UTC ------------------------------------

select cron.unschedule('recurring-due') where exists (select 1 from cron.job where jobname = 'recurring-due');
select cron.schedule('recurring-due', '0 8 * * *', $cron$
  select net.http_post(
    url := 'https://ycnkggcelqqewdzcsyyp.supabase.co/functions/v1/recurring-due',
    headers := jsonb_build_object('Content-Type', 'application/json',
      'x-webhook-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'notify_webhook_secret')),
    body := '{}'::jsonb
  );
$cron$);
