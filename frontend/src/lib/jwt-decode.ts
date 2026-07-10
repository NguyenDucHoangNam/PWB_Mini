/**
 * Decode JWT expiry (`exp` claim) from a JWT string.
 * Returns epoch milliseconds or null if the token is malformed.
 */
export function decodeJwtExpiry(token: string): number | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = payload + "===".slice((payload.length + 3) % 4);
    const json =
      typeof atob === "function" ? atob(padded) : Buffer.from(payload, "base64").toString("utf-8");
    const parsed = JSON.parse(json) as { exp?: number };
    return typeof parsed.exp === "number" ? parsed.exp * 1000 : null;
  } catch {
    return null;
  }
}
