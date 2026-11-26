-- =====================================================================
-- Gastos Casa · v1.2: editar gastos + notificaciones push (FCM)
-- Además (una vez): supabase secrets set FCM_SERVICE_ACCOUNT="$(cat clave.json)"
--                    supabase functions deploy notify-expense --no-verify-jwt
-- =====================================================================

create extension if not exists pg_net with schema extensions;

-- Secreto compartido trigger <-> Edge Function. Se genera aquí y nunca sale de la base
-- de datos: la función lo lee con el RPC de abajo (solo service_role).
do $$ begin
  if not exists (select 1 from vault.secrets where name = 'notify_webhook_secret') then
    perform vault.create_secret(encode(extensions.gen_random_bytes(24), 'hex'), 'notify_webhook_secret');
  end if;
end $$;

create or replace function public.notify_webhook_secret()
returns text language sql stable security definer set search_path = public as $$
  select decrypted_secret from vault.decrypted_secrets where name = 'notify_webhook_secret';
$$;
revoke execute on function public.notify_webhook_secret() from public, anon, authenticated;
grant execute on function public.notify_webhook_secret() to service_role;

-- ---------- Tokens de dispositivo -------------------------------------

create table if not exists public.device_tokens (
  token      text primary key,
  user_id    uuid not null references public.profiles(id) on delete cascade,
  updated_at timestamptz not null default now()
);
create index if not exists device_tokens_user_idx on public.device_tokens(user_id);

alter table public.device_tokens enable row level security;
drop policy if exists tokens_own on public.device_tokens;
create policy tokens_own on public.device_tokens for all to authenticated
  using (user_id = auth.uid()) with check (user_id = auth.uid());
grant select, insert, update, delete on public.device_tokens to authenticated;

-- Un token pasa a otra cuenta si se cambia de sesión en el mismo móvil.
create or replace function public.register_device(p_token text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  insert into public.device_tokens (token, user_id, updated_at) values (p_token, auth.uid(), now())
  on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
end $$;
revoke execute on function public.register_device(text) from anon;

-- ---------- Editar gasto ----------------------------------------------

alter table public.expenses add column if not exists updated_at timestamptz;
alter table public.expenses add column if not exists updated_by uuid references public.profiles(id);

create or replace function public.update_expense(
  p_expense_id uuid,
  p_description text,
  p_amount_cents bigint,
  p_category_id uuid,
  p_paid_by uuid,
  p_splits jsonb
) returns uuid language plpgsql security definer set search_path = public as $$
declare
  e public.expenses;
  v_sum bigint;
  s jsonb;
begin
  select * into e from public.expenses where id = p_expense_id;
  if not found or not public.is_member(e.group_id) then raise exception 'No eres miembro del grupo'; end if;
  if e.archived_at is not null then raise exception 'No se puede editar un gasto archivado'; end if;
  if p_amount_cents <= 0 then raise exception 'Importe no válido'; end if;
  if not exists (select 1 from public.group_members where group_id = e.group_id and user_id = p_paid_by) then
    raise exception 'El pagador no pertenece al grupo';
  end if;
  if jsonb_array_length(p_splits) = 0 then raise exception 'Elige al menos una persona'; end if;
  select coalesce(sum((x->>'share_cents')::bigint), 0) into v_sum from jsonb_array_elements(p_splits) x;
  if v_sum <> p_amount_cents then raise exception 'El reparto no suma el total'; end if;
  for s in select * from jsonb_array_elements(p_splits) loop
    if not exists (select 1 from public.group_members
                   where group_id = e.group_id and user_id = (s->>'user_id')::uuid) then
      raise exception 'Una de las personas no pertenece al grupo';
    end if;
  end loop;

  delete from public.expense_splits where expense_id = p_expense_id;
  insert into public.expense_splits (expense_id, user_id, share_cents)
  select p_expense_id, (x->>'user_id')::uuid, (x->>'share_cents')::bigint from jsonb_array_elements(p_splits) x;

  update public.expenses set
    description = trim(p_description), amount_cents = p_amount_cents, category_id = p_category_id,
    paid_by = p_paid_by, updated_at = now(), updated_by = auth.uid()
  where id = p_expense_id;
  return p_expense_id;
end $$;
revoke execute on function public.update_expense(uuid, text, bigint, uuid, uuid, jsonb) from anon;

-- ---------- Aviso a la Edge Function ----------------------------------

create or replace function public.notify_expense_change()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_secret text;
  v_row public.expenses;
begin
  -- Archivar (solo cambia archived_at) no avisa.
  if tg_op = 'UPDATE' and new.archived_at is distinct from old.archived_at then return null; end if;
  -- Borrado en cascada al eliminar el grupo: no avisa.
  if tg_op = 'DELETE' and not exists (select 1 from public.groups where id = old.group_id) then return null; end if;
  -- Un gasto archivado que se borra no interesa.
  if tg_op = 'DELETE' and old.archived_at is not null then return null; end if;

  v_row := case when tg_op = 'DELETE' then old else new end;
  select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'notify_webhook_secret';
  if v_secret is null then return null; end if;

  perform net.http_post(
    url := 'https://ycnkggcelqqewdzcsyyp.supabase.co/functions/v1/notify-expense',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-webhook-secret', v_secret),
    body := jsonb_build_object(
      'op', tg_op,
      'actor', coalesce(auth.uid(), v_row.updated_by, v_row.created_by),
      'expense', jsonb_build_object(
        'id', v_row.id, 'group_id', v_row.group_id, 'description', v_row.description,
        'amount_cents', v_row.amount_cents, 'is_settlement', v_row.is_settlement, 'paid_by', v_row.paid_by
      )
    )
  );
  return null;
end $$;

drop trigger if exists expenses_notify on public.expenses;
create trigger expenses_notify
  after insert or update or delete on public.expenses
  for each row execute function public.notify_expense_change();
