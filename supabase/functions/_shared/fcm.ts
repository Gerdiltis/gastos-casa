// Envío por FCM HTTP v1 compartido por las Edge Functions.
import { createClient } from "npm:@supabase/supabase-js@2.49.4";
import { importPKCS8, SignJWT } from "npm:jose@5.9.6";

export const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false },
});
const sa = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "{}");

let cached: { token: string; exp: number } | null = null;
let webhookSecret: string | null = null;

/** El trigger y el cron firman con un secreto que solo existe en Vault. */
export async function authorized(req: Request): Promise<boolean> {
  if (!webhookSecret) {
    const { data, error } = await db.rpc("notify_webhook_secret");
    if (error) console.log("secret error", error.message);
    webhookSecret = (data as string | null) ?? null;
  }
  return !!webhookSecret && req.headers.get("x-webhook-secret") === webhookSecret;
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

/** Manda un mensaje solo de datos a todos los móviles de esos usuarios. Borra tokens caducados. */
export async function sendToUsers(userIds: string[], data: Record<string, string>) {
  if (userIds.length === 0) return [];
  const { data: tokens } = await db.from("device_tokens").select("token,user_id").in("user_id", userIds);
  if (!tokens?.length) return [];
  const access = await googleToken();
  const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
  return await Promise.all(tokens.map(async ({ token, user_id }) => {
    const r = await fetch(url, {
      method: "POST",
      headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token, data, android: { priority: "high" } } }),
    });
    if (r.ok) return { user_id, ok: true };
    const err = await r.text();
    if (r.status === 404 || err.includes("UNREGISTERED") || err.includes("INVALID_ARGUMENT")) {
      await db.from("device_tokens").delete().eq("token", token);
    }
    console.log("fcm error", user_id, r.status, err.slice(0, 200));
    return { user_id, ok: false };
  }));
}
