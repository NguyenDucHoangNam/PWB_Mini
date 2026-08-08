/**
 * Decode JWT expiry (`exp` claim) from a JWT string.
 * Returns epoch milliseconds or null if the token is malformed.
 */
export function decodeJwtExpiry(token: string): number | null {
  const payload = decodeJwtPayload(token);
  return payload?.exp ? payload.exp * 1000 : null;
}

export interface JwtPayload {
  jti: string;
  exp: number;
  iat: number;
  sub: string;
  email?: string;
  role?: string;
}

export function decodeJwtPayload(token: string): JwtPayload | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  try {
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = payload + "===".slice((payload.length + 3) % 4);
    const json =
      typeof atob === "function" ? atob(padded) : Buffer.from(payload, "base64").toString("utf-8");
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}
