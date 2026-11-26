// Edge Function: recibe el aviso del trigger `expenses_notify` y manda la
// notificación push (FCM HTTP v1) al resto de miembros del grupo.
import { createClient } from "npm:@supabase/supabase-js@2.49.4";
import { importPKCS8, SignJWT } from "npm:jose@5.9.6";

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

const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});
const sa = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "{}");

let cached: { token: string; exp: number } | null = null;
let webhookSecret: string | null = null;

async function expectedSecret(): Promise<string | null> {
  if (webhookSecret) return webhookSecret;
  const { data, error } = await db.rpc("notify_webhook_secret");
  if (error) console.log("secret error", error.message);
  webhookSecret = (data as string | null) ?? null;
  return webhookSecret;
}

async function googleToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp - 60 > now) return cached.token;
  const key = await importPKCS8(sa.private_key, "RS256");
  const jwt = await new SignJWT({ scope: "https://www.googleapis.com/auth/firebase.messaging" })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setAudience("https://oauth2.googleapis.com/token")
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(key);
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  const j = await r.json();
  if (!r.ok) throw new Error("OAuth: " + JSON.stringify(j));
  cached = { token: j.access_token, exp: now + j.expires_in };
  return cached.token;
}

export function money(cents: number): string {
  const s = (Math.abs(cents) / 100).toFixed(2).split(".");
  const int = s[0].replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${cents < 0 ? "-" : ""}${int},${s[1]} €`;
}

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
  const expected = await expectedSecret();
  if (!expected || req.headers.get("x-webhook-secret") !== expected) {
    return new Response("forbidden", { status: 403 });
  }
  const p = (await req.json()) as Payload;
  const e = p.expense;

  const [{ data: group }, { data: actor }, { data: members }] = await Promise.all([
    db.from("groups").select("name").eq("id", e.group_id).maybeSingle(),
    db.from("profiles").select("display_name").eq("id", p.actor).maybeSingle(),
    db.from("group_members").select("user_id").eq("group_id", e.group_id).neq("user_id", p.actor),
  ]);
  const userIds = (members ?? []).map((m) => m.user_id);
  if (!group || userIds.length === 0) return Response.json({ sent: 0 });

  const { data: tokens } = await db.from("device_tokens").select("token,user_id").in("user_id", userIds);
  if (!tokens?.length) return Response.json({ sent: 0 });

  const title = group.name;
  const body = message(p.op, actor?.display_name ?? "Alguien", e);
  const access = await googleToken();
  const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;

  const results = await Promise.all(tokens.map(async ({ token, user_id }) => {
    const r = await fetch(url, {
      method: "POST",
      headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          data: { title, body, group_id: e.group_id, expense_id: e.id, op: p.op },
          android: { priority: "high" },
        },
      }),
    });
    if (r.ok) return { user_id, ok: true };
    const err = await r.text();
    if (r.status === 404 || err.includes("UNREGISTERED") || err.includes("INVALID_ARGUMENT")) {
      await db.from("device_tokens").delete().eq("token", token);
    }
    console.log("fcm error", user_id, r.status, err.slice(0, 200));
    return { user_id, ok: false };
  }));

  console.log(JSON.stringify({ op: p.op, group: e.group_id, actor: p.actor, results }));
  return Response.json({ sent: results.filter((r) => r.ok).length, attempted: results.length });
});
