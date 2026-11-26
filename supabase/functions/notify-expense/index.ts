// Edge Function: recibe el aviso del trigger `expenses_notify` y manda la
// notificación push al resto de miembros del grupo.
import { authorized, db, money, sendToUsers } from "../_shared/fcm.ts";

type Payload = {
  op: "INSERT" | "UPDATE" | "DELETE";
  actor: string;
  expense: {
    id: string;
    group_id: string;
    description: string;
    amount_cents: number;
    is_settlement: boolean;
    paid_by: string;
  };
};

export function message(op: Payload["op"], actor: string, e: Payload["expense"]): string {
  if (e.is_settlement) {
    return op === "DELETE"
      ? `${actor} eliminó un pago de ${money(e.amount_cents)}`
      : `${actor} registró un pago de ${money(e.amount_cents)}`;
  }
  switch (op) {
    case "INSERT": return `${actor} añadió «${e.description}» · ${money(e.amount_cents)}`;
    case "UPDATE": return `${actor} modificó «${e.description}» · ${money(e.amount_cents)}`;
    default: return `${actor} eliminó «${e.description}»`;
  }
}

Deno.serve(async (req) => {
  if (!(await authorized(req))) return new Response("forbidden", { status: 403 });
  const p = (await req.json()) as Payload;
  const e = p.expense;

  const [{ data: group }, { data: actor }, { data: members }] = await Promise.all([
    db.from("groups").select("name").eq("id", e.group_id).maybeSingle(),
    db.from("profiles").select("display_name").eq("id", p.actor).maybeSingle(),
    db.from("group_members").select("user_id").eq("group_id", e.group_id).neq("user_id", p.actor),
  ]);
  const userIds = (members ?? []).map((m) => m.user_id);
  if (!group || userIds.length === 0) return Response.json({ sent: 0 });

  const results = await sendToUsers(userIds, {
    type: "expense",
    title: group.name,
    body: message(p.op, actor?.display_name ?? "Alguien", e),
    group_id: e.group_id,
    expense_id: e.id,
    op: p.op,
  });
  console.log(JSON.stringify({ op: p.op, group: e.group_id, actor: p.actor, results }));
  return Response.json({ sent: results.filter((r) => r.ok).length, attempted: results.length });
});
