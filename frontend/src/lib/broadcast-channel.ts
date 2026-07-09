import type { AuthUser } from "@/features/auth/stores/use-auth-store";

export const AUTH_CHANNEL = "pwb_auth_channel";

export type AuthChannelMessage =
  | { type: "LOGOUT" }
  | { type: "TOKEN_UPDATED"; token: string; user: AuthUser };

export function broadcastAuthMessage(message: AuthChannelMessage): void {
  if (typeof window === "undefined" || typeof BroadcastChannel === "undefined") return;
  try {
    const channel = new BroadcastChannel(AUTH_CHANNEL);
    channel.postMessage(message);
    channel.close();
  } catch {
    // BroadcastChannel is best-effort. Fallback to in-tab storage event
    // for legacy browsers is not implemented - we accept the trade-off.
  }
}