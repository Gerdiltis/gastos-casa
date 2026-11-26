// Edge Function diaria (pg_cron): pide al creador de cada gasto recurrente que
// confirme la repetición que toca hoy.
import { authorized, db, money, sendToUsers } from "../_shared/fcm.ts";

type Due = {
  id: string; group_id: string; created_by: string; description: string; amount_cents: number;
  next_date: string; occurrence: number; total: number; group_name: string;
};

Deno.serve(async (req) => {
  if (!(await authorized(req))) return new Response("forbidden", { status: 403 });
  const { data, error } = await db.rpc("claim_recurring_due");
  if (error) return new Response(error.message, { status: 500 });
  const due = (data ?? []) as Due[];

  const out = await Promise.all(due.map(async (d) => {
    const results = await sendToUsers([d.created_by], {
      type: "recurring",
      title: d.group_name,
      body: `¿Apuntar «${d.description}» · ${money(d.amount_cents)}? (${d.occurrence}/${d.total})`,
      group_id: d.group_id,
      recurring_id: d.id,
      date: d.next_date,
    });
    return { id: d.id, date: d.next_date, attempted: results.length, sent: results.filter((r) => r.ok).length };
  }));
  console.log(JSON.stringify({ due: out }));
  return Response.json({ due: out });
});
